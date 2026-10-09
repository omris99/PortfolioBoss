package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ai.AnalystRating;
import portfolioboss.ai.RatingCounts;
import portfolioboss.ai.SourceConsensus;
import portfolioboss.model.AnalystConsensus;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Which source's consensus the UI shows, and on which scale (AI_ANALYSIS_TODO.md, decision 16): the code chooses, not
 * Claude — MarketBeat first whenever it gives a target.
 */
class ConsensusCalculatorTest {

    private static final String MARKETBEAT = "https://www.marketbeat.com/stocks/NASDAQ/AAPL/forecast";
    private static final String MARKETBEAT_STOCK_PAGE = "https://www.marketbeat.com/stocks/NASDAQ/AAPL";
    private static final String FINANCHILL = "https://financhill.com/stocks/sp500/aapl";
    private static final String STOCKANALYSIS = "https://stockanalysis.com/stocks/aapl/forecast/";
    private static final String CHARTMILL = "https://www.chartmill.com/stock/quote/AAPL/analyst-ratings";
    /** AAPL's price at IB in the sync before the analysis of 2026-10-09. */
    private static final double APPLE_PRICE = 336.83;

    // ── MarketBeat first ────────────────────────────────────────────────────────────────────────

    /**
     * AAPL's analysis of 2026-10-09: MarketBeat's forecast page is chosen although Financhill and 247wallst cover more
     * analysts; MarketBeat's stock page has no target. The spread runs over the four sources with a target.
     */
    @Test
    void appleTakesMarketBeatsForecastPageOverSourcesWithMoreAnalysts() {
        AnalystConsensus consensus = new ConsensusCalculator(List.of(
                new SourceConsensus(MARKETBEAT, LocalDate.of(2026, 10, 1), 42, new RatingCounts(1, 26, 13, 2, 0),
                        340.02, AnalystRating.BUY),
                new SourceConsensus(FINANCHILL, LocalDate.of(2026, 10, 4), 48, new RatingCounts(0, 30, 16, 2, 0),
                        328.22, AnalystRating.BUY),
                new SourceConsensus("https://247wallst.com/companies/aapl/price-prediction", LocalDate.of(2026, 10, 7),
                        44, new RatingCounts(0, 25, 13, 6, 0), 328.09, AnalystRating.BUY),
                new SourceConsensus(MARKETBEAT_STOCK_PAGE, LocalDate.of(2026, 10, 5), 42,
                        new RatingCounts(1, 26, 13, 2, 0), null, AnalystRating.BUY),
                new SourceConsensus("https://247wallst.com/investing/2026/09/11/apple-stock", LocalDate.of(2026, 9, 11),
                        44, new RatingCounts(6, 19, 14, 3, 2), 323.86, null)),
                APPLE_PRICE).consensus();

        assertThat(consensus.sourceUrl()).isEqualTo(MARKETBEAT);
        assertThat(consensus.rating()).isEqualTo(AnalystRating.BUY);   // an average of 2.38
        assertThat(consensus.analystCount()).isEqualTo(42);
        assertThat(consensus.averageTarget()).isEqualTo(340.02);
        assertThat(consensus.targetUpsidePercent()).isCloseTo(0.9471, within(0.0001));   // 0.9% above the price
        assertThat(consensus.targetLow()).isEqualTo(323.86);
        assertThat(consensus.targetHigh()).isEqualTo(340.02);
        assertThat(consensus.sourceCount()).isEqualTo(4);
    }

