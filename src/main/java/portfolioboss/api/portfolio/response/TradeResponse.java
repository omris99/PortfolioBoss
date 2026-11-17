package portfolioboss.api.portfolio.response;

import portfolioboss.db.portfolio.TradeEntity;
import portfolioboss.model.portfolio.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One trade as the UI receives it. {@code note} is {@code null} when not entered; {@code price} never is (it is
 * required), and neither is {@code commission} (the default was stored instead). {@code commission} and
 * {@code investorId} were added after the others.
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
