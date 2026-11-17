package portfolioboss.api.recommendations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.api.analysis.response.StockAnalysisResponse;
import portfolioboss.api.marketdata.response.AnalystTargetResponse;
import portfolioboss.api.marketdata.response.CompanyInfoResponse;
import portfolioboss.api.marketdata.response.MomentumResponse;
import portfolioboss.api.recommendations.response.CorrelationResponse;
import portfolioboss.api.recommendations.response.HotStockResponse;
import portfolioboss.api.recommendations.response.LatestRecommendationResponse;
import portfolioboss.api.recommendations.response.PickedStockResponse;
import portfolioboss.api.recommendations.response.RecommendationsResponse;
import portfolioboss.calculation.analysis.AnalystTrendCalculator;
import portfolioboss.calculation.analysis.ConsensusCalculator;
import portfolioboss.calculation.marketdata.DailyClosesWithSpy;
import portfolioboss.calculation.recommendations.HotStockCalculator;
import portfolioboss.calculation.recommendations.NewsScoreCalculator;
import portfolioboss.calculation.recommendations.RecommendationCalculator;
import portfolioboss.calculation.recommendations.RecommendationCandidate;
import portfolioboss.calculation.recommendations.ReturnsCorrelation;
import portfolioboss.db.analysis.StockAnalysisEntity;
import portfolioboss.db.analysis.StockAnalysisRepository;
import portfolioboss.db.investors.InvestorRepository;
import portfolioboss.db.marketdata.DailyCloseRepository;
import portfolioboss.db.portfolio.AccountStateRepository;
import portfolioboss.db.portfolio.HoldingEntity;
import portfolioboss.db.portfolio.HoldingRepository;
import portfolioboss.db.recommendations.InvestmentRecommendationEntity;
import portfolioboss.db.recommendations.InvestmentRecommendationRepository;
import portfolioboss.db.recommendations.ScanCandidateEntity;
import portfolioboss.db.recommendations.ScanCandidateRepository;
import portfolioboss.db.recommendations.ScanEntity;
import portfolioboss.db.recommendations.ScanRepository;
import portfolioboss.ib.DailyClose;
import portfolioboss.model.analysis.AnalystConsensus;
import portfolioboss.model.portfolio.HoldingStatus;
import portfolioboss.model.recommendations.HotStockLabel;
import portfolioboss.recommendations.CandidateWithItsList;
import portfolioboss.recommendations.ScanListsService;
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
 * it moves with most, who holds it already, (session 3) its latest analysis with the risk signs, and (session 4b) the
 * analysts' target from its stored Dow Jones targets. Which stock is in which list, and the short list, are
 * {@link ScanListsService}'s. Like {@code PortfolioReadService} it reads only the database, and all of it is worked out
 * on this read — a change of rule applies to old scans and old analyses too.
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
    private final ScanListsService scanListsService;

    public RecommendationsReadService(ScanRepository scanRepository, AccountStateRepository accountStateRepository,
                                      HoldingRepository holdingRepository, InvestorRepository investorRepository,
                                      DailyCloseRepository dailyCloseRepository,
                                      StockAnalysisRepository stockAnalysisRepository,
                                      ScanCandidateRepository scanCandidateRepository,
                                      InvestmentRecommendationRepository investmentRecommendationRepository,
                                      ScanListsService scanListsService) {
        this.scanRepository = scanRepository;
        this.accountStateRepository = accountStateRepository;
        this.holdingRepository = holdingRepository;
        this.investorRepository = investorRepository;
        this.dailyCloseRepository = dailyCloseRepository;
        this.stockAnalysisRepository = stockAnalysisRepository;
        this.scanCandidateRepository = scanCandidateRepository;
        this.investmentRecommendationRepository = investmentRecommendationRepository;
        this.scanListsService = scanListsService;
    }

    /**
     * Empty before the first scan. Built inside the transaction: with {@code open-in-view=false} that is the only place
     * where the scan's candidates and the holdings' trades can still be read.
     */
    @Transactional(readOnly = true)
    public Optional<RecommendationsResponse> currentRecommendations() {
        return scanRepository.findFirstByOrderByScannedAtDesc().map(this::toResponse);
    }

    private RecommendationsResponse toResponse(ScanEntity scan) {
        List<HoldingEntity> openHoldings = openHoldings();
        DailyClosesWithSpy dailyCloses = dailyClosesWithSpyOf(scan.candidates(), openHoldings);
        long accountOwnerId = investorRepository.accountOwner().id();
        Map<Integer, StockAnalysisEntity> latestAnalysisByConId = latestAnalysisByConIdOf(scan.candidates());
        // the day the timing note counts from: the scan's, not the clock's, so the same scan always reads the same
        LocalDate scanDate = dateOf(scan.scannedAt());
        List<CandidateWithItsList> candidatesWithTheirLists =
                scanListsService.candidatesWithTheirLists(scan, dailyCloses);
        Set<Long> shortListCandidateIds = scanListsService.shortListOf(scan, candidatesWithTheirLists)
                .candidates().stream()
                .map(candidateWithItsList -> candidateWithItsList.candidate().id())
                .collect(Collectors.toSet());
        Map<HotStockLabel, List<HotStockResponse>> stocksByList = candidatesWithTheirLists.stream()
                .collect(Collectors.groupingBy(
                        candidateWithItsList -> candidateWithItsList.hotStock().label(),
                        () -> new EnumMap<>(HotStockLabel.class),
                        Collectors.mapping(
                                candidateWithItsList -> hotStockResponseOf(candidateWithItsList, openHoldings,
                                        dailyCloses, accountOwnerId,
                                        latestAnalysisByConId.get(candidateWithItsList.candidate().conId()),
                                        scanDate,
                                        shortListCandidateIds.contains(candidateWithItsList.candidate().id())),
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

    /** The stored closes of every scanned stock, every open holding and SPY. */
    private DailyClosesWithSpy dailyClosesWithSpyOf(List<ScanCandidateEntity> candidates,
                                                    List<HoldingEntity> openHoldings) {
        Set<Integer> conIds = new HashSet<>();
        candidates.forEach(candidate -> conIds.add(candidate.conId()));
        openHoldings.forEach(holding -> conIds.add(holding.conId()));
        return dailyCloseRepository.dailyClosesWithSpy(conIds);
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

    /**
     * @param latestAnalysis {@code null} while the stock has never been analyzed
     * @param scanDate       the day the timing note counts from
     * @param inShortList    the full run analyzes it
     */
    private HotStockResponse hotStockResponseOf(CandidateWithItsList candidateWithItsList,
                                                List<HoldingEntity> openHoldings,
                                                DailyClosesWithSpy dailyCloses,
                                                long accountOwnerId,
                                                StockAnalysisEntity latestAnalysis,
                                                LocalDate scanDate,
                                                boolean inShortList) {
        ScanCandidateEntity candidate = candidateWithItsList.candidate();
        HotStockCalculator hotStock = candidateWithItsList.hotStock();
        Stock finnhubStock = latestAnalysis == null ? null : latestAnalysis.finnhubStock();
        StockAnalysisResult analysisResult = latestAnalysis == null ? null : latestAnalysis.result();
        RecommendationCandidate recommendationCandidate = new RecommendationCandidate(candidate.id(), hotStock,
                candidateWithItsList.analystTarget(), finnhubStock, analysisResult);
        // counted from the day of the analysis, as the recommendation counted it
        LocalDate analysisDate = latestAnalysis == null ? null : dateOf(latestAnalysis.analyzedAt());
        NewsScoreCalculator newsScore = analysisResult == null ? null
                : new NewsScoreCalculator(analysisResult.newsEvents(), analysisResult.recentActions(), analysisDate);
        // the rule for this one stock: none of what is asked below depends on the other stocks of a short list
        RecommendationCalculator recommendationRule =
                new RecommendationCalculator(List.of(recommendationCandidate), analysisDate);
        boolean riskSignsChecked = recommendationCandidate.hasCompanyFacts();
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
                correlationsWithHoldings(candidate, openHoldings, dailyCloses),
                investorsHolding(candidate, openHoldings, accountOwnerId),
                analysisResponseOf(latestAnalysis, hotStock),
                companyInfoOf(recommendationCandidate, latestAnalysis, scanDate),
                newsScore == null ? null : newsScore.score(),
                newsScore == null ? List.of() : newsScore.weighedNewsEvents(),
                newsScore == null ? List.of() : newsScore.countedAnalystMoves(),
                riskSignsChecked ? recommendationRule.riskWeightOf(recommendationCandidate) : null,
                riskSignsChecked ? recommendationRule.scoreOf(recommendationCandidate) : null,
                recommendationRule.canBePicked(recommendationCandidate),
                new AnalystTargetResponse(candidateWithItsList.analystTarget()),
                inShortList,
                recommendationRule.reasonsNotPickable(recommendationCandidate));
    }

    /**
     * What Finnhub answered with the stock's latest analysis, with the risk signs the pick weighs and the timing note
     * counted from {@code scanDate}; {@code null} while never analyzed, or when Finnhub didn't answer with the analysis.
     */
    private CompanyInfoResponse companyInfoOf(RecommendationCandidate recommendationCandidate,
                                              StockAnalysisEntity latestAnalysis, LocalDate scanDate) {
        if (recommendationCandidate.stock() == null) {
            return null;
        }
        return new CompanyInfoResponse(recommendationCandidate.riskSignsCalculator(), latestAnalysis.analyzedAt(),
                scanDate);
    }

    private LocalDate dateOf(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
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
                                                               DailyClosesWithSpy dailyCloses) {
        List<DailyClose> candidateCloses = dailyCloses.closesOf(candidate.conId());
        Comparator<CorrelationResponse> highestFirst =
                Comparator.comparingDouble(CorrelationResponse::coefficient).reversed();
        return openHoldings.stream()
                .filter(holding -> holding.conId() != candidate.conId())
                .map(holding -> correlationResponseOf(holding, new ReturnsCorrelation(candidateCloses,
                        dailyCloses.closesOf(holding.conId()))))
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