    /** TTWO's analysis of 2026-10-09: a MarketBeat news item wins over a blog post that covers more analysts. */
    @Test
    void aMarketBeatArticleWinsOverABlogWithMoreAnalysts() {
        String marketBeatArticle = "https://www.marketbeat.com/instant-alerts/consensus-take-two-interactive";
        AnalystConsensus consensus = consensusOf(
                new SourceConsensus(marketBeatArticle, LocalDate.of(2026, 10, 7), 21, new RatingCounts(1, 18, 1, 1, 0),
                        298.89, AnalystRating.BUY),
                new SourceConsensus("https://www.tikr.com/blog/take-two-interactives-free-cash-flow",
                        LocalDate.of(2026, 9, 15), 29, new RatingCounts(0, 28, 0, 1, 0), 286.44, AnalystRating.BUY),
                new SourceConsensus("https://tickernerd.com/stock/ttwo-forecast", LocalDate.of(2026, 9, 17), 29,
                        new RatingCounts(0, 28, 0, 1, 0), null, AnalystRating.BUY));

        assertThat(consensus.sourceUrl()).isEqualTo(marketBeatArticle);
        assertThat(consensus.rating()).isEqualTo(AnalystRating.BUY);
        assertThat(consensus.analystCount()).isEqualTo(21);
        assertThat(consensus.averageTarget()).isEqualTo(298.89);
        assertThat(consensus.targetLow()).isEqualTo(286.44);
        assertThat(consensus.sourceCount()).isEqualTo(2);
    }

    /** MarketBeat's only page gives no target: the choice is made among all the sources, as without MarketBeat. */
    @Test
    void aMarketBeatPageWithoutATargetLeavesTheChoiceToAllTheSources() {
        AnalystConsensus consensus = consensusOf(
                new SourceConsensus(MARKETBEAT_STOCK_PAGE, null, 42, new RatingCounts(1, 26, 13, 2, 0), null,
                        AnalystRating.BUY),
                new SourceConsensus(FINANCHILL, null, 48, new RatingCounts(0, 30, 16, 2, 0), 328.22, null));

        assertThat(consensus.sourceUrl()).isEqualTo(FINANCHILL);
    }

    /** MarketBeat is the site in the address — marketbeat.com, with or without "www." —, not the word anywhere in it. */
    @Test
    void onlyMarketBeatsOwnSiteCountsAsMarketBeat() {
        AnalystConsensus withoutMarketBeat = consensusOf(
                new SourceConsensus("https://marketbeat.com.example.net/aapl", null, 10, null, 300.0, null),
                new SourceConsensus("https://example.com/news/marketbeat.com-says", null, 12, null, 310.0, null),
                new SourceConsensus(FINANCHILL, null, 48, new RatingCounts(0, 30, 16, 2, 0), 328.22, null));
        AnalystConsensus withMarketBeat = consensusOf(
                new SourceConsensus("https://marketbeat.com/stocks/NASDAQ/AAPL/forecast", null, 10, null, 300.0, null),
                new SourceConsensus(FINANCHILL, null, 48, new RatingCounts(0, 30, 16, 2, 0), 328.22, null));

        assertThat(withoutMarketBeat.sourceUrl()).isEqualTo(FINANCHILL);
        assertThat(withMarketBeat.sourceUrl()).isEqualTo("https://marketbeat.com/stocks/NASDAQ/AAPL/forecast");
    }

    // ── choosing among the sources ──────────────────────────────────────────────────────────────

    @Test
    void aSourceWithABreakdownWinsOverOneWithMoreAnalystsButNoBreakdown() {
        AnalystConsensus consensus = consensusOf(
                new SourceConsensus(STOCKANALYSIS, null, 60, null, 328.09, AnalystRating.STRONG_BUY),
                new SourceConsensus(FINANCHILL, null, 48, new RatingCounts(0, 30, 16, 2, 0), 328.22, null));

        assertThat(consensus.sourceUrl()).isEqualTo(FINANCHILL);
    }

    @Test
    void asManyAnalystsGoesToTheLaterPublishedSource() {
        AnalystConsensus consensus = consensusOf(
                new SourceConsensus(FINANCHILL, LocalDate.of(2026, 10, 5), 48, new RatingCounts(0, 30, 16, 2, 0),
                        328.22, null),
                new SourceConsensus(STOCKANALYSIS, LocalDate.of(2026, 10, 7), 48, new RatingCounts(2, 26, 13, 7, 0),
                        328.09, null));

        assertThat(consensus.sourceUrl()).isEqualTo(STOCKANALYSIS);
    }

