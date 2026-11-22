package portfolioboss.sync;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.calculation.splits.StockSplit;
import portfolioboss.db.splits.StockSplitRepository;
import portfolioboss.flex.FlexReport;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.flex.FlexReportPeriod;
import portfolioboss.flex.FlexReportTrade;
import portfolioboss.tax.FlexReportStorageService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sync's step that stores the splits of IB's Flex reports (STOCK_SPLITS_TODO.md, session 1.3), against the real
 * PostgreSQL test database: the reports stored through the app's own service, made-up rows in IB's shape.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({StockSplitDetectionService.class, FlexReportStorageService.class})
class StockSplitDetectionServiceTest {

    private static final int CON_ID = 1111;
    private static final LocalDateTime SPLIT_TIME = LocalDateTime.of(2026, 3, 10, 20, 25);

    @Autowired
    private StockSplitDetectionService stockSplitDetectionService;

    @Autowired
    private FlexReportStorageService flexReportStorageService;

    @Autowired
    private StockSplitRepository stockSplitRepository;

    /** 10 held, "SPLIT 4 FOR 1" adding 30: stored as IB wrote it. */
    @Test
    void aSplitUnderstoodIsStored() {
        storeReportWithASplitAdding("30");

        stockSplitDetectionService.storeNewSplits();

        assertThat(stockSplitRepository.storedStockSplits().splits()).containsExactly(
                new StockSplit("A1", "WXYZ", CON_ID, CON_ID, SPLIT_TIME, 1, 4));
    }

    /** Every sync brings the same rows again: a split is stored once, by IB's action id (decision 2). */
    @Test
    void aSplitAlreadyStoredIsNotStoredAgain() {
        storeReportWithASplitAdding("30");

        stockSplitDetectionService.storeNewSplits();
        storeReportWithASplitAdding("30");
        stockSplitDetectionService.storeNewSplits();

        assertThat(stockSplitRepository.count()).isEqualTo(1);
    }

    /** 10 held, "SPLIT 4 FOR 1" — but 20 added: not understood, so nothing is stored. */
    @Test
    void aSplitNotUnderstoodIsNotStored() {
        storeReportWithASplitAdding("20");

        stockSplitDetectionService.storeNewSplits();

        assertThat(stockSplitRepository.count()).isZero();
    }

    @Test
    void nothingIsStoredWithoutACorporateAction() {
        stockSplitDetectionService.storeNewSplits();

        assertThat(stockSplitRepository.count()).isZero();
    }

    private void storeReportWithASplitAdding(String sharesAdded) {
        FlexReportTrade buy = new FlexReportTrade("101", "201", CON_ID, "WXYZ", "STK", "USD", LocalDate.of(2026, 1, 5),
                LocalDateTime.of(2026, 1, 5, 10, 0), LocalDate.of(2026, 1, 6), "BUY", new BigDecimal("10"),
                new BigDecimal("400"), new BigDecimal("-5"), "USD", new BigDecimal("-4005"), "ExchTrade", null);
        FlexReportCorporateAction split = new FlexReportCorporateAction("901", "A1", "FS", CON_ID, "WXYZ", "STK",
                SPLIT_TIME, new BigDecimal(sharesAdded), BigDecimal.ZERO, BigDecimal.ZERO,
                "WXYZ(US0000000000) SPLIT 4 FOR 1 (WXYZ, WXYZ CORP, US0000000000)");
        flexReportStorageService.storeReport(new FlexReport(
                new FlexReportPeriod(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 13)), List.of(buy), List.of(),
                List.of(split)), Instant.parse("2026-03-14T10:00:00Z"));
    }
}
