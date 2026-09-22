package portfolioboss.api.response;

import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingStatus;
import portfolioboss.model.Holding;
import portfolioboss.utils.Utils;

/**
 * One holding as the UI receives it. Jackson turns the record into JSON using the component names,
 * so they are the JSON keys and must stay stable ({@code ui/src/types/portfolio.ts} mirrors them).
 * A figure IB did not report is {@code null}. It is built from the stored row; the last four fields
 * ({@code id}, {@code conId}, {@code sector}, {@code status}) were added when the API began reading from the
 * database. Fields are only ever added, never renamed or removed.
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
        HoldingStatus status) {

    static HoldingResponse from(HoldingEntity entity) {
        Holding holding = entity.toIbHolding();
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
                entity.id(),
                entity.conId(),
                entity.sector(),
                entity.status());
    }
}