    @Test
    void asManyAnalystsOnTheSameDateGoesToTheAddressFirstAlphabetically() {
        LocalDate sameDate = LocalDate.of(2026, 10, 5);
        AnalystConsensus consensus = consensusOf(
                new SourceConsensus("https://b.example/aapl", sameDate, 48, new RatingCounts(0, 30, 16, 2, 0), 330.0,
                        null),
                new SourceConsensus("https://a.example/aapl", sameDate, 48, new RatingCounts(0, 30, 16, 2, 0), 331.0,
                        null));

        assertThat(consensus.sourceUrl()).isEqualTo("https://a.example/aapl");
    }

    /** No source shows a breakdown: the one with a target covering the most analysts, with its own label. */
    @Test
    void withoutAnyBreakdownTheSourceWithATargetAndTheMostAnalystsGivesItsOwnLabel() {
        AnalystConsensus consensus = consensusOf(
                new SourceConsensus("https://a.example", null, 40, null, 100.0, AnalystRating.HOLD),
                new SourceConsensus("https://b.example", null, 55, null, 110.0, AnalystRating.BUY),
                new SourceConsensus("https://c.example", null, 70, null, null, AnalystRating.SELL));

        assertThat(consensus.sourceUrl()).isEqualTo("https://b.example");
        assertThat(consensus.rating()).isEqualTo(AnalystRating.BUY);
        assertThat(consensus.analystCount()).isEqualTo(55);
        assertThat(consensus.targetLow()).isEqualTo(100.0);
        assertThat(consensus.targetHigh()).isEqualTo(110.0);
        assertThat(consensus.sourceCount()).isEqualTo(2);
    }

    /** AEVA in session 0: one source, 11 analysts and a $30.6 target, but neither a breakdown nor a label. */
    @Test
    void aevaHasATargetButNoConsensus() {
        AnalystConsensus consensus = consensusOf(new SourceConsensus(CHARTMILL, null, 11, null, 30.6, null));

        assertThat(consensus.rating()).isNull();
        assertThat(consensus.analystCount()).isEqualTo(11);
        assertThat(consensus.averageTarget()).isEqualTo(30.6);
        assertThat(consensus.targetLow()).isEqualTo(30.6);
        assertThat(consensus.targetHigh()).isEqualTo(30.6);
        assertThat(consensus.sourceCount()).isEqualTo(1);
    }

    @Test
    void withoutAnyTargetTheSourceWithTheMostAnalystsIsStillShown() {
        AnalystConsensus consensus = consensusOf(
                new SourceConsensus("https://a.example", null, null, new RatingCounts(0, 10, 10, 0, 0), null, null),
                new SourceConsensus("https://b.example", null, 30, null, null, AnalystRating.HOLD));

        assertThat(consensus.sourceUrl()).isEqualTo("https://b.example");
        assertThat(consensus.rating()).isEqualTo(AnalystRating.HOLD);
        assertThat(consensus.averageTarget()).isNull();
        assertThat(consensus.targetLow()).isNull();
        assertThat(consensus.targetHigh()).isNull();
        assertThat(consensus.sourceCount()).isZero();
    }

    @Test
    void noSourceAtAllHasNoConsensus() {
        assertThat(new ConsensusCalculator(List.of(), APPLE_PRICE).consensus()).isNull();
    }

    // ── the target against the price ────────────────────────────────────────────────────────────

    @Test
    void aTargetAboveThePriceIsAPositiveUpside() {
        AnalystConsensus consensus = new ConsensusCalculator(
                List.of(new SourceConsensus(CHARTMILL, null, 11, null, 30.6, null)), 25.5).consensus();

        assertThat(consensus.targetUpsidePercent()).isCloseTo(20.0, within(1e-9));
    }

