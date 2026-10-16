package portfolioboss.calculation;

import portfolioboss.ai.AnalystRating;
import portfolioboss.ai.RatingCounts;
import portfolioboss.ai.SourceConsensus;
import portfolioboss.model.AnalystConsensus;

import java.net.URI;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Works out the analysts' consensus the UI shows ({@link AnalystConsensus}) from the sources Claude extracted, each on
 * its own (AI_ANALYSIS_TODO.md, decision 16): the code chooses the source, not Claude, so the same results always give
 * the same one. Derived on every read from the stored analysis, never stored itself.
 *
 * <p>The choice: MarketBeat first, the most reliable of the sources (2026-10-09) — when one of its pages gives an
 * average target, the choice is made among MarketBeat's pages only, and among all the sources otherwise. Then, among
 * the sources with both a rating breakdown and an average target the one covering the most analysts, then the later
 * published, then the address first alphabetically; without such a source, the same among those with a target;
 * without any, among all. The spread of the targets is always over every source.
 *
 * <p>The consensus is worked out from the chosen source's breakdown, on one scale for every source — Strong Buy counts
 * 1, Buy 2, Hold 3, Sell 4, Strong Sell 5, and the average decides: up to 1.5 {@code STRONG_BUY}, up to 2.5
 * {@code BUY}, up to 3.5 {@code HOLD}, up to 4.5 {@code SELL}, above {@code STRONG_SELL}. Without a breakdown it is
 * the source's own label.
 *
 * <p>Example, AAPL on 2026-10-09: MarketBeat's forecast page covers 42 analysts — 1 Strong Buy, 26 Buy, 13 Hold, 2 Sell,
 * an average of 2.38 — so the consensus is {@code BUY} with MarketBeat's $340.02 target, although Financhill covers 48;
 * the four targets run from $323.86 to $340.02.
 *
 * @param sources     the consensus of every source, as Claude extracted them
 * @param marketPrice IB's latest price, which the target is measured against; {@code null} if IB did not report one
 */
public record ConsensusCalculator(List<SourceConsensus> sources, Double marketPrice) {

    /** The source the consensus is taken from whenever it gives a target — the most reliable one. */
    private static final String MARKETBEAT_HOST = "marketbeat.com";
    /** The highest average that is still the rating; above it is the next one down. */
    private static final double STRONG_BUY_UP_TO = 1.5;
    private static final double BUY_UP_TO = 2.5;
    private static final double HOLD_UP_TO = 3.5;
    private static final double SELL_UP_TO = 4.5;

    /** The chosen source's figures with the spread of every source's target; {@code null} without any source. */
    public AnalystConsensus consensus() {
        if (sources.isEmpty()) {
            return null;
        }
        SourceConsensus chosenSource = chooseSource();
        List<Double> averageTargets = sourcesWhere(sources, this::hasAverageTarget).stream()
                .map(SourceConsensus::averageTarget)
                .toList();
        return new AnalystConsensus(
                consensusRatingOf(chosenSource),
                knownAnalystCountOf(chosenSource),
                chosenSource.averageTarget(),
                upsidePercentOf(chosenSource.averageTarget()),
                chosenSource.sourceUrl(),
                averageTargets.stream().min(Comparator.naturalOrder()).orElse(null),
                averageTargets.stream().max(Comparator.naturalOrder()).orElse(null),
                averageTargets.size());
    }

    // ── choosing the source ─────────────────────────────────────────────────────────────────────

    /** Among MarketBeat's pages when one of them gives a target, among all the sources otherwise. */
    private SourceConsensus chooseSource() {
        List<SourceConsensus> marketBeatSources = sourcesWhere(sources, this::isMarketBeat);
        boolean marketBeatGivesATarget = marketBeatSources.stream().anyMatch(this::hasAverageTarget);
        return chooseAmong(marketBeatGivesATarget ? marketBeatSources : sources);
    }

