package portfolioboss.calculation.portfolio;

import portfolioboss.ib.Holding;

import java.util.List;

/**
 * A holding's share of the whole account, as the Overview's weights show it on the Portfolio tab: the whole account is
 * the market value of the open holdings in dollars plus IB's cash — not {@code netLiquidation}, which also counts accrued
 * interest and dividends —, and a holding in another currency (no exchange rate yet), with no market value, or worth 0 or
 * less is left out. The twin of {@code wholeAccountOf} in {@code ui/src/components/graphs/portfolioWeights.ts}: change
 * both together.
 *
 * @param openHoldings IB's figures of every open holding
 * @param cash         IB's {@code TotalCashValue}; {@code null} when IB didn't report it
 */
public record PortfolioWeightsCalculator(List<Holding> openHoldings, Double cash) {

    private static final String ACCOUNT_CURRENCY = "USD";

    /** {@code null} when the holding is left out, or the whole account can't be worked out. */
    public Double percentOfAccountOf(Holding holding) {
        Double wholeAccount = wholeAccount();
        if (wholeAccount == null || !isWeighed(holding)) {
            return null;
        }
        return holding.marketValue() / wholeAccount * 100;
    }

    /** The stocks weighed plus the cash; {@code null} when the cash is unknown or the sum isn't above 0. */
    public Double wholeAccount() {
        if (cash == null) {
            return null;
        }
        double stocksValue = openHoldings.stream()
                .filter(this::isWeighed)
                .mapToDouble(Holding::marketValue)
                .sum();
        double wholeAccount = stocksValue + cash;
        return wholeAccount > 0 ? wholeAccount : null;
    }

    private boolean isWeighed(Holding holding) {
        return Double.isFinite(holding.marketValue())
                && ACCOUNT_CURRENCY.equals(holding.currency())
                && holding.marketValue() > 0;
    }
}
