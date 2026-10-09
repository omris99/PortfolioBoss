package portfolioboss.api.response;

import portfolioboss.calculation.HoldingStatus;
import portfolioboss.calculation.HoldingWarning;

import java.time.LocalDate;
import java.util.List;

/**
 * One holding as the UI receives it. Jackson turns the record into JSON using the component names,
 * so they are the JSON keys and must stay stable ({@code ui/src/types/portfolio.ts} mirrors them).
 * A figure IB did not report is {@code null}. {@code PortfolioReadService} builds it from the stored row; `id`,
 * `conId`, `sector` and `status` were added when the API began reading from the database, `firstBuyDate`,
 * `lastSellDate`, `holdingDays` and `trades` when those were derived from the `trade` table,
 * `warnings` when the trades entered began to be checked against IB, `investorQuantities` when the account
 * got several investors, and `momentum` with the daily closes. Fields are only ever added, never renamed or removed.
 *
 * @param investorQuantities how {@code position} — and its value, cost and unrealized P&amp;L — divides between the
 *                           investors; only those holding some of it
 * @param momentum           from the stored daily closes; {@code null} while there are none for this holding
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
        List<InvestorQuantityResponse> investorQuantities,
        MomentumResponse momentum) {
}
