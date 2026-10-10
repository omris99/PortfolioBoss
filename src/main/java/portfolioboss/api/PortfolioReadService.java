package portfolioboss.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.api.response.HoldingResponse;
import portfolioboss.api.response.InvestorQuantityResponse;
import portfolioboss.api.response.InvestorResponse;
import portfolioboss.api.response.MomentumResponse;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.api.response.StockAnalysisResponse;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.calculation.AnalystTrendCalculator;
import portfolioboss.calculation.ConsensusCalculator;
import portfolioboss.calculation.HoldingHistory;
import portfolioboss.calculation.InvestorSummary;
import portfolioboss.calculation.InvestorSummaryCalculator;
import portfolioboss.calculation.Momentum;
import portfolioboss.calculation.PositionTrades;
import portfolioboss.calculation.SignalCalculator;
import portfolioboss.db.AccountStateEntity;
import portfolioboss.db.AccountStateRepository;
import portfolioboss.db.DailyCloseEntity;
import portfolioboss.db.DailyCloseRepository;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.InvestorEntity;
import portfolioboss.db.InvestorRepository;
import portfolioboss.db.ManualPositionEntity;
import portfolioboss.db.ManualPositionRepository;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.db.StockAnalysisRepository;
import portfolioboss.ib.Benchmark;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Holding;
import portfolioboss.model.AnalystConsensus;
import portfolioboss.model.AnalystTrend;
import portfolioboss.model.HoldingSignal;
import portfolioboss.model.MomentumLabel;
import portfolioboss.utils.Utils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Reads the portfolio the last sync stored and turns it into what the API serves: the holdings with what is derived
 * from their trades, closes and latest analysis, every investor's closed positions, and the investors' cards. The response records only
 * hold the result — the shape of the JSON —; the work of putting it together is here. The API always reads from the
 * database, never from TWS: {@code PortfolioSyncService} is the only thing that writes to it.
 */
@Service
public class PortfolioReadService {

    private final HoldingRepository holdingRepository;
    private final AccountStateRepository accountStateRepository;
    private final ManualPositionRepository manualPositionRepository;
    private final InvestorRepository investorRepository;
    private final DailyCloseRepository dailyCloseRepository;
    private final StockAnalysisRepository stockAnalysisRepository;

    public PortfolioReadService(HoldingRepository holdingRepository, AccountStateRepository accountStateRepository,
                                ManualPositionRepository manualPositionRepository,
                                InvestorRepository investorRepository, DailyCloseRepository dailyCloseRepository,
                                StockAnalysisRepository stockAnalysisRepository) {
        this.holdingRepository = holdingRepository;
        this.accountStateRepository = accountStateRepository;
        this.manualPositionRepository = manualPositionRepository;
        this.investorRepository = investorRepository;
        this.dailyCloseRepository = dailyCloseRepository;
        this.stockAnalysisRepository = stockAnalysisRepository;
    }

    /**
     * Empty until a sync has stored something. The response is built inside the transaction: with
     * {@code open-in-view=false} that is the only place where the entities can still be read.
     */
    @Transactional(readOnly = true)
    public Optional<PortfolioResponse> currentPortfolio() {
        return accountStateRepository.findFirstByOrderByAsOfDesc().map(this::toResponse);
    }

