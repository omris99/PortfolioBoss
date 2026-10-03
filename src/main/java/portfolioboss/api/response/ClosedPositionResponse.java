package portfolioboss.api.response;

import portfolioboss.db.HoldingEntity;
import portfolioboss.domain.ClosedPosition;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One closed position as the UI receives it: a stretch of owning a holding, from a buy to the sell that brought it back
 * to zero, with the holding's symbol and sector. The component names are the JSON keys and must stay stable
 * ({@code ui/src/types/portfolio.ts} mirrors them). A figure that needs a price nobody entered is {@code null}.
 * {@code warning} was added after the others, and like them is never renamed or removed.
 *
 * @param holdingId the holding whose trades it was derived from
 * @param warning   what to fix in its trades (more sold than bought), in English and shown as it is; {@code null}
 *                  when nothing needs checking
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
        BigDecimal realizedPnlPercent,
        String warning) {

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
                closedPosition.realizedPnlPercent(),
                closedPosition.warning());
    }
}
