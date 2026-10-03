package portfolioboss.api.response;

import portfolioboss.db.HoldingEntity;
import portfolioboss.db.ManualClosedPositionEntity;
import portfolioboss.domain.ClosedPosition;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One closed position as the UI receives it: a stretch of owning a stock, from a buy to the sell that brought it back
 * to zero — derived from a holding's trades, or entered by hand as one row ({@code source}). The component names are
 * the JSON keys and must stay stable ({@code ui/src/types/portfolio.ts} mirrors them). A figure that needs a price
 * nobody entered is {@code null}. {@code warning}, then {@code commissions}, {@code source},
 * {@code manualClosedPositionId} and {@code note} were added after the others, and like them are never renamed or
 * removed.
 *
 * @param holdingId              the holding whose trades it was derived from; {@code null} for a row entered by hand
 * @param warning                what to fix in its trades (more sold than bought), in English and shown as it is;
 *                               {@code null} when nothing needs checking
 * @param commissions            of every buy and sell in it, already taken off {@code realizedPnl}
 * @param manualClosedPositionId the row to edit or delete when {@code source} is {@code MANUAL}; {@code null} otherwise
 * @param note                   the row's note when {@code MANUAL}; {@code null} otherwise (a derived one has a note
 *                               per trade, not one of its own)
 */
public record ClosedPositionResponse(
        Long holdingId,
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
        String warning,
        BigDecimal commissions,
        ClosedPositionSource source,
        Long manualClosedPositionId,
        String note) {

    protected ClosedPositionResponse(HoldingEntity holdingEntity, ClosedPosition closedPosition) {
        this(holdingEntity.id(), holdingEntity.symbol(), holdingEntity.currency(), holdingEntity.sector(),
                closedPosition, ClosedPositionSource.TRADES, null, null);
    }

    /** Public: {@code ClosedPositionWriteService} answers a write with the row as the list will show it. */
    public ClosedPositionResponse(ManualClosedPositionEntity manualClosedPosition) {
        this(null, manualClosedPosition.symbol(), manualClosedPosition.currency(), manualClosedPosition.sector(),
                manualClosedPosition.toClosedPosition(), ClosedPositionSource.MANUAL, manualClosedPosition.id(),
                manualClosedPosition.note());
    }

    /** What both kinds share: every figure comes from {@link ClosedPosition}, so it is computed the same way. */
    private ClosedPositionResponse(Long holdingId, String symbol, String currency, String sector,
                                   ClosedPosition closedPosition, ClosedPositionSource source,
                                   Long manualClosedPositionId, String note) {
        this(holdingId,
                symbol,
                currency,
                sector,
                closedPosition.openDate(),
                closedPosition.closeDate(),
                closedPosition.holdingDays(),
                closedPosition.quantity(),
                closedPosition.averageBuyPrice(),
                closedPosition.averageSellPrice(),
                closedPosition.realizedPnl(),
                closedPosition.realizedPnlPercent(),
                closedPosition.warning(),
                closedPosition.commissions(),
                source,
                manualClosedPositionId,
                note);
    }
}