    /** {@code investors} come in id order, the account owner first — {@code InvestorRepository.findAllByOrderById}. */
    private PortfolioResponse toResponse(AccountStateEntity accountState) {
        List<HoldingEntity> storedHoldings = holdingRepository.findByAccountOrderById(accountState.account());
        List<ManualPositionEntity> manualPositions = manualPositionRepository.findAllByOrderById();
        List<InvestorEntity> investors = investorRepository.findAllByOrderById();
        Map<Integer, List<DailyClose>> dailyClosesByConId = dailyClosesOf(storedHoldings);
        Map<Long, StockAnalysisEntity> latestAnalysisByHoldingId = latestAnalysisByHoldingId();
        // How far an OPEN holding's day count runs (see HoldingHistory) — the last sync, in the local
        // calendar day, not the instant the API happens to be called.
        LocalDate snapshotDate = accountState.asOf().atZone(ZoneId.systemDefault()).toLocalDate();
        long accountOwnerId = accountOwnerOf(investors).id();
        return new PortfolioResponse(
                accountState.account(),
                accountState.asOf(),
                accountState.netLiquidation(),
                accountState.totalCashValue(),
                storedHoldings.stream()
                        .map(holding -> holdingResponseOf(holding, snapshotDate, accountOwnerId,
                                momentumOf(holding, dailyClosesByConId), latestAnalysisByHoldingId.get(holding.id())))
                        .toList(),
                closedPositionsOf(storedHoldings, manualPositions),
                investorsOf(accountState, storedHoldings, manualPositions, investors, accountOwnerId));
    }

    /** The stored closes of every holding and of SPY, by contract id — one query for all of them. */
    private Map<Integer, List<DailyClose>> dailyClosesOf(List<HoldingEntity> holdings) {
        Set<Integer> conIds = new HashSet<>();
        holdings.forEach(holding -> conIds.add(holding.conId()));
        conIds.add(Benchmark.SPY_CON_ID);
        return dailyCloseRepository.findByConIdInOrderByConIdAscBarDateAsc(conIds).stream()
                .collect(Collectors.groupingBy(DailyCloseEntity::conId,
                        Collectors.mapping(DailyCloseEntity::toDailyClose, Collectors.toList())));
    }

    /** The latest analysis of every holding that has one, by holding id — one query for all of them. */
    private Map<Long, StockAnalysisEntity> latestAnalysisByHoldingId() {
        return stockAnalysisRepository.findLatestOfEveryHolding().stream()
                .collect(Collectors.toMap(StockAnalysisEntity::holdingId, analysis -> analysis));
    }

