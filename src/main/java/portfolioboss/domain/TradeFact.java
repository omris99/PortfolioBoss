package portfolioboss.domain;

import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trade, reduced to just what {@link HoldingHistory} needs: no id, price or note. Built from a
 * {@code TradeEntity} ({@code TradeEntity.toTradeFact()}) so this class stays free of JPA.
 */
public record TradeFact(LocalDate date, TradeSide side, BigDecimal quantity) {

    /** The quantity as it changes the position: positive for a buy, negative for a sell. */
    public BigDecimal signedQuantity() {
        return side == TradeSide.BUY ? quantity : quantity.negate();
    }
}
