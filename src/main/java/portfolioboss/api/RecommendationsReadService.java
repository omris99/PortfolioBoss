package portfolioboss.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.api.response.CorrelationResponse;
import portfolioboss.api.response.HotStockResponse;
import portfolioboss.api.response.LatestRecommendationResponse;
import portfolioboss.api.response.MomentumResponse;
import portfolioboss.api.response.PickedStockResponse;
import portfolioboss.api.response.RecommendationsResponse;
import portfolioboss.api.response.StockAnalysisResponse;
import portfolioboss.calculation.AnalystTrendCalculator;
import portfolioboss.calculation.ConsensusCalculator;
import portfolioboss.calculation.HotStockCalculator;
import portfolioboss.calculation.Momentum;
import portfolioboss.calculation.RecommendationCandidate;
import portfolioboss.calculation.ReturnsCorrelation;
import portfolioboss.db.AccountStateRepository;
import portfolioboss.db.DailyCloseRepository;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.InvestmentRecommendationEntity;
import portfolioboss.db.InvestmentRecommendationRepository;
import portfolioboss.db.InvestorRepository;
import portfolioboss.db.ScanCandidateEntity;
import portfolioboss.db.ScanCandidateRepository;
import portfolioboss.db.ScanEntity;
import portfolioboss.db.ScanRepository;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.db.StockAnalysisRepository;
import portfolioboss.ib.Benchmark;
import portfolioboss.ib.DailyClose;
import portfolioboss.model.AnalystConsensus;
import portfolioboss.model.HoldingStatus;
import portfolioboss.model.HotStockLabel;
import portfolioboss.stocks.Stock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the latest scan the sync stored and turns it into the Recommendations page's three lists (RECOMMENDATIONS_TODO.md,
 * session 1): each stock's momentum from its stored closes, what the rule of decision 1 says about it, the open holdings
 * it moves with most, who holds it already, and (session 3) its latest analysis with the risk signs. Like
 * {@code PortfolioReadService} it reads only the database, and all of it is worked out on this read — a change of rule
 * applies to old scans and old analyses too.
 */
@Service
public class RecommendationsReadService {

    /** The open holdings a stock moves with most, shown next to it. */
    private static final int MOST_CORRELATIONS_SHOWN = 3;

    private final ScanRepository scanRepository;
    private final AccountStateRepository accountStateRepository;
    private final HoldingRepository holdingRepository;
    private final InvestorRepository investorRepository;
    private final DailyCloseRepository dailyCloseRepository;
    private final StockAnalysisRepository stockAnalysisRepository;
    private final ScanCandidateRepository scanCandidateRepository;
    private final InvestmentRecommendationRepository investmentRecommendationRepository;

    public RecommendationsReadService(ScanRepository scanRepository, AccountStateRepository accountStateRepository,
                                      HoldingRepository holdingRepository, InvestorRepository investorRepository,
                                      DailyCloseRepository dailyCloseRepository,
                                      StockAnalysisRepository stockAnalysisRepository,
                                      ScanCandidateRepository scanCandidateRepository,
                                      InvestmentRecommendationRepository investmentRecommendationRepository) {
        this.scanRepository = scanRepository;
        this.accountStateRepository = accountStateRepository;
        this.holdingRepository = holdingRepository;
        this.investorRepository = investorRepository;
        this.dailyCloseRepository = dailyCloseRepository;
        this.stockAnalysisRepository = stockAnalysisRepository;
        this.scanCandidateRepository = scanCandidateRepository;
        this.investmentRecommendationRepository = investmentRecommendationRepository;
    }

    /**
     * Empty before the first scan. Built inside the transaction: with {@code open-in-view=false} that is the only place
     * where the scan's candidates and the holdings' trades can still be read.
     */
    @Transactional(readOnly = true)
    public Optional<RecommendationsResponse> currentRecommendations() {
        return scanRepository.findFirstByOrderByScannedAtDesc().map(this::toResponse);
    }

