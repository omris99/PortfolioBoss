package portfolioboss.api.response;

import portfolioboss.db.AccountStateEntity;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.ManualClosedPositionEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

/**
 * The body of {@code GET /api/portfolio}: the account figures and the holdings of the last sync, as stored in
 * the database. {@code asOf} is written as an ISO-8601 string ({@code "2026-09-19T08:05:00Z"}); a figure IB did
 * not report is {@code null} (the database already holds it as {@code NULL}). {@code closedPositions} was added
 * after the others, and like them is never renamed or removed; it holds the closed positions entered by hand as well.
 */
public record PortfolioResponse(
        String account,
        Instant asOf,
        Double netLiquidation,
        Double totalCashValue,
        List<HoldingResponse> holdings,
        List<ClosedPositionResponse> closedPositions) {

    public static PortfolioResponse from(AccountStateEntity accountState, List<HoldingEntity> holdings,
                                         List<ManualClosedPositionEntity> manualClosedPositions) {
        // How far an OPEN holding's day count runs (see HoldingHistory) — the last sync, in the local
        // calendar day, not the instant the API happens to be called.
        LocalDate snapshotDate = accountState.asOf().atZone(ZoneId.systemDefault()).toLocalDate();
        return new PortfolioResponse(
                accountState.account(),
                accountState.asOf(),
                accountState.netLiquidation(),
                accountState.totalCashValue(),
                holdings.stream().map(holding -> HoldingResponse.from(holding, snapshotDate)).toList(),
                closedPositionsOf(holdings, manualClosedPositions));
    }

    /** The ones derived from trades first, then the ones entered by hand; the UI sorts them by date itself. */
    private static List<ClosedPositionResponse> closedPositionsOf(List<HoldingEntity> holdings,
                                                                  List<ManualClosedPositionEntity> manualClosedPositions) {
        Stream<ClosedPositionResponse> enteredByHand = manualClosedPositions.stream().map(ClosedPositionResponse::new);
        return Stream.concat(derivedClosedPositionsOf(holdings), enteredByHand).toList();
    }

    /**
     * Every holding's closed positions, open and closed holdings alike: a holding still open today can have been sold
     * in full and bought again before.
     */
    private static Stream<ClosedPositionResponse> derivedClosedPositionsOf(List<HoldingEntity> holdings) {
        return holdings.stream()
                .flatMap(holding -> holding.tradeHistory().closedPositions().stream()
                        .map(closedPosition -> new ClosedPositionResponse(holding, closedPosition)));
    }
}