    private SourceConsensus chooseAmong(List<SourceConsensus> sourcesToChooseFrom) {
        List<SourceConsensus> candidates = sourcesWhere(sourcesToChooseFrom,
                source -> hasRatingCounts(source) && hasAverageTarget(source));
        if (candidates.isEmpty()) {
            candidates = sourcesWhere(sourcesToChooseFrom, this::hasAverageTarget);
        }
        if (candidates.isEmpty()) {
            candidates = sourcesToChooseFrom;
        }
        Comparator<SourceConsensus> mostAnalystsThenLatestThenAddress =
                Comparator.comparing(this::knownAnalystCountOf, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(SourceConsensus::publishedDate, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(SourceConsensus::sourceUrl, Comparator.reverseOrder());
        return candidates.stream().max(mostAnalystsThenLatestThenAddress).orElseThrow();
    }

    private List<SourceConsensus> sourcesWhere(List<SourceConsensus> sourcesToFilter,
                                               Predicate<SourceConsensus> condition) {
        return sourcesToFilter.stream().filter(condition).toList();
    }

    /**
     * The site in the address is marketbeat.com itself ({@code www.} or any other part before it) — not another site
     * whose address merely contains the word. An address that can't be read is not MarketBeat.
     */
    private boolean isMarketBeat(SourceConsensus source) {
        try {
            String host = URI.create(source.sourceUrl()).getHost();
            return host != null && (host.equals(MARKETBEAT_HOST) || host.endsWith("." + MARKETBEAT_HOST));
        } catch (IllegalArgumentException unreadableAddress) {
            return false;
        }
    }

    /** A breakdown with at least one rating in it. */
    private boolean hasRatingCounts(SourceConsensus source) {
        return source.ratingCounts() != null && totalOf(source.ratingCounts()) > 0;
    }

    private boolean hasAverageTarget(SourceConsensus source) {
        return source.averageTarget() != null;
    }

    /** The count the source states, else its ratings added up; {@code null} when it shows neither. */
    private Integer knownAnalystCountOf(SourceConsensus source) {
        if (source.analystCount() != null) {
            return source.analystCount();
        }
        return hasRatingCounts(source) ? totalOf(source.ratingCounts()) : null;
    }

    // ── the one scale ───────────────────────────────────────────────────────────────────────────

    /** From the breakdown when the source shows one, else the source's own label, else {@code null}. */
    private AnalystRating consensusRatingOf(SourceConsensus source) {
        return hasRatingCounts(source) ? ratingFromAverage(averageScoreOf(source.ratingCounts())) : source.ratingLabel();
    }

    private int totalOf(RatingCounts ratingCounts) {
        return ratingCounts.strongBuy() + ratingCounts.buy() + ratingCounts.hold() + ratingCounts.sell()
                + ratingCounts.strongSell();
    }

    /** From 1 (all Strong Buy) to 5 (all Strong Sell); called only for a breakdown with ratings in it. */
    private double averageScoreOf(RatingCounts ratingCounts) {
        int scoreSum = ratingCounts.strongBuy() + ratingCounts.buy() * 2 + ratingCounts.hold() * 3
                + ratingCounts.sell() * 4 + ratingCounts.strongSell() * 5;
        return (double) scoreSum / totalOf(ratingCounts);
    }

    private AnalystRating ratingFromAverage(double averageScore) {
        if (averageScore <= STRONG_BUY_UP_TO) {
            return AnalystRating.STRONG_BUY;
        }
        if (averageScore <= BUY_UP_TO) {
            return AnalystRating.BUY;
        }
        if (averageScore <= HOLD_UP_TO) {
            return AnalystRating.HOLD;
        }
        return averageScore <= SELL_UP_TO ? AnalystRating.SELL : AnalystRating.STRONG_SELL;
    }

    /** How far {@code target} is above IB's price, in percent; {@code null} without a target or a price. */
    private Double upsidePercentOf(Double target) {
        if (target == null || marketPrice == null || marketPrice <= 0) {
            return null;
        }
        return (target / marketPrice - 1) * 100;
    }
}
