package portfolioboss.api.response;

import portfolioboss.db.HoldingEntity;
import portfolioboss.domain.ClosedPosition;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One closed position as the UI receives it: a stretch of owning a holding, from a buy to the sell that brought it back
 * to zero, with the holding's symbol and sector. The component names are the JSON keys and must stay stable
 * ({@code ui/src/types/portfolio.ts} mirrors them). A figure that needs a price nobody entered is {@code null}.
 *
 * @param holdingId the holding whose trades it was derived from
 */
public record ClosedPositionResponse(
        long holdingId,
        String symbol,
        String currency,
        String sector,
        LocalDate openDate,
        LocalDate closeDate,
        long holdingDays,
        BigDecimal quantity,
        BigDecimal averageBuyPrice,
        BigDecimal averageSellPrice,
        BigDecimal realizedPnl,
        BigDecimal realizedPnlPercent) {

    protected ClosedPositionResponse(HoldingEntity holdingEntity, ClosedPosition closedPosition) {
        this(holdingEntity.id(),
                holdingEntity.symbol(),
                holdingEntity.currency(),
                holdingEntity.sector(),
                closedPosition.openDate(),
                closedPosition.closeDate(),
                closedPosition.holdingDays(),
                closedPosition.quantity(),
                closedPosition.averageBuyPrice(),
                closedPosition.averageSellPrice(),
                closedPosition.realizedPnl(),
                closedPosition.realizedPnlPercent());
    }
}
