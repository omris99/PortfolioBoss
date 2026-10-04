package portfolioboss.api.response;

import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingStatus;
import portfolioboss.domain.HoldingHistory;
import portfolioboss.domain.HoldingWarning;
import portfolioboss.model.Holding;
import portfolioboss.utils.Utils;

import java.time.LocalDate;
import java.util.List;

/**
 * One holding as the UI receives it. Jackson turns the record into JSON using the component names,
 * so they are the JSON keys and must stay stable ({@code ui/src/types/portfolio.ts} mirrors them).
 * A figure IB did not report is {@code null}. It is built from the stored row; `id`, `conId`, `sector`
 * and `status` were added when the API began reading from the database, `firstBuyDate`,
 * `lastSellDate`, `holdingDays` and `trades` when those were derived from the `trade` table,
 * `warnings` when the trades entered began to be checked against IB, and `investorQuantities` when the account
 * got several investors. Fields are only ever added, never renamed or removed.
 *
 * @param investorQuantities how {@code position} divides between the investors — only those holding some of it
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
        List<TradeResponse> trades,
        List<HoldingWarning> warnings,
        List<InvestorQuantityResponse> investorQuantities) {

    /**
     * {@code snapshotDate} is how far an still-{@code OPEN} holding's day count runs — see {@link HoldingHistory};
     * {@code accountOwnerId} is who holds the shares the other investors' trades don't explain.
     */
    protected static HoldingResponse from(HoldingEntity holdingEntity, LocalDate snapshotDate, long accountOwnerId) {
        Holding holding = holdingEntity.toIbHolding();
        HoldingHistory holdingHistory = holdingEntity.tradeHistory();

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
                holdingEntity.trades().stream().map(TradeResponse::new).toList(),
                holdingHistory.warnings(holdingEntity.status(), holding.position()),
                investorQuantitiesOf(holdingEntity, accountOwnerId));
    }

    /** Static: it runs before the record exists, as an argument of the constructor. */
    private static List<InvestorQuantityResponse> investorQuantitiesOf(HoldingEntity holdingEntity,
                                                                       long accountOwnerId) {
        return holdingEntity.toPositionTrades().quantitiesByInvestor(accountOwnerId).entrySet().stream()
                .map(investorQuantity -> new InvestorQuantityResponse(investorQuantity.getKey(),
                        investorQuantity.getValue()))
                .toList();
    }
}
