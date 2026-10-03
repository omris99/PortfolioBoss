package portfolioboss.api.response;

import portfolioboss.db.TradeEntity;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trade as the UI receives it. {@code price} and {@code note} are {@code null} when not entered; {@code commission}
 * never is (the default was stored instead), and was added after the others.
 */
public record TradeResponse(
        long id,
        LocalDate tradeDate,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal price,
        String note,
        BigDecimal commission) {

    public TradeResponse(TradeEntity trade) {
        this(trade.id(), trade.tradeDate(), trade.side(), trade.quantity(), trade.price(), trade.note(),
                trade.commission());
    }
}
