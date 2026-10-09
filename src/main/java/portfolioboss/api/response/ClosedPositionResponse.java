package portfolioboss.api.response;

import portfolioboss.calculation.ClosedPosition;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.ManualPositionEntity;
import portfolioboss.db.TradeEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * The shares sold in one position period as the UI receives it: a stretch of owning a stock that a sell brought back to
 * zero, or one still held that has had a sell already ({@code remainingQuantity} above 0), at average cost — of a
 * holding or of a manual position ({@code source}). The component names are the JSON keys and must stay stable
 * ({@code ui/src/types/portfolio.ts} mirrors them). A figure that needs a price nobody entered is {@code null}.
 * {@code warning}, {@code commissions}, {@code source}, {@code manualPositionId}, {@code note},
 * {@code remainingQuantity}, {@code trades} and {@code investorId} were added after the others, and like them are never
 * renamed or removed. Each investor's trades make closed positions of their own, so one holding can have a closed
 * position of the account owner's while the other investor still holds their shares.
 *
 * @param holdingId         the holding whose trades it was derived from; {@code null} for a manual position
 * @param quantity          the shares sold in the period so far
 * @param averageBuyPrice   the average cost of the shares sold
 * @param warning           what to fix in its trades (more sold than bought), in English and shown as it is;
 *                          {@code null} when nothing needs checking
 * @param commissions       of every sell, and of the buys the part that went with the shares sold — already taken off
 *                          {@code realizedPnl}
 * @param manualPositionId  the manual position it comes from, to correct it or add trades; {@code null} otherwise
 * @param note              the manual position's note; {@code null} for a holding (its notes are on its trades)
 * @param remainingQuantity the shares of the period still held: 0 once a sell brought it back to zero
 * @param trades            every trade of the period, by date
 * @param investorId        whose shares were sold — the period is made of that investor's trades only
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
        Long manualPositionId,
        String note,
        BigDecimal remainingQuantity,
        List<TradeResponse> trades,
        long investorId) {

    public ClosedPositionResponse(HoldingEntity holdingEntity, ClosedPosition closedPosition, long investorId) {
        this(holdingEntity.id(), holdingEntity.symbol(), holdingEntity.currency(), holdingEntity.sector(),
                closedPosition, ClosedPositionSource.TRADES, null, null,
                tradesOfPeriod(holdingEntity.trades(), closedPosition), investorId);
    }

    public ClosedPositionResponse(ManualPositionEntity manualPosition, ClosedPosition closedPosition,
                                  long investorId) {
        this(null, manualPosition.symbol(), manualPosition.currency(), manualPosition.sector(), closedPosition,
                ClosedPositionSource.MANUAL, manualPosition.id(), manualPosition.note(),
                tradesOfPeriod(manualPosition.trades(), closedPosition), investorId);
    }

    /** What both kinds share: every figure comes from {@link ClosedPosition}, so it is computed the same way. */
    private ClosedPositionResponse(Long holdingId, String symbol, String currency, String sector,
                                   ClosedPosition closedPosition, ClosedPositionSource source, Long manualPositionId,
                                   String note, List<TradeResponse> trades, long investorId) {
        this(holdingId,
                symbol,
                currency,
                sector,
                closedPosition.openDate(),
                closedPosition.closeDate(),
                closedPosition.holdingDays(),
                closedPosition.soldQuantity(),
                closedPosition.averageBuyPrice(),
                closedPosition.averageSellPrice(),
                closedPosition.realizedPnl(),
                closedPosition.realizedPnlPercent(),
                closedPosition.warning(),
                closedPosition.commissions(),
                source,
                manualPositionId,
                note,
                closedPosition.remainingQuantity(),
                trades,
                investorId);
    }

    /**
     * The trades of this period out of all the owner's trades, which are already in date order. Static: it runs before
     * the record exists, as an argument of {@code this(...)}.
     */
    private static List<TradeResponse> tradesOfPeriod(List<TradeEntity> allTrades, ClosedPosition closedPosition) {
        return allTrades.stream()
                .filter(trade -> closedPosition.tradeIds().contains(trade.id()))
                .map(TradeResponse::new)
                .toList();
    }
}
