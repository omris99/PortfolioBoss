package portfolioboss.api.response;

import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeEntity;
import portfolioboss.domain.HoldingHistory;
import portfolioboss.domain.TradeFact;
import portfolioboss.model.Holding;
import portfolioboss.utils.Utils;

import java.time.LocalDate;
import java.util.List;

/**
 * One holding as the UI receives it. Jackson turns the record into JSON using the component names,
 * so they are the JSON keys and must stay stable ({@code ui/src/types/portfolio.ts} mirrors them).
 * A figure IB did not report is {@code null}. It is built from the stored row; `id`, `conId`, `sector`
 * and `status` were added when the API began reading from the database, and `firstBuyDate`,
 * `lastSellDate`, `holdingDays` and `trades` when those were derived from the `trade` table. Fields are
 * only ever added, never renamed or removed.
 */
public record HoldingResponse(
        String symbol,
        String secType,
        String currency,
        Double position,
        Double averageCost,
        Double marketPrice,
        Double marketValue,
        Double unrealizedPnl,
        Double realizedPnl,
        String account,
        Double costBasis,
        Double unrealizedPnlPercent,
        long id,
        int conId,
        String sector,
        HoldingStatus status,
        LocalDate firstBuyDate,
        LocalDate lastSellDate,
        Long holdingDays,
        List<TradeResponse> trades) {

    /** {@code snapshotDate} is how far an still-{@code OPEN} holding's day count runs — see {@link HoldingHistory}. */
    protected static HoldingResponse from(HoldingEntity holdingEntity, LocalDate snapshotDate) {
        Holding holding = holdingEntity.toIbHolding();
        List<TradeEntity> tradeEntities = holdingEntity.trades();
        List<TradeFact> tradeFacts = tradeEntities.stream().map(TradeEntity::toTradeFact).toList();
        HoldingHistory holdingHistory = HoldingHistory.of(tradeFacts);

        return new HoldingResponse(
                holding.symbol(),
                holding.secType(),
                holding.currency(),
                Utils.finiteOrNull(holding.position()),
                Utils.finiteOrNull(holding.averageCost()),
                Utils.finiteOrNull(holding.marketPrice()),
                Utils.finiteOrNull(holding.marketValue()),
                Utils.finiteOrNull(holding.unrealizedPnl()),
                Utils.finiteOrNull(holding.realizedPnl()),
                holding.account(),
                Utils.finiteOrNull(holding.costBasis()),
                Utils.finiteOrNull(holding.unrealizedPnlPercent()),
                holdingEntity.id(),
                holdingEntity.conId(),
                holdingEntity.sector(),
                holdingEntity.status(),
                holdingHistory.firstBuyDate(),
                holdingHistory.lastSellDate(),
                holdingHistory.holdingDays(holdingEntity.status(), snapshotDate),
                tradeEntities.stream().map(TradeResponse::new).toList());
    }
}
