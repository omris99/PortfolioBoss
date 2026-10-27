package portfolioboss.stocks;

/**
 * Four of a company's figures from Finnhub, for the risk signs of RECOMMENDATIONS_TODO.md, decision 5. Each one is
 * {@code null} when Finnhub doesn't give it — a figure that isn't there raises no sign.
 *
 * @param revenueGrowthPercent the revenue of the last 12 months against the 12 before, in percent ({@code 14.24} is
 *                             14.24%)
 * @param netMarginPercent     the net profit of the last 12 months out of the revenue, in percent; negative for a loss
 * @param debtToEquity         the total debt divided by the equity, in the latest quarter's report — a ratio, not a
 *                             percent ({@code 0.78}: the debt is 0.78 of the equity)
 * @param peRatio              the price divided by the earnings per share of the last 12 months
 */
public record CompanyFacts(Double revenueGrowthPercent, Double netMarginPercent, Double debtToEquity, Double peRatio) {
}