    /**
     * The stocks of the latest scan that are in one of the lists, in the page's order — HOT first —, worked out exactly
     * as for the page: what the Recommendations page's Analyze works on. Empty before the first scan.
     */
    @Transactional(readOnly = true)
    public List<CandidateWithItsList> candidatesWithTheirListsOfLatestScan() {
        return scanRepository.findFirstByOrderByScannedAtDesc()
                .map(scan -> candidatesWithTheirLists(scan.candidates(), dailyClosesOf(scan.candidates(), List.of())))
                .orElse(List.of());
    }

    private RecommendationsResponse toResponse(ScanEntity scan) {
        List<HoldingEntity> openHoldings = openHoldings();
        Map<Integer, List<DailyClose>> dailyClosesByConId = dailyClosesOf(scan.candidates(), openHoldings);
        long accountOwnerId = investorRepository.accountOwner().id();
        Map<Integer, StockAnalysisEntity> latestAnalysisByConId = latestAnalysisByConIdOf(scan.candidates());
        // the day the timing note counts from: the scan's, not the clock's, so the same scan always reads the same
        LocalDate scanDate = scan.scannedAt().atZone(ZoneId.systemDefault()).toLocalDate();
        Map<HotStockLabel, List<HotStockResponse>> stocksByList =
                candidatesWithTheirLists(scan.candidates(), dailyClosesByConId).stream()
                        .collect(Collectors.groupingBy(
                                candidateWithItsList -> candidateWithItsList.hotStock().label(),
                                () -> new EnumMap<>(HotStockLabel.class),
                                Collectors.mapping(
                                        candidateWithItsList -> hotStockResponseOf(candidateWithItsList, openHoldings,
                                                dailyClosesByConId, accountOwnerId,
                                                latestAnalysisByConId.get(candidateWithItsList.candidate().conId()),
                                                scanDate),
                                        Collectors.toList())));
        return new RecommendationsResponse(
                scan.scannedAt(),
                scan.candidates().size(),
                stocksByList.getOrDefault(HotStockLabel.HOT, List.of()),
                stocksByList.getOrDefault(HotStockLabel.TOO_HOT, List.of()),
                stocksByList.getOrDefault(HotStockLabel.CORRECTING, List.of()),
                latestRecommendation());
    }

    /**
     * The latest recommendation, with the date of the scan it came from and its stocks in its order; {@code null}
     * before the first full run.
     */
    private LatestRecommendationResponse latestRecommendation() {
        return investmentRecommendationRepository.findFirstByOrderByRecommendedAtDesc()
                .map(this::latestRecommendationResponseOf)
                .orElse(null);
    }

    private LatestRecommendationResponse latestRecommendationResponseOf(InvestmentRecommendationEntity recommendation) {
        Instant scannedAt = scanRepository.findById(recommendation.scanId()).orElseThrow().scannedAt();
        Map<Long, ScanCandidateEntity> pickedCandidateById =
                scanCandidateRepository.findAllById(recommendation.picks()).stream()
                        .collect(Collectors.toMap(ScanCandidateEntity::id, candidate -> candidate));
        List<PickedStockResponse> picks = recommendation.picks().stream()
                .map(pickedCandidateById::get)
                .map(PickedStockResponse::new)
                .toList();
        return new LatestRecommendationResponse(recommendation.recommendedAt(), scannedAt, picks);
    }

    /** The open holdings of the account the last sync read, with their trades — who holds what comes from them. */
    private List<HoldingEntity> openHoldings() {
        return accountStateRepository.findFirstByOrderByAsOfDesc()
                .map(accountState -> holdingRepository.findByAccountOrderById(accountState.account()))
                .orElse(List.of())
                .stream()
                .filter(holding -> holding.status() == HoldingStatus.OPEN)
                .toList();
    }

