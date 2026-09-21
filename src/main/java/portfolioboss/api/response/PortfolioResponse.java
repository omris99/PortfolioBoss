package portfolioboss.api.response;

import portfolioboss.model.PortfolioSnapshot;

import java.time.Instant;
import java.util.List;

/**
 * The body of {@code GET /api/portfolio}. {@code asOf} is written as an ISO-8601 string
 * ({@code "2026-09-19T08:05:00Z"}); a figure IB did not report is {@code null}.
 */
public record PortfolioResponse(
        String account,
        Instant asOf,
        Double netLiquidation,
        Double totalCashValue,
        List<HoldingResponse> holdings) {

    public static PortfolioResponse from(PortfolioSnapshot snapshot) {
        List<HoldingResponse> holdings = snapshot.holdings().stream()
                .map(HoldingResponse::from)
                .toList();
        return new PortfolioResponse(
                snapshot.account(),
                snapshot.asOf(),
                JsonNumbers.finiteOrNull(snapshot.netLiquidation()),
                JsonNumbers.finiteOrNull(snapshot.totalCashValue()),
                holdings);
    }
}
