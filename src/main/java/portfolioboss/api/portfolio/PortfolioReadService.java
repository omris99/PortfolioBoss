package portfolioboss.api.portfolio;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.api.analysis.response.EstimatedAnalysisCostResponse;
import portfolioboss.api.analysis.response.StockAnalysisResponse;
import portfolioboss.api.investors.response.InvestorQuantityResponse;
import portfolioboss.api.investors.response.InvestorResponse;
import portfolioboss.api.marketdata.response.AnalystTargetResponse;
import portfolioboss.api.marketdata.response.CompanyInfoResponse;
import portfolioboss.api.marketdata.response.MomentumResponse;
import portfolioboss.api.portfolio.response.ClosedPositionResponse;
import portfolioboss.api.portfolio.response.HoldingResponse;
import portfolioboss.api.portfolio.response.PortfolioResponse;
import portfolioboss.api.portfolio.response.TradeResponse;
import portfolioboss.calculation.analysis.AnalystTrendCalculator;
import portfolioboss.calculation.analysis.ConsensusCalculator;
import portfolioboss.calculation.analysis.SignalCalculator;
import portfolioboss.calculation.investors.InvestorSummary;
import portfolioboss.calculation.investors.InvestorSummaryCalculator;
import portfolioboss.calculation.investors.PositionTrades;
import portfolioboss.calculation.marketdata.AnalystTargetCalculator;
import portfolioboss.calculation.marketdata.DailyClosesWithSpy;
import portfolioboss.calculation.marketdata.Momentum;
import portfolioboss.calculation.marketdata.RiskSignsCalculator;
import portfolioboss.calculation.portfolio.HoldingHistory;
import portfolioboss.db.analysis.StockAnalysisEntity;
import portfolioboss.db.analysis.StockAnalysisRepository;
import portfolioboss.db.investors.InvestorEntity;
import portfolioboss.db.investors.InvestorRepository;
import portfolioboss.db.marketdata.AnalystPriceTargetRepository;
import portfolioboss.db.marketdata.DailyCloseRepository;
import portfolioboss.db.marketdata.StockEntity;
import portfolioboss.db.marketdata.StockRepository;
import portfolioboss.db.portfolio.AccountStateEntity;
import portfolioboss.db.portfolio.AccountStateRepository;
import portfolioboss.db.portfolio.HoldingEntity;
import portfolioboss.db.portfolio.HoldingRepository;
import portfolioboss.db.portfolio.ManualPositionEntity;
import portfolioboss.db.portfolio.ManualPositionRepository;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.Holding;
import portfolioboss.model.analysis.AnalystConsensus;
import portfolioboss.model.analysis.AnalystTrend;
import portfolioboss.model.analysis.HoldingSignal;
import portfolioboss.model.marketdata.MomentumLabel;
import portfolioboss.utils.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private final StockRepository stockRepository;
    private final AnalystPriceTargetRepository analystPriceTargetRepository;

    public PortfolioReadService(HoldingRepository holdingRepository, AccountStateRepository accountStateRepository,
                                ManualPositionRepository manualPositionRepository,
                                InvestorRepository investorRepository, DailyCloseRepository dailyCloseRepository,
                                StockAnalysisRepository stockAnalysisRepository, StockRepository stockRepository,
                                AnalystPriceTargetRepository analystPriceTargetRepository) {
        this.holdingRepository = holdingRepository;
        this.accountStateRepository = accountStateRepository;
        this.manualPositionRepository = manualPositionRepository;
        this.investorRepository = investorRepository;
        this.dailyCloseRepository = dailyCloseRepository;
        this.stockAnalysisRepository = stockAnalysisRepository;
        this.stockRepository = stockRepository;
        this.analystPriceTargetRepository = analystPriceTargetRepository;
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
        List<Integer> holdingConIds = storedHoldings.stream().map(HoldingEntity::conId).toList();
        DailyClosesWithSpy dailyCloses = dailyCloseRepository.dailyClosesWithSpy(holdingConIds);
        Map<Long, StockAnalysisEntity> latestAnalysisByHoldingId = latestAnalysisByHoldingId();
        Map<Integer, StockEntity> stocksByConId = stockRepository.stocksByConId(holdingConIds);
        Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsByConId =
                analystPriceTargetRepository.analystPriceTargetsByConId(holdingConIds);
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
                                dailyCloses.momentumOf(holding.conId()), latestAnalysisByHoldingId.get(holding.id()),
                                stocksByConId.get(holding.conId()),
                                analystPriceTargetsByConId.getOrDefault(holding.conId(), List.of())))
                        .toList(),
                closedPositionsOf(storedHoldings, manualPositions),
                investorsOf(accountState, storedHoldings, manualPositions, investors, accountOwnerId),
                estimatedAnalysisCostOf(latestAnalysisByHoldingId.values()));
    }

    /** The latest analysis of every holding that has one, by holding id — one query for all of them. */
    private Map<Long, StockAnalysisEntity> latestAnalysisByHoldingId() {
        return stockAnalysisRepository.findLatestOfEveryHolding().stream()
                .collect(Collectors.toMap(StockAnalysisEntity::holdingId, analysis -> analysis));
    }

    /**
     * What one stock's analysis is expected to cost: the average of {@code latestAnalyses}, one per holding — the runs
     * of today's searches and prices. {@code null} while nothing has been analyzed.
     */
    private EstimatedAnalysisCostResponse estimatedAnalysisCostOf(Collection<StockAnalysisEntity> latestAnalyses) {
        if (latestAnalyses.isEmpty()) {
            return null;
        }
        BigDecimal analysisCount = BigDecimal.valueOf(latestAnalyses.size());
        BigDecimal totalCostUsd = latestAnalyses.stream()
                .map(StockAnalysisEntity::costUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalTavilyCredits = BigDecimal.valueOf(latestAnalyses.stream()
                .mapToLong(StockAnalysisEntity::tavilyCredits)
                .sum());
        // Dividing a BigDecimal needs to be told how many decimal places to keep (0.10 / 3 never ends): 4 here, $0.0412
        // and 5.0000 credits; the UI rounds them for display.
        return new EstimatedAnalysisCostResponse(
                totalCostUsd.divide(analysisCount, 4, RoundingMode.HALF_UP),
                totalTavilyCredits.divide(analysisCount, 4, RoundingMode.HALF_UP),
                latestAnalyses.size());
    }

    private InvestorEntity accountOwnerOf(List<InvestorEntity> investors) {
        return investors.stream()
                .filter(InvestorEntity::isAccountOwner)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No account owner: V4__investors.sql creates one"));
    }

    // ── the holdings ────────────────────────────────────────────────────────────────────────────

    /**
     * {@code snapshotDate} is how far a still-{@code OPEN} holding's day count runs — see {@link HoldingHistory} —,
     * and the day the analysts' targets and the timing note are counted from; {@code accountOwnerId} is who holds the
     * shares the other investors' trades don't explain; {@code momentum}, {@code latestAnalysis} and {@code stock}
     * (what Finnhub answered, and when) are {@code null} while there are no closes, no analysis, or no answer from
     * Finnhub for the holding.
     */
    private HoldingResponse holdingResponseOf(HoldingEntity holdingEntity, LocalDate snapshotDate, long accountOwnerId,
                                              Momentum momentum, StockAnalysisEntity latestAnalysis, StockEntity stock,
                                              List<AnalystPriceTarget> analystPriceTargets) {
        Holding holding = holdingEntity.toIbHolding();
        HoldingHistory holdingHistory = holdingEntity.tradeHistory();
        MomentumResponse momentumResponse = momentum == null ? null : new MomentumResponse(momentum);
        AnalystTargetCalculator analystTargetCalculator = new AnalystTargetCalculator(analystPriceTargets,
                Utils.finiteOrNull(holding.marketPrice()), snapshotDate);

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
                signalOf(latestAnalysis, momentum),
                new AnalystTargetResponse(analystTargetCalculator),
                companyInfoOf(stock, momentum, snapshotDate),
                holdingEntity.industry());
    }

    /**
     * What Finnhub last answered about the holding's company, with the risk signs from it and from the closes, and the
     * timing note counted from {@code snapshotDate}; {@code null} while Finnhub never answered for the holding.
     */
    private CompanyInfoResponse companyInfoOf(StockEntity stock, Momentum momentum, LocalDate snapshotDate) {
        if (stock == null) {
            return null;
        }
        RiskSignsCalculator riskSignsCalculator = new RiskSignsCalculator(stock.finnhubData(), momentum);
        return new CompanyInfoResponse(riskSignsCalculator, stock.fetchedAt(), snapshotDate);
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
