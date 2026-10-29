package portfolioboss.ib;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading a stock's headlines page by page back in time, until its pages run out or reach the half year — on made-up
 * pages of IB's size, 300.
 */
class NewsHeadlinePagingTest {

    private static final int SNOW_CON_ID = 444884769;
    private static final int PANW_CON_ID = 110619459;
    private static final LocalDateTime OLDEST_WANTED = LocalDateTime.of(2026, 4, 27, 0, 0);
    private static final LocalDateTime OCTOBER_28 = LocalDateTime.of(2026, 10, 28, 15, 0);

    @Test
    void theFirstRoundAsksEveryStockForItsNewestPage() {
        NewsHeadlinePaging paging = new NewsHeadlinePaging(List.of(SNOW_CON_ID, PANW_CON_ID), OLDEST_WANTED);

        assertThat(paging.nextPages()).containsOnlyKeys(SNOW_CON_ID, PANW_CON_ID);
        assertThat(paging.nextPages().get(SNOW_CON_ID)).isNull();
    }

    /** A full page that brought new headlines and isn't back to the half year: the next page is before its oldest. */
    @Test
    void aFullPageAsksForThePageBeforeItsOldestHeadline() {
        NewsHeadlinePaging paging = new NewsHeadlinePaging(List.of(SNOW_CON_ID), OLDEST_WANTED);
        List<NewsHeadline> page = pageOf(300, OCTOBER_28, "first");

        paging.addPage(SNOW_CON_ID, page);

        assertThat(paging.nextPages()).containsEntry(SNOW_CON_ID, page.getLast().publishedAt());
    }

    @Test
    void aPageThatIsNotFullEndsTheStock() {
        NewsHeadlinePaging paging = new NewsHeadlinePaging(List.of(SNOW_CON_ID, PANW_CON_ID), OLDEST_WANTED);

        paging.addPage(SNOW_CON_ID, pageOf(17, OCTOBER_28, "first"));

        assertThat(paging.nextPages()).containsOnlyKeys(PANW_CON_ID);
    }

    /** The request failed or came late: the stock keeps what it has, and isn't asked again. */
    @Test
    void anEmptyPageEndsTheStock() {
        NewsHeadlinePaging paging = new NewsHeadlinePaging(List.of(SNOW_CON_ID), OLDEST_WANTED);

        paging.addPage(SNOW_CON_ID, List.of());

        assertThat(paging.hasStocksToRead()).isFalse();
        assertThat(paging.headlinesByConId()).isEmpty();
    }

    @Test
    void aPageBackToTheHalfYearEndsTheStock() {
        NewsHeadlinePaging paging = new NewsHeadlinePaging(List.of(SNOW_CON_ID), OLDEST_WANTED);

        paging.addPage(SNOW_CON_ID, pageOf(300, OLDEST_WANTED.plusHours(299), "first"));

        assertThat(paging.hasStocksToRead()).isFalse();
    }

    /** IB sends the same article again: kept once, and a page of nothing new ends the stock. */
    @Test
    void aHeadlineOnTwoPagesIsKeptOnceAndAPageOfNothingNewEndsTheStock() {
        NewsHeadlinePaging paging = new NewsHeadlinePaging(List.of(SNOW_CON_ID), OLDEST_WANTED);
        List<NewsHeadline> page = pageOf(300, OCTOBER_28, "first");

        paging.addPage(SNOW_CON_ID, page);
        paging.addPage(SNOW_CON_ID, page);

        assertThat(paging.hasStocksToRead()).isFalse();
        assertThat(paging.headlinesByConId().get(SNOW_CON_ID)).hasSize(300);
    }

    @Test
    void keepsEveryPagesHeadlines() {
        NewsHeadlinePaging paging = new NewsHeadlinePaging(List.of(SNOW_CON_ID), OLDEST_WANTED);
        List<NewsHeadline> firstPage = pageOf(300, OCTOBER_28, "first");

        paging.addPage(SNOW_CON_ID, firstPage);
        paging.addPage(SNOW_CON_ID, pageOf(120, firstPage.getLast().publishedAt().minusHours(1), "second"));

        assertThat(paging.headlinesByConId().get(SNOW_CON_ID)).hasSize(420);
    }

    /** {@code count} headlines an hour apart, the newest first, as IB sends them. */
    private List<NewsHeadline> pageOf(int count, LocalDateTime newest, String pageName) {
        List<NewsHeadline> page = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            page.add(new NewsHeadline(newest.minusHours(index), "DJ-N$" + pageName + index, "A headline"));
        }
        return page;
    }
}
