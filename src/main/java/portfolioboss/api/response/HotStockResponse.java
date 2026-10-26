package portfolioboss.api.response;

import portfolioboss.model.HotStockLabel;

import java.util.List;

/**
 * One stock of a Recommendations list (RECOMMENDATIONS_TODO.md, session 1): what the scan stored about it, its momentum
 * from the stored closes — the price is {@code momentum.lastClose} —, and what the rule of decision 1 says about it. The
 * component names are JSON keys: added to, never renamed or removed.
 *
 * @param candidateId       the {@code scan_candidate} row
 * @param industry          IB's sector, e.g. "Technology"; {@code null} when IB sent none
 * @param category          within it, e.g. "Software"
 * @param percentAboveSma50 how far the last close is above the 50-day average — below 0 in {@code CORRECTING}
 * @param pulledBack        within {@code HOT} only: 5–15% below the year's highest close
 * @param recovering        lower than a year ago (or than its first close); {@code null} when unknown
 * @param correlations      the open holdings it moves with most, highest first — at most three
 * @param heldByInvestorIds the investors who hold some of it, when it is an open holding already; empty otherwise
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
        List<Long> heldByInvestorIds) {
}