    @Test
    void withoutATargetOrAPriceThereIsNoUpside() {
        List<SourceConsensus> withTarget = List.of(new SourceConsensus(CHARTMILL, null, 11, null, 30.6, null));
        List<SourceConsensus> withoutTarget =
                List.of(new SourceConsensus(CHARTMILL, null, 30, null, null, AnalystRating.HOLD));

        assertThat(new ConsensusCalculator(withTarget, null).consensus().targetUpsidePercent()).isNull();
        assertThat(new ConsensusCalculator(withTarget, 0.0).consensus().targetUpsidePercent()).isNull();
        assertThat(new ConsensusCalculator(withoutTarget, APPLE_PRICE).consensus().targetUpsidePercent()).isNull();
    }

    // ── the one scale: Strong Buy 1 … Strong Sell 5, the average decides ────────────────────────

    /** Financhill's AAPL breakdown in session 0: 30 Buy, 16 Hold, 2 Sell average 2.42. */
    @Test
    void financhillsAppleBreakdownIsBuy() {
        assertThat(ratingFrom(new RatingCounts(0, 30, 16, 2, 0))).isEqualTo(AnalystRating.BUY);
    }

    /** The breakdown, not the source's own label, so every source is on one scale. */
    @Test
    void theBreakdownDecidesOverTheSourcesOwnLabel() {
        AnalystConsensus consensus = consensusOf(new SourceConsensus(FINANCHILL, null, 48,
                new RatingCounts(0, 30, 16, 2, 0), 328.22, AnalystRating.STRONG_BUY));

        assertThat(consensus.rating()).isEqualTo(AnalystRating.BUY);
    }

    @Test
    void anAverageExactlyOnALimitBelongsToTheBetterRating() {
        assertThat(ratingFrom(new RatingCounts(1, 1, 0, 0, 0))).isEqualTo(AnalystRating.STRONG_BUY);   // 1.5
        assertThat(ratingFrom(new RatingCounts(0, 1, 1, 0, 0))).isEqualTo(AnalystRating.BUY);          // 2.5
        assertThat(ratingFrom(new RatingCounts(0, 0, 1, 1, 0))).isEqualTo(AnalystRating.HOLD);         // 3.5
        assertThat(ratingFrom(new RatingCounts(0, 0, 0, 1, 1))).isEqualTo(AnalystRating.SELL);         // 4.5
    }

    @Test
    void anAverageJustAboveALimitBelongsToTheNextRatingDown() {
        assertThat(ratingFrom(new RatingCounts(1, 2, 0, 0, 0))).isEqualTo(AnalystRating.BUY);          // 1.67
        assertThat(ratingFrom(new RatingCounts(0, 0, 0, 1, 2))).isEqualTo(AnalystRating.STRONG_SELL);  // 4.67
    }

    /** A breakdown of nothing but zeros is no breakdown: the source's own label decides. */
    @Test
    void anEmptyBreakdownLeavesItToTheSourcesOwnLabel() {
        AnalystConsensus consensus = consensusOf(new SourceConsensus(FINANCHILL, null, null,
                new RatingCounts(0, 0, 0, 0, 0), 328.22, AnalystRating.HOLD));

        assertThat(consensus.rating()).isEqualTo(AnalystRating.HOLD);
        assertThat(consensus.analystCount()).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private AnalystConsensus consensusOf(SourceConsensus... sources) {
        return new ConsensusCalculator(List.of(sources), null).consensus();
    }

    /** The consensus of a single source that shows only this breakdown and a target. */
    private AnalystRating ratingFrom(RatingCounts ratingCounts) {
        return consensusOf(new SourceConsensus("https://a.example", null, null, ratingCounts, 100.0, null)).rating();
    }
}
