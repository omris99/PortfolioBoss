package portfolioboss.api.response;

import portfolioboss.db.AccountStateEntity;
import portfolioboss.db.HoldingEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * The body of {@code GET /api/portfolio}: the account figures and the holdings of the last sync, as stored in
 * the database. {@code asOf} is written as an ISO-8601 string ({@code "2026-09-19T08:05:00Z"}); a figure IB did
 * not report is {@code null} (the database already holds it as {@code NULL}).
 */
public record PortfolioResponse(
        String account,
        Instant asOf,
        Double netLiquidation,
        Double totalCashValue,
        List<HoldingResponse> holdings) {

    public static PortfolioResponse from(AccountStateEntity accountState, List<HoldingEntity> holdings) {
        // How far an OPEN holding's day count runs (see HoldingHistory) — the last sync, in the local
        // calendar day, not the instant the API happens to be called.
        LocalDate snapshotDate = accountState.asOf().atZone(ZoneId.systemDefault()).toLocalDate();
        return new PortfolioResponse(
                accountState.account(),
                accountState.asOf(),
                accountState.netLiquidation(),
                accountState.totalCashValue(),
                holdings.stream().map(holding -> HoldingResponse.from(holding, snapshotDate)).toList());
    }
}
