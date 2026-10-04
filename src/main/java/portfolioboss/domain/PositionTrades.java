package portfolioboss.domain;

import portfolioboss.model.Holding;
import portfolioboss.utils.Utils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A holding or a manual position, with every trade entered for it — of every investor. What
 * {@link InvestorSummaryCalculator} works from, and where a holding's quantity is divided between the investors. Free of
 * JPA: built by
 * {@code HoldingEntity.toPositionTrades()} and {@code ManualPositionEntity.toPositionTrades()}.
 *
 * <p>Example: IB reports 39 NVDA, and the other investor's trades add up to 24. They hold 24, and the account owner the 15
 * left — whatever the owner's own trades say, since IB is the source of truth for the quantity.
 *
 * @param ibHolding IB's last reading of the holding — for a {@code CLOSED} one, 0 shares at the last price seen — or
 *                  {@code null} for a manual position, which IB never reported
 * @param trades    every trade entered for it, of every investor
 */
public record PositionTrades(String symbol, String currency, Holding ibHolding, List<TradeFact> trades) {

    /** A holding IB reported, not a manual position. */
    public boolean isHolding() {
        return ibHolding != null;
    }

    public boolean isInAccountCurrency() {
        return InvestorSummary.ACCOUNT_CURRENCY.equals(currency);
    }

    /** The investors with at least one trade here, by id. */
    public List<Long> investorIds() {
        return trades.stream().map(TradeFact::investorId).distinct().sorted().toList();
    }

    public List<TradeFact> tradesOf(long investorId) {
        return trades.stream().filter(trade -> trade.investorId() == investorId).toList();
    }

    /** Their trades alone, so that their closed positions and average cost are theirs only. */
    public HoldingHistory historyOf(long investorId) {
        return HoldingHistory.of(tradesOf(investorId));
    }

    /** What the investor's trades add up to: buys less sells. */
    public BigDecimal quantityOf(long investorId) {
        return historyOf(investorId).netQuantity();
    }

    /**
     * How IB's quantity divides between the investors, by id: every other investor holds what their trades add up to,
     * and the account owner whatever is left — below zero when the others' trades add up to more than IB reports. Only
     * those holding something. Without the account owner if IB reported no quantity.
     */
    public Map<Long, BigDecimal> quantitiesByInvestor(long accountOwnerId) {
        Map<Long, BigDecimal> quantities = new TreeMap<>();
        BigDecimal accountOwnerQuantity = ibQuantity();
        for (long investorId : otherInvestorIds(accountOwnerId)) {
            BigDecimal quantity = quantityOf(investorId);
            quantities.put(investorId, quantity);
            accountOwnerQuantity = accountOwnerQuantity == null ? null : accountOwnerQuantity.subtract(quantity);
        }
        if (accountOwnerQuantity != null) {
            quantities.put(accountOwnerId, accountOwnerQuantity);
        }
        quantities.values().removeIf(quantity -> quantity.signum() == 0);
        return quantities;
    }

    /** What IB reports: 0 for a manual position, which IB doesn't hold; {@code null} if IB reported no number. */
    public BigDecimal ibQuantity() {
        return isHolding() ? Utils.decimalOrNull(ibHolding.position()) : BigDecimal.ZERO;
    }

    /** {@code null} for a manual position, or if IB reported none. */
    public BigDecimal ibMarketPrice() {
        return isHolding() ? Utils.decimalOrNull(ibHolding.marketPrice()) : null;
    }

    /** {@code null} for a manual position, or if IB reported none. */
    public BigDecimal ibMarketValue() {
        return isHolding() ? Utils.decimalOrNull(ibHolding.marketValue()) : null;
    }

    /**
     * {@code position × averageCost}, with IB's commissions in it. 0 once nothing is held, even if IB's average cost is
     * unknown; {@code null} for a manual position.
     */
    public BigDecimal ibCostBasis() {
        if (!isHolding()) {
            return null;
        }
        if (ibHolding.position() == 0) {
            return BigDecimal.ZERO;
        }
        return Utils.decimalOrNull(ibHolding.costBasis());
    }

    private List<Long> otherInvestorIds(long accountOwnerId) {
        return investorIds().stream().filter(investorId -> investorId != accountOwnerId).toList();
    }
}
