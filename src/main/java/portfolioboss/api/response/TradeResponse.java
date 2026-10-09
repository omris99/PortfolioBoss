package portfolioboss.api.response;

import portfolioboss.calculation.TradeSide;
import portfolioboss.db.TradeEntity;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trade as the UI receives it. {@code price} and {@code note} are {@code null} when not entered; {@code commission}
 * never is (the default was stored instead). {@code commission} and {@code investorId} were added after the others.
 *
 * @param investorId whose trade it is — one of {@code PortfolioResponse.investors}
 */
public record TradeResponse(
        long id,
        LocalDate tradeDate,
        TradeSide side,
        BigDecimal quantity,
        BigDecimal price,
        String note,
        BigDecimal commission,
        long investorId) {

    public TradeResponse(TradeEntity trade) {
        this(trade.id(), trade.tradeDate(), trade.side(), trade.quantity(), trade.price(), trade.note(),
                trade.commission(), trade.investorId());
    }
}
