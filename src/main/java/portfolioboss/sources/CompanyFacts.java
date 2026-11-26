package portfolioboss.sources;

/**
 * Four of a company's figures, for the risk signs of RECOMMENDATIONS_TODO.md, decision 5 — the same four whichever
 * source gave them. Each one is {@code null} when the source doesn't give it: a figure that isn't there raises no sign.
 *
 * @param revenueGrowthPercent how fast the revenue grows, in percent ({@code 14.24} is 14.24%): Finnhub's is the last 12
 *                             months against the 12 before; Finviz has no such figure, and its latest quarter against
 *                             the same quarter a year before stands in for it (RECOMMENDATIONS_FINVIZ_TODO.md,
 *                             decision 12)
 * @param netMarginPercent     the net profit out of the revenue, in percent; negative for a loss
 * @param debtToEquity         the total debt divided by the equity — a ratio, not a percent ({@code 0.78}: the debt is
 *                             0.78 of the equity)
 * @param peRatio              the price divided by the earnings per share of the last 12 months
 */
public record CompanyFacts(Double revenueGrowthPercent, Double netMarginPercent, Double debtToEquity, Double peRatio) {
}