    /** The stored closes of every scanned stock, every open holding and SPY, by contract id. */
    private Map<Integer, List<DailyClose>> dailyClosesOf(List<ScanCandidateEntity> candidates,
                                                         List<HoldingEntity> openHoldings) {
        Set<Integer> conIds = new HashSet<>();
        candidates.forEach(candidate -> conIds.add(candidate.conId()));
        openHoldings.forEach(holding -> conIds.add(holding.conId()));
        conIds.add(Benchmark.SPY_CON_ID);
        return dailyCloseRepository.dailyClosesByConId(conIds);
    }

    /**
     * The latest analysis of each of the scan's stocks, by contract id — from any scan, so that a new scan doesn't hide
     * what was paid for (decision 12). Two queries: the analyses, then the rows they were made from — perhaps of an
     * earlier scan — for their contract ids.
     */
    private Map<Integer, StockAnalysisEntity> latestAnalysisByConIdOf(List<ScanCandidateEntity> candidates) {
        if (candidates.isEmpty()) {
            return Map.of();
        }
        Set<Integer> conIds = candidates.stream().map(ScanCandidateEntity::conId).collect(Collectors.toSet());
        List<StockAnalysisEntity> latestAnalyses = stockAnalysisRepository.findLatestOfEveryScannedStock(conIds);
        List<Long> analyzedCandidateIds = latestAnalyses.stream().map(StockAnalysisEntity::scanCandidateId).toList();
        Map<Long, Integer> conIdByCandidateId = scanCandidateRepository.findAllById(analyzedCandidateIds).stream()
                .collect(Collectors.toMap(ScanCandidateEntity::id, ScanCandidateEntity::conId));
        return latestAnalyses.stream().collect(Collectors.toMap(
                analysis -> conIdByCandidateId.get(analysis.scanCandidateId()), analysis -> analysis));
    }

    /** The candidates in one of the lists, in the page's order; a candidate in no list is left out. */
    private List<CandidateWithItsList> candidatesWithTheirLists(List<ScanCandidateEntity> candidates,
                                                                Map<Integer, List<DailyClose>> dailyClosesByConId) {
        return candidates.stream()
                .map(candidate -> candidateWithItsListOf(candidate, dailyClosesByConId))
                .flatMap(Optional::stream)
                .sorted((firstCandidate, secondCandidate) ->
                        firstCandidate.hotStock().comparePlaceInList(secondCandidate.hotStock()))
                .toList();
    }

    /** Empty when the stock is in no list: no stored closes, or no long upward trend. */
    private Optional<CandidateWithItsList> candidateWithItsListOf(ScanCandidateEntity candidate,
                                                  Map<Integer, List<DailyClose>> dailyClosesByConId) {
        Momentum momentum = Momentum.of(dailyClosesByConId.getOrDefault(candidate.conId(), List.of()),
                dailyClosesByConId.getOrDefault(Benchmark.SPY_CON_ID, List.of()));
        if (momentum == null) {
            return Optional.empty();
        }
        HotStockCalculator hotStock = new HotStockCalculator(momentum);
        return hotStock.label() == null ? Optional.empty() : Optional.of(new CandidateWithItsList(candidate, hotStock));
    }

    /**
     * @param latestAnalysis {@code null} while the stock has never been analyzed
     * @param scanDate       the day the timing note counts from
     */
    private HotStockResponse hotStockResponseOf(CandidateWithItsList candidateWithItsList,
                                                List<HoldingEntity> openHoldings,
                                                Map<Integer, List<DailyClose>> dailyClosesByConId,
                                                long accountOwnerId,
                                                StockAnalysisEntity latestAnalysis,
                                                LocalDate scanDate) {
        ScanCandidateEntity candidate = candidateWithItsList.candidate();
        HotStockCalculator hotStock = candidateWithItsList.hotStock();
        Stock finnhubStock = latestAnalysis == null ? null : latestAnalysis.finnhubStock();
        StockAnalysisResult analysisResult = latestAnalysis == null ? null : latestAnalysis.result();
        RecommendationCandidate recommendationCandidate =
                new RecommendationCandidate(candidate.id(), hotStock, finnhubStock, analysisResult);
        return new HotStockResponse(
                candidate.id(),
                candidate.conId(),
                candidate.symbol(),
                candidate.companyName(),
                candidate.exchange(),
                candidate.industry(),
                candidate.category(),
                hotStock.label(),
                new MomentumResponse(hotStock.momentum()),
                hotStock.percentAboveSma50(),
                hotStock.pulledBack(),
                hotStock.recovering(),
                correlationsWithHoldings(candidate, openHoldings, dailyClosesByConId),
                investorsHolding(candidate, openHoldings, accountOwnerId),
                analysisResponseOf(latestAnalysis, hotStock),
                finnhubStock == null ? null : finnhubStock.nextEarningsDate(),
                finnhubStock == null ? null : finnhubStock.companyFacts(),
                recommendationCandidate.hasCompanyFacts() ? recommendationCandidate.riskSigns() : null,
                recommendationCandidate.reportsWithin14Days(scanDate),
                newsScoreOf(recommendationCandidate, latestAnalysis));
    }

