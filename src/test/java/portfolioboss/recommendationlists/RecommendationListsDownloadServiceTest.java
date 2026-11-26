package portfolioboss.recommendationlists;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import portfolioboss.exceptions.FinvizUnavailableException;
import portfolioboss.sources.finviz.FinvizScreen;
import portfolioboss.sources.finviz.FinvizScreens;
import portfolioboss.sources.finviz.FinvizStock;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * The Recommendations page's lists for the sync (RECOMMENDATIONS_FINVIZ_TODO.md, session 2), against the real PostgreSQL
 * test database, with Finviz's screens a stand-in ({@code @MockitoBean}): no test calls Finviz. Every stock is made up.
 * The times are New York's evenings in November, after the 16:00 close (21:00 UTC).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import(RecommendationListsDownloadService.class)
class RecommendationListsDownloadServiceTest {

    /** A Tuesday, an hour after the US market closed. */
    private static final Instant AFTER_TUESDAYS_CLOSE = Instant.parse("2026-11-10T22:00:00Z");
    private static final Instant AN_HOUR_LATER = Instant.parse("2026-11-10T23:00:00Z");
    private static final Instant AFTER_WEDNESDAYS_CLOSE = Instant.parse("2026-11-11T22:00:00Z");

    @Autowired
    private RecommendationListsDownloadService recommendationListsDownloadService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private FinvizScreens finvizScreens;

    @Test
    void downloadsTheThreeListsAndStoresEachStockUnderIbsSymbol() {
        finvizGives(List.of(finvizStock("VELQ"), finvizStock("BRK-B")), List.of(finvizStock("TAVR")),
                List.of(finvizStock("MRWS"), finvizStock("BRK-B")));

        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);
        entityManager.flush();

