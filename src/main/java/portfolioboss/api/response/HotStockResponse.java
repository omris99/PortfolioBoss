package portfolioboss.api.response;

import portfolioboss.model.HotStockLabel;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;

import java.util.List;

/**
 * One stock of a Recommendations list (RECOMMENDATIONS_TODO.md, session 1): what the scan stored about it, its momentum
 * from the stored closes — the price is {@code momentum.lastClose} —, what the rule of decision 1 says about it, and
 * (session 3) its latest analysis with Finnhub's answer and the risk signs worked out from them on this read. Finnhub's
 * records go into the JSON as they are. The component names are JSON keys: added to, never renamed or removed.
 *
 * @param candidateId       the {@code scan_candidate} row
 * @param industry          IB's sector, e.g. "Technology"; {@code null} when IB sent none
 * @param category          within it, e.g. "Software"
 * @param percentAboveSma50 how far the last close is above the 50-day average — below 0 in {@code CORRECTING}
 * @param pulledBack        within {@code HOT} only: 5–15% below the year's highest close
 * @param recovering        lower than a year ago (or than its first close); {@code null} when unknown
 * @param correlations      the open holdings it moves with most, highest first — at most three
 * @param heldByInvestorIds the investors who hold some of it, when it is an open holding already; empty otherwise
 * @param analysis            its latest analysis, from any scan (decision 12), the consensus measured against the last
 *                            close; {@code null} while it has never been analyzed
 * @param nextEarningsDate    Finnhub's, from that analysis; {@code null} without one, or without a report in the next
 *                            four months
 * @param companyFacts        Finnhub's four figures, from that analysis; {@code null} without Finnhub's answer
 * @param riskSigns           its risk signs (decision 5); {@code null} without Finnhub's figures — then they were never
 *                            checked
 * @param reportsWithin14Days the timing note: its report is due 0 to 14 days after the latest scan
 * @param newsScore           its news events and the analysts' moves weighed (decision 13), counted from the day of
 *                            the analysis — as the recommendation weighed them; {@code null} while never analyzed
 */
public record HotStockResponse(
        long candidateId,
        int conId,
        String symbol,
        String companyName,
        String exchange,
        String industry,
        String category,
        HotStockLabel label,
        MomentumResponse momentum,
        double percentAboveSma50,
        boolean pulledBack,
        Boolean recovering,
        List<CorrelationResponse> correlations,
        List<Long> heldByInvestorIds,
        StockAnalysisResponse analysis,
        EarningsDate nextEarningsDate,
        CompanyFacts companyFacts,
        List<RiskSign> riskSigns,
        boolean reportsWithin14Days,
        Integer newsScore) {
}