    /** Counted from the day of the analysis, as the recommendation counted it; {@code null} while never analyzed. */
    private Integer newsScoreOf(RecommendationCandidate recommendationCandidate, StockAnalysisEntity latestAnalysis) {
        if (latestAnalysis == null) {
            return null;
        }
        LocalDate analysisDate = latestAnalysis.analyzedAt().atZone(ZoneId.systemDefault()).toLocalDate();
        return recommendationCandidate.newsScore(analysisDate);
    }

    /**
     * As for a holding: the analysis as stored, with the consensus and the analysts' trend worked out from it on this
     * read — the targets measured against the last close.
     */
    private StockAnalysisResponse analysisResponseOf(StockAnalysisEntity latestAnalysis, HotStockCalculator hotStock) {
        if (latestAnalysis == null) {
            return null;
        }
        StockAnalysisResult result = latestAnalysis.result();
        AnalystConsensus consensus =
                new ConsensusCalculator(result.consensusBySource(), hotStock.momentum().lastClose()).consensus();
        return new StockAnalysisResponse(latestAnalysis, consensus, new AnalystTrendCalculator(result.recentActions()));
    }

    /** The open holdings the stock moves with most, highest first — not the stock itself when it is one of them. */
    private List<CorrelationResponse> correlationsWithHoldings(ScanCandidateEntity candidate,
                                                               List<HoldingEntity> openHoldings,
                                                               Map<Integer, List<DailyClose>> dailyClosesByConId) {
        List<DailyClose> candidateCloses = dailyClosesByConId.getOrDefault(candidate.conId(), List.of());
        Comparator<CorrelationResponse> highestFirst =
                Comparator.comparingDouble(CorrelationResponse::coefficient).reversed();
        return openHoldings.stream()
                .filter(holding -> holding.conId() != candidate.conId())
                .map(holding -> correlationResponseOf(holding, new ReturnsCorrelation(candidateCloses,
                        dailyClosesByConId.getOrDefault(holding.conId(), List.of()))))
                .filter(Objects::nonNull)
                .sorted(highestFirst)
                .limit(MOST_CORRELATIONS_SHOWN)
                .toList();
    }

    /** {@code null} when there is no coefficient: too few shared dates, or a price that never moved. */
    private CorrelationResponse correlationResponseOf(HoldingEntity holding, ReturnsCorrelation correlation) {
        Double coefficient = correlation.coefficient();
        if (coefficient == null) {
            return null;
        }
        return new CorrelationResponse(holding.id(), holding.symbol(), coefficient, correlation.movesTogether());
    }

    /** The investors who hold some of the stock, when it is an open holding already; empty otherwise. */
    private List<Long> investorsHolding(ScanCandidateEntity candidate, List<HoldingEntity> openHoldings,
                                        long accountOwnerId) {
        return openHoldings.stream()
                .filter(holding -> holding.conId() == candidate.conId())
                .flatMap(holding -> holding.toPositionTrades().partsByInvestor(accountOwnerId).keySet().stream())
                .toList();
    }
}