    private InvestorEntity accountOwnerOf(List<InvestorEntity> investors) {
        return investors.stream()
                .filter(InvestorEntity::isAccountOwner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No account owner: V4__investors.sql creates one"));
    }

    // ── the holdings ────────────────────────────────────────────────────────────────────────────

    /**
     * {@code snapshotDate} is how far a still-{@code OPEN} holding's day count runs — see {@link HoldingHistory};
     * {@code accountOwnerId} is who holds the shares the other investors' trades don't explain; {@code momentum} and
     * {@code latestAnalysis} are {@code null} while there are no closes, or no analysis, for the holding.
     */
    private HoldingResponse holdingResponseOf(HoldingEntity holdingEntity, LocalDate snapshotDate, long accountOwnerId,
                                              Momentum momentum, StockAnalysisEntity latestAnalysis) {
        Holding holding = holdingEntity.toIbHolding();
        HoldingHistory holdingHistory = holdingEntity.tradeHistory();
        MomentumResponse momentumResponse = momentum == null ? null : new MomentumResponse(momentum);

        return new HoldingResponse(
                holding.symbol(),
                holding.secType(),
                holding.currency(),
                Utils.finiteOrNull(holding.position()),
                Utils.finiteOrNull(holding.averageCost()),
                Utils.finiteOrNull(holding.marketPrice()),
                Utils.finiteOrNull(holding.marketValue()),
                Utils.finiteOrNull(holding.unrealizedPnl()),
                Utils.finiteOrNull(holding.realizedPnl()),
                holding.account(),
                Utils.finiteOrNull(holding.costBasis()),
                Utils.finiteOrNull(holding.unrealizedPnlPercent()),
                holdingEntity.id(),
                holdingEntity.conId(),
                holdingEntity.sector(),
                holdingEntity.status(),
                holdingHistory.firstBuyDate(),
                holdingHistory.lastSellDate(),
                holdingHistory.holdingDays(holdingEntity.status(), snapshotDate),
                holdingEntity.trades().stream().map(TradeResponse::new).toList(),
                holdingHistory.warnings(holdingEntity.status(), holding.position()),
                investorQuantitiesOf(holdingEntity, accountOwnerId),
                momentumResponse,
                analysisResponseOf(latestAnalysis, holding),
                signalOf(latestAnalysis, momentum));
    }

    /**
     * The analysis as stored, with the consensus and the analysts' trend worked out from it on this read — the target
     * measured against IB's latest price — so that a change of rule applies to old analyses too.
     */
    private StockAnalysisResponse analysisResponseOf(StockAnalysisEntity latestAnalysis, Holding holding) {
        if (latestAnalysis == null) {
            return null;
        }
        StockAnalysisResult result = latestAnalysis.result();
        AnalystConsensus consensus =
                new ConsensusCalculator(result.consensusBySource(), Utils.finiteOrNull(holding.marketPrice())).consensus();
        return new StockAnalysisResponse(latestAnalysis, consensus, new AnalystTrendCalculator(result.recentActions()));
    }

    /** The dot needs two of its three signs, so without an analysis — the momentum alone — there is none. */
    private HoldingSignal signalOf(StockAnalysisEntity latestAnalysis, Momentum momentum) {
        if (latestAnalysis == null) {
            return null;
        }
        MomentumLabel momentumLabel = momentum == null ? null : momentum.label();
        StockAnalysisResult result = latestAnalysis.result();
        AnalystTrend analystTrend = new AnalystTrendCalculator(result.recentActions()).trend();
        return new SignalCalculator(momentumLabel, analystTrend, result.sentiment()).signal();
    }

    /** Derived on every read from the stored closes, never stored itself — like {@code firstBuyDate}. */
    private Momentum momentumOf(HoldingEntity holding, Map<Integer, List<DailyClose>> dailyClosesByConId) {
        return Momentum.of(dailyClosesByConId.getOrDefault(holding.conId(), List.of()),
                dailyClosesByConId.getOrDefault(Benchmark.SPY_CON_ID, List.of()));
    }

    private List<InvestorQuantityResponse> investorQuantitiesOf(HoldingEntity holdingEntity, long accountOwnerId) {
        return holdingEntity.toPositionTrades().partsByInvestor(accountOwnerId).entrySet().stream()
                .map(investorPart -> new InvestorQuantityResponse(investorPart.getKey(), investorPart.getValue()))
                .toList();
    }

    // ── the closed positions ────────────────────────────────────────────────────────────────────

    /** The holdings' first, then the manual positions'; the UI sorts them by date itself. */
    private List<ClosedPositionResponse> closedPositionsOf(List<HoldingEntity> holdings,
                                                           List<ManualPositionEntity> manualPositions) {
        return Stream.concat(holdings.stream().flatMap(this::holdingClosedPositionsOf),
                manualPositions.stream().flatMap(this::manualClosedPositionsOf)).toList();
    }

    /**
     * A holding's closed positions, investor by investor (INVESTORS_TODO.md, decision 8) — of open and closed holdings
     * alike: a holding still open today can have been sold in full and bought again before, or sold in part.
     */
    private Stream<ClosedPositionResponse> holdingClosedPositionsOf(HoldingEntity holding) {
        PositionTrades positionTrades = holding.toPositionTrades();
        return positionTrades.investorIds().stream()
                .flatMap(investorId -> positionTrades.historyOf(investorId).closedPositions().stream()
                        .map(closedPosition -> new ClosedPositionResponse(holding, closedPosition, investorId)));
    }

    private Stream<ClosedPositionResponse> manualClosedPositionsOf(ManualPositionEntity manualPosition) {
        PositionTrades positionTrades = manualPosition.toPositionTrades();
        return positionTrades.investorIds().stream()
                .flatMap(investorId -> positionTrades.historyOf(investorId).closedPositions().stream()
                        .map(closedPosition -> new ClosedPositionResponse(manualPosition, closedPosition, investorId)));
    }

    // ── the investors ───────────────────────────────────────────────────────────────────────────

    /** Every investor's summary card, in the order of {@code investors}. */
    private List<InvestorResponse> investorsOf(AccountStateEntity accountState, List<HoldingEntity> holdings,
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
