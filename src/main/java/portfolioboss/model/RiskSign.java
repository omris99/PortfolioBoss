package portfolioboss.model;

/**
 * The five risk signs of a stock the recommendation may pick (RECOMMENDATIONS_TODO.md, decision 5), worked out by
 * {@code calculation.RecommendationCandidate}. Each has a weight of its own in {@code calculation.RecommendationCalculator}:
 * a stock whose signs weigh more than 2 together is not picked. The limits and the weights are opening numbers, for the
 * measurement to test. The order of the constants is the order they are listed in. The names are JSON values.
 */
public enum RiskSign {
    /** A net margin below 0 over the last 12 months: the company loses money (Finnhub). */
    LOSES_MONEY,
    /** A debt more than twice the equity (Finnhub). */
    HIGH_DEBT,
    /** A price more than 50 times the earnings per share of the last 12 months (Finnhub). */
    EXPENSIVE,
    /** Less revenue over the last 12 months than over the 12 before (Finnhub). */
    REVENUE_FALLING,
    /** A price lower than a year ago (decision 1) — from IB's closes, not from Finnhub. */
    RECOVERING
}
