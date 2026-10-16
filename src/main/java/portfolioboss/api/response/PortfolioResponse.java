package portfolioboss.api.response;

import java.time.Instant;
import java.util.List;

/**
 * The body of {@code GET /api/portfolio}: the account figures and the holdings of the last sync, as stored in
 * the database. {@code asOf} is written as an ISO-8601 string ({@code "2026-09-19T08:05:00Z"}); a figure IB did
 * not report is {@code null} (the database already holds it as {@code NULL}). {@code closedPositions},
 * {@code investors} and {@code estimatedAnalysisCost} were added after the others, and like them are never renamed or
 * removed; {@code closedPositions} holds the manual positions' closed positions as well. Put together by
 * {@code PortfolioReadService}.
 *
 * @param investors             every investor's summary card, the account owner first
 * @param estimatedAnalysisCost what analyzing one stock is expected to cost; {@code null} while nothing has been
 *                              analyzed
 */
public record PortfolioResponse(
        String account,
        Instant asOf,
        Double netLiquidation,
        Double totalCashValue,
        List<HoldingResponse> holdings,
        List<ClosedPositionResponse> closedPositions,
        List<InvestorResponse> investors,
        EstimatedAnalysisCostResponse estimatedAnalysisCost) {
}