        assertThat(jdbc.queryForList("select recommendation_list || ' ' || symbol from listed_stock order by id",
                String.class)).containsExactly("FAST_GROWTH VELQ", "FAST_GROWTH BRK B", "SLOW_GROWTH TAVR",
                "VALUE MRWS", "VALUE BRK B");
        assertThat(jdbc.queryForMap("select source, downloaded_at, closes_read_at from stock_lists_download"))
                .containsEntry("source", "Finviz")
                .containsEntry("closes_read_at", null);
        assertThat(jdbc.queryForObject("select figures ->> 'peg' from listed_stock where symbol = 'VELQ'",
                String.class)).isEqualTo("1.4");
        assertThat(jdbc.queryForObject("select figures ->> 'symbol' from listed_stock where symbol = 'MRWS'",
                String.class)).isEqualTo("MRWS");
    }

    @Test
    void aStockFinvizSendsTwiceInOneListIsStoredOnce() {
        finvizGives(List.of(finvizStock("VELQ"), finvizStock("VELQ")), List.of(), List.of());

        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);
        entityManager.flush();

        assertThat(countOf("select count(*) from listed_stock")).isEqualTo(1);
    }

    @Test
    void nothingIsAskedWhileFinvizIsOff() {
        given(finvizScreens.isConnected()).willReturn(false);

        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);

        verify(finvizScreens, never()).stocksOf(any(), any());
        assertThat(countOf("select count(*) from stock_lists_download")).isZero();
    }

    /** Once a trading day: a second sync on the same evening asks Finviz nothing, the next evening's does. */
    @Test
    void theListsAreDownloadedOnceATradingDay() {
        finvizGives(List.of(finvizStock("VELQ")), List.of(), List.of());

        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);
        recommendationListsDownloadService.downloadWhenDue(AN_HOUR_LATER);
        verify(finvizScreens, times(1)).stocksOf(eq(FinvizScreen.FAST_GROWTH), any());

        recommendationListsDownloadService.downloadWhenDue(AFTER_WEDNESDAYS_CLOSE);
        verify(finvizScreens, times(2)).stocksOf(eq(FinvizScreen.FAST_GROWTH), any());
    }

    /** A download is the three lists of one moment: one that fails leaves none stored, and the sync goes on. */
    @Test
    void aListThatFailsStoresNoListAndThrowsNothing() {
        finvizGives(List.of(finvizStock("VELQ")), List.of(finvizStock("TAVR")), List.of());
        given(finvizScreens.stocksOf(eq(FinvizScreen.VALUE), any()))
                .willThrow(new FinvizUnavailableException("Finviz answered HTTP 500"));

        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);

        assertThat(countOf("select count(*) from stock_lists_download")).isZero();
        assertThat(countOf("select count(*) from listed_stock")).isZero();
    }

    @Test
    void beforeTheFirstDownloadNothingIsReadFromIb() {
        assertThat(recommendationListsDownloadService.listedStocksToRead(AFTER_TUESDAYS_CLOSE))
                .isEqualTo(ListedStocksToRead.NOTHING);
    }

    /** A new download: every symbol's contract is to be found, and the closes read with them. */
    @Test
    void afterADownloadEverySymbolIsToBeFound() {
        finvizGives(List.of(finvizStock("VELQ"), finvizStock("BRK-B")), List.of(finvizStock("VELQ")), List.of());
        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);

        ListedStocksToRead toRead = recommendationListsDownloadService.listedStocksToRead(AFTER_TUESDAYS_CLOSE);

        assertThat(toRead.symbolsToFind()).containsExactly("VELQ", "BRK B");
        assertThat(toRead.conIdsToReadClosesOf()).isEmpty();
    }

    /**
     * The contracts IB found are stored, and the closes are not read again until the next US market close; then the
     * contracts known are read, and the symbol not found yet is asked for again.
     */
    @Test
    void theContractsFoundAreStoredAndTheClosesReadOnceATradingDay() {
        finvizGives(List.of(finvizStock("VELQ"), finvizStock("NONE")), List.of(), List.of());
        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);
        ListedStocksToRead toRead = recommendationListsDownloadService.listedStocksToRead(AFTER_TUESDAYS_CLOSE);

        recommendationListsDownloadService.storeContractsFound(toRead, Map.of("VELQ", 7001), AFTER_TUESDAYS_CLOSE);
        entityManager.flush();

        assertThat(jdbc.queryForObject("select con_id from listed_stock where symbol = 'VELQ'", Integer.class))
                .isEqualTo(7001);
        assertThat(recommendationListsDownloadService.listedStocksToRead(AN_HOUR_LATER))
                .isEqualTo(ListedStocksToRead.NOTHING);
        ListedStocksToRead nextEvening = recommendationListsDownloadService.listedStocksToRead(AFTER_WEDNESDAYS_CLOSE);
        assertThat(nextEvening.conIdsToReadClosesOf()).containsExactly(7001);
        assertThat(nextEvening.symbolsToFind()).containsExactly("NONE");
    }

    /** A symbol IB found once is never asked for again: the next download takes its contract at once. */
    @Test
    void aSymbolFoundInAnEarlierDownloadGetsItsContractAtOnce() {
        finvizGives(List.of(finvizStock("VELQ")), List.of(), List.of());
        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);
        recommendationListsDownloadService.storeContractsFound(
                recommendationListsDownloadService.listedStocksToRead(AFTER_TUESDAYS_CLOSE), Map.of("VELQ", 7001),
                AFTER_TUESDAYS_CLOSE);
        entityManager.flush();

        recommendationListsDownloadService.downloadWhenDue(AFTER_WEDNESDAYS_CLOSE);
        ListedStocksToRead toRead = recommendationListsDownloadService.listedStocksToRead(AFTER_WEDNESDAYS_CLOSE);

        assertThat(toRead.symbolsToFind()).isEmpty();
        assertThat(toRead.conIdsToReadClosesOf()).containsExactly(7001);
    }

    @Test
    void nothingReadStoresNothing() {
        finvizGives(List.of(finvizStock("VELQ")), List.of(), List.of());
        recommendationListsDownloadService.downloadWhenDue(AFTER_TUESDAYS_CLOSE);

        recommendationListsDownloadService.storeContractsFound(ListedStocksToRead.NOTHING, Map.of("VELQ", 7001),
                AFTER_TUESDAYS_CLOSE);
        entityManager.flush();

        assertThat(countOf("select count(*) from listed_stock where con_id is not null")).isZero();
        assertThat(countOf("select count(*) from stock_lists_download where closes_read_at is not null")).isZero();
    }

    private void finvizGives(List<FinvizStock> fastGrowth, List<FinvizStock> slowGrowth, List<FinvizStock> value) {
        given(finvizScreens.isConnected()).willReturn(true);
        given(finvizScreens.stocksOf(eq(FinvizScreen.FAST_GROWTH), any())).willReturn(fastGrowth);
        given(finvizScreens.stocksOf(eq(FinvizScreen.SLOW_GROWTH), any())).willReturn(slowGrowth);
        given(finvizScreens.stocksOf(eq(FinvizScreen.VALUE), any())).willReturn(value);
    }

    /** A made-up stock: only its symbol differs from one to another. */
    private FinvizStock finvizStock(String symbol) {
        return new FinvizStock(symbol, symbol + " Inc", "Technology", "Software - Application", "USA", 5230.40, 82.15,
                34.10, 28.40, 1.4, 6.10, 4.20, 30.20, 12.50, 18.30, 9.40, 71.20, 22.10, 15.30, 0.42, null, 95.00, 1.90);
    }

    private int countOf(String query) {
        return jdbc.queryForObject(query, Integer.class);
    }
}
