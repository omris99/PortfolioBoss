package portfolioboss.api.response;

import portfolioboss.db.AccountStateEntity;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.ManualPositionEntity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.Stream;

/**
 * The body of {@code GET /api/portfolio}: the account figures and the holdings of the last sync, as stored in
 * the database. {@code asOf} is written as an ISO-8601 string ({@code "2026-09-19T08:05:00Z"}); a figure IB did
 * not report is {@code null} (the database already holds it as {@code NULL}). {@code closedPositions} was added
 * after the others, and like them is never renamed or removed; it holds the manual positions' closed positions as well.
 */
public record PortfolioResponse(
        String account,
        Instant asOf,
        Double netLiquidation,
        Double totalCashValue,
        List<HoldingResponse> holdings,
        List<ClosedPositionResponse> closedPositions) {

    public static PortfolioResponse from(AccountStateEntity accountState, List<HoldingEntity> holdings,
                                         List<ManualPositionEntity> manualPositions) {
        // How far an OPEN holding's day count runs (see HoldingHistory) — the last sync, in the local
        // calendar day, not the instant the API happens to be called.
        LocalDate snapshotDate = accountState.asOf().atZone(ZoneId.systemDefault()).toLocalDate();
        return new PortfolioResponse(
                accountState.account(),
                accountState.asOf(),
                accountState.netLiquidation(),
                accountState.totalCashValue(),
                holdings.stream().map(holding -> HoldingResponse.from(holding, snapshotDate)).toList(),
                closedPositionsOf(holdings, manualPositions));
    }

    /** The holdings' first, then the manual positions'; the UI sorts them by date itself. */
    private static List<ClosedPositionResponse> closedPositionsOf(List<HoldingEntity> holdings,
                                                                  List<ManualPositionEntity> manualPositions) {
        return Stream.concat(holdingClosedPositionsOf(holdings), manualClosedPositionsOf(manualPositions)).toList();
    }

    /**
     * Every holding's closed positions, open and closed holdings alike: a holding still open today can have been sold
     * in full and bought again before, or sold in part.
     */
    private static Stream<ClosedPositionResponse> holdingClosedPositionsOf(List<HoldingEntity> holdings) {
        return holdings.stream()
                .flatMap(holding -> holding.tradeHistory().closedPositions().stream()
                        .map(closedPosition -> new ClosedPositionResponse(holding, closedPosition)));
    }

    private static Stream<ClosedPositionResponse> manualClosedPositionsOf(List<ManualPositionEntity> manualPositions) {
        return manualPositions.stream()
                .flatMap(manualPosition -> manualPosition.tradeHistory().closedPositions().stream()
                        .map(closedPosition -> new ClosedPositionResponse(manualPosition, closedPosition)));
    }
}
