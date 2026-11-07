package portfolioboss.api.response;

import portfolioboss.model.HoldingSignal;
import portfolioboss.model.HoldingStatus;
import portfolioboss.model.HoldingWarning;

import java.time.LocalDate;
import java.util.List;

/**
 * One holding as the UI receives it. Jackson turns the record into JSON using the component names,
 * so they are the JSON keys ({@code ui/src/types/portfolio.ts} mirrors them): one is renamed or removed only together
 * with the UI's types and every reader of it, in the same change.
 * A figure IB did not report is {@code null}. {@code PortfolioReadService} builds it from the stored row; `id`,
 * `conId`, `sector` and `status` were added when the API began reading from the database, `firstBuyDate`,
 * `lastSellDate`, `holdingDays` and `trades` when those were derived from the `trade` table,
 * `warnings` when the trades entered began to be checked against IB, `investorQuantities` when the account
 * got several investors, `momentum` with the daily closes, `analysis` and `signal` with the stock analysis, and the last
 * three with what a scanned stock has (MY_HOLDINGS_TODO.md, session 2; in session 3 Finnhub's four fields moved into
 * `companyInfo`, which a scanned stock has too).
 *
 * @param sector              IB's broad word, from the holding's contract details (MY_HOLDINGS_TODO.md, decision 11):
 *                            "Technology" — what IB, and a scanned stock's {@code industry}, call industry
 * @param investorQuantities  how {@code position} — and its value, cost and unrealized P&amp;L — divides between the
 *                            investors; only those holding some of it
 * @param momentum            from the stored daily closes; {@code null} while there are none for this holding
 * @param analysis            the latest stock analysis; {@code null} while the holding has never been analyzed
 * @param signal              the colored dot (AI_ANALYSIS_TODO.md, decision 6); {@code null} without an analysis, or
 *                            while fewer than two of its three signs are known. The two fields below don't change it
 * @param analystTarget       the analysts' target from the holding's Dow Jones headlines, against IB's latest price and
 *                            counted back from the last sync's day
 * @param companyInfo         what Finnhub last answered about the holding's company, with its risk signs and the
 *                            timing note counted from the last sync's day; {@code null} while Finnhub never answered —
 *                            it is asked about the holdings in dollars only (MY_HOLDINGS_TODO.md, decision 10)
 * @param industry           IB's finer word, "Semiconductors" — what IB, and a scanned stock's {@code category}, call
 *                            category; {@code null} until a sync reads the contract details, and when IB gives none
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
        MomentumResponse momentum,
        StockAnalysisResponse analysis,
        HoldingSignal signal,
        AnalystTargetResponse analystTarget,
        CompanyInfoResponse companyInfo,
        String industry) {
}
