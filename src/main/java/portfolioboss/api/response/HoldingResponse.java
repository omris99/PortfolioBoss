package portfolioboss.api.response;

import portfolioboss.model.HoldingSignal;
import portfolioboss.model.HoldingStatus;
import portfolioboss.model.HoldingWarning;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;

import java.time.LocalDate;
import java.util.List;

/**
 * One holding as the UI receives it. Jackson turns the record into JSON using the component names,
 * so they are the JSON keys and must stay stable ({@code ui/src/types/portfolio.ts} mirrors them).
 * A figure IB did not report is {@code null}. {@code PortfolioReadService} builds it from the stored row; `id`,
 * `conId`, `sector` and `status` were added when the API began reading from the database, `firstBuyDate`,
 * `lastSellDate`, `holdingDays` and `trades` when those were derived from the `trade` table,
 * `warnings` when the trades entered began to be checked against IB, `investorQuantities` when the account
 * got several investors, `momentum` with the daily closes, `analysis` and `signal` with the stock analysis, and the last
 * five with what a scanned stock has (MY_HOLDINGS_TODO.md, session 2). Fields are only ever added, never renamed or
 * removed.
 *
 * @param sector              IB's broad word, from the holding's contract details (MY_HOLDINGS_TODO.md, decision 11):
 *                            "Technology" — what IB, and a scanned stock's {@code industry}, call industry
 * @param investorQuantities  how {@code position} — and its value, cost and unrealized P&amp;L — divides between the
 *                            investors; only those holding some of it
 * @param momentum            from the stored daily closes; {@code null} while there are none for this holding
 * @param analysis            the latest stock analysis; {@code null} while the holding has never been analyzed
 * @param signal              the colored dot (AI_ANALYSIS_TODO.md, decision 6); {@code null} without an analysis, or
 *                            while fewer than two of its three signs are known. The five fields below don't change it
 * @param analystTarget       the analysts' target from the holding's Dow Jones headlines, against IB's latest price and
 *                            counted back from the last sync's day
 * @param nextEarningsDate    Finnhub's; {@code null} without Finnhub's answer, or without a report in the next four
 *                            months
 * @param companyFacts        Finnhub's four figures; {@code null} without Finnhub's answer
 * @param riskSigns           the risk signs (RECOMMENDATIONS_TODO.md, decision 5); {@code null} without Finnhub's
 *                            figures — then they were never checked, which an empty list would hide
 * @param reportsWithin14Days the timing note: the next report is due 0 to 14 days after the last sync
 * @param industry            IB's finer word, "Semiconductors" — what IB, and a scanned stock's {@code category}, call
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
        EarningsDate nextEarningsDate,
        CompanyFacts companyFacts,
        List<RiskSign> riskSigns,
        boolean reportsWithin14Days,
        String industry) {
}
