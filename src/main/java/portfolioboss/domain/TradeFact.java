package portfolioboss.domain;

import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trade, reduced to just what {@link HoldingHistory} needs: no id or note. Built from a
 * {@code TradeEntity} ({@code TradeEntity.toTradeFact()}) so this class stays free of JPA.
 *
 * @param price      the price per share, or {@code null} if none was entered
 * @param commission what the broker charged for it — never {@code null}: a trade entered without one is stored with
 *                   the default ({@code Utils.calculateOrderCommission})
 */
public record TradeFact(LocalDate date, TradeSide side, BigDecimal quantity, BigDecimal price, BigDecimal commission) {

    /** The quantity as it changes the position: positive for a buy, negative for a sell. */
    public BigDecimal signedQuantity() {
        return side == TradeSide.BUY ? quantity : quantity.negate();
    }

    /** {@code quantity × price}, or {@code null} if no price was entered. */
    public BigDecimal amount() {
        return price == null ? null : quantity.multiply(price);
    }
}
