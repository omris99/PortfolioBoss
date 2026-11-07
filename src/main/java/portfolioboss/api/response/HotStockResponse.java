package portfolioboss.api.response;

import portfolioboss.model.HotStockLabel;
import portfolioboss.model.NotPickableReason;
import portfolioboss.model.WeighedAnalystMove;
import portfolioboss.model.WeighedNewsEvent;

import java.util.List;

/**
 * One stock of a Recommendations list (RECOMMENDATIONS_TODO.md, session 1): what the scan stored about it, its momentum
 * from the stored closes — the price is {@code momentum.lastClose} —, what the rule of decision 1 says about it, and
 * (session 3) its latest analysis with Finnhub's answer and the risk signs worked out from them on this read. The
 * component names are JSON keys ({@code ui/src/types/recommendations.ts} mirrors them): one is renamed or removed only
 * together with the UI's types and every reader of it, in the same change — as Finnhub's four fields were, into
 * {@code companyInfo} (MY_HOLDINGS_TODO.md, session 3): the same object a holding has.
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
 * @param companyInfo         what Finnhub answered with that analysis, with its risk signs (decision 5) and the timing
 *                            note counted from the latest scan's day; {@code null} while never analyzed, or when Finnhub
 *                            didn't answer with the analysis
 * @param newsScore          its news events and the analysts' moves weighed (decision 13), counted from the day of
 *                            the analysis — as the recommendation weighed them; {@code null} while never analyzed
 * @param weighedNewsEvents   every event of that analysis with what it weighs in {@code newsScore}; empty while never
 *                            analyzed
 * @param countedAnalystMoves the analysts' upgrades and downgrades that count in {@code newsScore}, with what each
 *                            weighs; empty while never analyzed
 * @param riskWeight          its risk signs' weights added up; {@code null} when the signs were never checked
 * @param score               the news score less {@code riskWeight} — what the picks are ordered by; {@code null} when
 *                            the signs were never checked
 * @param canBePicked         no reason against it ({@code reasonsNotPickable} empty): the recommendation could pick it if
 *                            its score is among the highest
 * @param analystTarget       the analysts' target from its Dow Jones headlines (decision 14), against the last close —
 *                            known before any analysis
 * @param inShortList         among the first 10 HOT stocks with enough upside: the full run analyzes it
 * @param reasonsNotPickable  why the recommendation can't pick it, in the order the checks run; empty when it can
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
        CompanyInfoResponse companyInfo,
        Integer newsScore,
        List<WeighedNewsEvent> weighedNewsEvents,
        List<WeighedAnalystMove> countedAnalystMoves,
        Integer riskWeight,
        Integer score,
        boolean canBePicked,
        AnalystTargetResponse analystTarget,
        boolean inShortList,
        List<NotPickableReason> reasonsNotPickable) {
}
