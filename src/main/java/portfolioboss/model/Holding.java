package portfolioboss.model;

/**
 * A single portfolio holding as reported by Interactive Brokers.
 *
 * <p>All figures come straight from IB's {@code updatePortfolio} callback — the broker is the
 * source of truth for quantity and average cost, so nothing here is entered by hand.
 *
 * @param conId        IB's stable id for the instrument ({@code symbol} can change; this does not)
 * @param position     number of shares held (can be fractional)
 * @param averageCost  average cost per share, including commissions (IB already folds these in)
 * @param marketPrice  last price IB has for the instrument
 * @param marketValue  {@code position * marketPrice}
 * @param unrealizedPnl unrealized profit/loss in account currency
 */
public record Holding(
        String symbol,
        int conId,
        String secType,
        String currency,
        double position,
        double averageCost,
        double marketPrice,
        double marketValue,
        double unrealizedPnl,
        double realizedPnl,
        String account) {

    /** Total amount originally paid for the current shares. */
    public double costBasis() {
        return position * averageCost;
    }

    /** Unrealized P&L as a percentage of the cost basis. */
    public double unrealizedPnlPercent() {
        double basis = Math.abs(costBasis());
        return basis == 0.0 ? 0.0 : (unrealizedPnl / basis) * 100.0;
    }
}
