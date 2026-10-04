package portfolioboss.api.response;

import portfolioboss.db.AccountStateEntity;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.InvestorEntity;
import portfolioboss.db.ManualPositionEntity;
import portfolioboss.domain.InvestorSummary;
import portfolioboss.domain.InvestorSummaryCalculator;
import portfolioboss.domain.PositionTrades;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * The body of {@code GET /api/portfolio}: the account figures and the holdings of the last sync, as stored in
 * the database. {@code asOf} is written as an ISO-8601 string ({@code "2026-09-19T08:05:00Z"}); a figure IB did
 * not report is {@code null} (the database already holds it as {@code NULL}). {@code closedPositions} and
 * {@code investors} were added after the others, and like them are never renamed or removed; {@code closedPositions}
 * holds the manual positions' closed positions as well.
 *
 * @param investors every investor's summary card, the account owner first
 */
public record PortfolioResponse(
        String account,
        Instant asOf,
        Double netLiquidation,
        Double totalCashValue,
        List<HoldingResponse> holdings,
        List<ClosedPositionResponse> closedPositions,
        List<InvestorResponse> investors) {

    /** {@code investors} in id order, the account owner first — {@code InvestorRepository.findAllByOrderById}. */
    public static PortfolioResponse from(AccountStateEntity accountState, List<HoldingEntity> holdings,
                                         List<ManualPositionEntity> manualPositions, List<InvestorEntity> investors) {
        // How far an OPEN holding's day count runs (see HoldingHistory) — the last sync, in the local
        // calendar day, not the instant the API happens to be called.
        LocalDate snapshotDate = accountState.asOf().atZone(ZoneId.systemDefault()).toLocalDate();
        long accountOwnerId = accountOwnerOf(investors).id();
        return new PortfolioResponse(
                accountState.account(),
                accountState.asOf(),
                accountState.netLiquidation(),
                accountState.totalCashValue(),
                holdings.stream().map(holding -> HoldingResponse.from(holding, snapshotDate, accountOwnerId)).toList(),
                closedPositionsOf(holdings, manualPositions),
                investorsOf(accountState, holdings, manualPositions, investors, accountOwnerId));
    }

    private static InvestorEntity accountOwnerOf(List<InvestorEntity> investors) {
        return investors.stream()
                .filter(InvestorEntity::isAccountOwner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No account owner: V4__investors.sql creates one"));
    }

    /** The holdings' first, then the manual positions'; the UI sorts them by date itself. */
    private static List<ClosedPositionResponse> closedPositionsOf(List<HoldingEntity> holdings,
                                                                  List<ManualPositionEntity> manualPositions) {
        return Stream.concat(holdings.stream().flatMap(PortfolioResponse::holdingClosedPositionsOf),
                manualPositions.stream().flatMap(PortfolioResponse::manualClosedPositionsOf)).toList();
    }

    /**
     * A holding's closed positions, investor by investor (INVESTORS_TODO.md, decision 8) — of open and closed holdings
     * alike: a holding still open today can have been sold in full and bought again before, or sold in part.
     */
    private static Stream<ClosedPositionResponse> holdingClosedPositionsOf(HoldingEntity holding) {
        PositionTrades positionTrades = holding.toPositionTrades();
        return positionTrades.investorIds().stream()
                .flatMap(investorId -> positionTrades.historyOf(investorId).closedPositions().stream()
                        .map(closedPosition -> new ClosedPositionResponse(holding, closedPosition, investorId)));
    }

    private static Stream<ClosedPositionResponse> manualClosedPositionsOf(ManualPositionEntity manualPosition) {
        PositionTrades positionTrades = manualPosition.toPositionTrades();
        return positionTrades.investorIds().stream()
                .flatMap(investorId -> positionTrades.historyOf(investorId).closedPositions().stream()
                        .map(closedPosition -> new ClosedPositionResponse(manualPosition, closedPosition, investorId)));
    }

    /** Every investor's summary card, in the order of {@code investors}. */
    private static List<InvestorResponse> investorsOf(AccountStateEntity accountState, List<HoldingEntity> holdings,
                                                      List<ManualPositionEntity> manualPositions,
                                                      List<InvestorEntity> investors, long accountOwnerId) {
        List<PositionTrades> positions = Stream.concat(
                holdings.stream().map(HoldingEntity::toPositionTrades),
                manualPositions.stream().map(ManualPositionEntity::toPositionTrades)).toList();
        InvestorSummaryCalculator calculator = new InvestorSummaryCalculator(
                accountOwnerId,
                investors.stream().map(InvestorEntity::id).toList(),
                positions,
                investors.stream().flatMap(investor -> investor.cashMovementFacts().stream()).toList(),
                accountState.totalCashValue(),
                accountState.netLiquidation());
        Map<Long, InvestorSummary> summariesByInvestor = calculator.summariesByInvestor();
        return investors.stream()
                .map(investor -> new InvestorResponse(investor, summariesByInvestor.get(investor.id())))
                .toList();
    }
}
