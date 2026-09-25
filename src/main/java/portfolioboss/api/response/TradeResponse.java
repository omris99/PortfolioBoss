package portfolioboss.api.response;

import portfolioboss.db.TradeEntity;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One trade as the UI receives it. {@code price} and {@code note} are {@code null} when not entered. */
public record TradeResponse(
        long id,
        LocalDate tradeDate,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal price,
        String note) {

    public TradeResponse(TradeEntity trade) {
        this(trade.id(), trade.tradeDate(), trade.side(), trade.quantity(), trade.price(), trade.note());
    }
}
