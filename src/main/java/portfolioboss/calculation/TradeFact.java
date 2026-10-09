package portfolioboss.calculation;

import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trade, reduced to just what {@link HoldingHistory} needs: no note. Built from a {@code TradeEntity}
 * ({@code TradeEntity.toTradeFact()}) so this class stays free of JPA.
 *
 * @param id         the {@code trade} row, so a closed position can say which trades it is made of
 * @param investorId whose trade it is: their shares, their cash ({@link InvestorSummaryCalculator})
 * @param price      the price per share, or {@code null} if none was entered
 * @param commission what the broker charged for it — never {@code null}: a trade entered without one is stored with
 *                   the default ({@code OrderCommission.defaultFor})
 */
public record TradeFact(Long id, long investorId, LocalDate date, TradeSide side, BigDecimal quantity,
                        BigDecimal price, BigDecimal commission) {

    /** The quantity as it changes the position: positive for a buy, negative for a sell. */
    public BigDecimal signedQuantity() {
        return side == TradeSide.BUY ? quantity : quantity.negate();
    }

    /** {@code quantity × price}, or {@code null} if no price was entered. */
    public BigDecimal amount() {
        return price == null ? null : quantity.multiply(price);
    }

    /**
     * What the trade did to the investor's cash: a buy takes its amount out, a sell brings its amount in, and either way
     * the commission goes out. Buy 10 at 100 with a $5 commission: -1,005. {@code null} if no price was entered.
     */
    public BigDecimal cashFlow() {
        BigDecimal amount = amount();
        if (amount == null) {
            return null;
        }
        BigDecimal signedAmount = side == TradeSide.BUY ? amount.negate() : amount;
        return signedAmount.subtract(commission);
    }
}
