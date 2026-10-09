package portfolioboss.ai;

/**
 * How many analysts give each rating, as one source shows them — a source that shows a breakdown shows all five, a
 * rating nobody gives being 0. The consensus on one scale for every source is worked out from them by
 * {@code calculation.ConsensusCalculator} (AI_ANALYSIS_TODO.md, decision 16).
 */
public record RatingCounts(int strongBuy, int buy, int hold, int sell, int strongSell) {
}
