package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.ai.AiKeys;
import portfolioboss.ai.ClaudeReply;
import portfolioboss.ai.StockAnalysisOutcome;
import portfolioboss.ai.StockAnalysisPipeline;
import portfolioboss.ai.StockAnalysisRun;
import portfolioboss.ai.StockToAnalyze;
import portfolioboss.api.response.AnalysisRunResponse;
import portfolioboss.api.response.RecommendationAnalysisRunResponse;
import portfolioboss.api.response.RecommendationPickResponse;
import portfolioboss.calculation.RecommendationCalculator;
import portfolioboss.calculation.RecommendationCandidate;
import portfolioboss.db.InvestmentRecommendationEntity;
import portfolioboss.db.InvestmentRecommendationRepository;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.db.StockAnalysisRepository;
import portfolioboss.stocks.FinnhubClient;
import portfolioboss.stocks.Stock;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * The Recommendations page's paid run (RECOMMENDATIONS_TODO.md, session 3), started only by the user's button: the
 * short list — the first HOT candidates of the latest scan whose analysts' target is at least 15% above the last close
 * (decision 14) — or one candidate of its lists goes through {@link StockAnalysisPipeline}, Finnhub is asked about each
 * beside it, and every analysis that succeeded is stored as a {@code stock_analysis} row of its candidate. After the
 * short list the code picks the recommendation ({@link RecommendationCalculator}) and stores it as an
 * {@code investment_recommendation} row — an empty one too, when no stock had enough upside and nothing was paid for;
 * one candidate's analysis picks nothing. It writes nothing else, and never reaches Interactive Brokers.
 *
 * <p>Not {@code @Transactional}, like {@code AnalysisService}: no database connection waits on the network. Finnhub and
 * Tavily are sent only the symbol, and Claude the symbol, the currency and the last close — never anything about the
 * portfolio (decision 6).
 */
@Service
public class RecommendationAnalysisService {

    /** The scan keeps stocks on NYSE and NASDAQ only (decision 2): every one is priced in dollars. */
    private static final String CURRENCY = "USD";

    private final AiKeys aiKeys;
    private final FinnhubClient finnhubClient;
    private final StockAnalysisPipeline stockAnalysisPipeline;
    private final RecommendationsReadService recommendationsReadService;
    private final StockAnalysisRepository stockAnalysisRepository;
    private final InvestmentRecommendationRepository investmentRecommendationRepository;

    public RecommendationAnalysisService(AiKeys aiKeys, FinnhubClient finnhubClient,
                                         StockAnalysisPipeline stockAnalysisPipeline,
                                         RecommendationsReadService recommendationsReadService,
                                         StockAnalysisRepository stockAnalysisRepository,
                                         InvestmentRecommendationRepository investmentRecommendationRepository) {
        this.aiKeys = aiKeys;
        this.finnhubClient = finnhubClient;
        this.stockAnalysisPipeline = stockAnalysisPipeline;
        this.recommendationsReadService = recommendationsReadService;
        this.stockAnalysisRepository = stockAnalysisRepository;
        this.investmentRecommendationRepository = investmentRecommendationRepository;
    }

    /**
     * {@code candidateId} {@code null}: the short list, then the recommendation. Otherwise that candidate alone, from any
     * of the three lists, and no recommendation. A missing key is 503, a candidate that isn't in the latest scan's lists
     * 404 — both before anything is searched.
     */
    public RecommendationAnalysisRunResponse analyze(Long candidateId) {
        boolean recommending = candidateId == null;
        refuseWhileAKeyIsMissing(recommending);
        ShortList shortList = recommending ? recommendationsReadService.shortListOfLatestScan() : null;
        List<CandidateWithItsList> candidates =
                recommending ? shortList.candidates() : List.of(findCandidateInTheLists(candidateId));
        Instant analyzedAt = Instant.now();
        LocalDate today = analyzedAt.atZone(ZoneId.systemDefault()).toLocalDate();

        List<Future<Stock>> pendingFinnhubStocks;
        StockAnalysisRun run;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            // Finnhub answers in a second; it runs beside the analysis, which takes far longer
            pendingFinnhubStocks = candidates.stream()
                    .map(candidateWithItsList -> executor.submit(() -> finnhubStockOf(candidateWithItsList, today)))
                    .toList();
            run = stockAnalysisPipeline.analyzeAll(candidates.stream().map(this::stockToAnalyze).toList(), today);
        }
        List<Stock> finnhubStocks = pendingFinnhubStocks.stream().map(Future::resultNow).toList();

        stockAnalysisRepository.saveAll(analysesToStore(candidates, finnhubStocks, run, analyzedAt));
        List<RecommendationPickResponse> picks = recommending ? pick(shortList, finnhubStocks, run, today) : null;
        if (recommending && shortList.scanId() != null) {
            storeTheRecommendation(shortList.scanId(), picks, analyzedAt);
        }
        return new RecommendationAnalysisRunResponse(new AnalysisRunResponse(run), picks);
    }

    /**
     * Every full run's picks are kept, an empty list too — "no recommendation this time" is an answer the measurement
     * counts (decision 9), also when no stock had enough upside and none was analyzed.
     */
    private void storeTheRecommendation(long scanId, List<RecommendationPickResponse> picks, Instant recommendedAt) {
        List<Long> pickedCandidateIds = picks.stream().map(RecommendationPickResponse::candidateId).toList();
        investmentRecommendationRepository.save(
                new InvestmentRecommendationEntity(scanId, recommendedAt, pickedCandidateIds));
    }

    /** The recommendation needs Finnhub's figures too: without them no stock can be picked (decision 5). */
    private void refuseWhileAKeyIsMissing(boolean recommending) {
        String missingKeyName = aiKeys.findMissingKeyName();
        if (missingKeyName != null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The analysis is off: " + missingKeyName + " is not set in config/local.env");
        }
        if (recommending && !finnhubClient.hasApiKey()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The recommendation is off: FINNHUB_API_KEY is not set in config/local.env, and without the "
                            + "company figures no stock can be picked");
        }
    }

    private CandidateWithItsList findCandidateInTheLists(long candidateId) {
        return recommendationsReadService.candidatesWithTheirListsOfLatestScan().stream()
                .filter(candidateWithItsList -> candidateWithItsList.candidate().id() == candidateId)
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No candidate with id " + candidateId + " in the latest scan's lists"));
    }

    /** The symbol, the currency and the last close IB sent — all Claude is told about the stock (decision 6). */
    private StockToAnalyze stockToAnalyze(CandidateWithItsList candidateWithItsList) {
        return new StockToAnalyze(candidateWithItsList.candidate().symbol(), CURRENCY,
                candidateWithItsList.hotStock().momentum().lastClose());
    }

    /**
     * Never throws: without the key, or when a call fails, the stock is analyzed all the same, without Finnhub's
     * answer — and can't be picked.
     */
    private Stock finnhubStockOf(CandidateWithItsList candidateWithItsList, LocalDate today) {
        if (!finnhubClient.hasApiKey()) {
            return null;
        }
        String symbol = candidateWithItsList.candidate().symbol();
        try {
            return finnhubClient.stock(symbol, today);
        } catch (RuntimeException e) {
            System.err.println("[stocks error] " + symbol + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * The candidates, Finnhub's answers and the run's outcomes are in the same order; a stock that failed stores
     * nothing.
     */
    private List<StockAnalysisEntity> analysesToStore(List<CandidateWithItsList> candidates, List<Stock> finnhubStocks,
                                                      StockAnalysisRun run, Instant analyzedAt) {
        List<StockAnalysisEntity> analyses = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            StockAnalysisOutcome outcome = run.outcomes().get(index);
            if (outcome.succeeded()) {
                ClaudeReply reply = outcome.reply();
                analyses.add(new StockAnalysisEntity(candidates.get(index).candidate(), finnhubStocks.get(index),
                        analyzedAt, reply.model(), reply.result(), outcome.searches(),
                        Math.toIntExact(reply.inputTokens()), Math.toIntExact(reply.outputTokens()),
                        outcome.tavilyCredits(), reply.costUsd()));
            }
        }
        return analyses;
    }

    /** Only the stocks analyzed can be picked: one that failed has no analysis to show beside the pick. */
    private List<RecommendationPickResponse> pick(ShortList shortList, List<Stock> finnhubStocks, StockAnalysisRun run,
                                                  LocalDate today) {
        List<CandidateWithItsList> candidates = shortList.candidates();
        List<RecommendationCandidate> analyzedCandidates = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            StockAnalysisOutcome outcome = run.outcomes().get(index);
            if (outcome.succeeded()) {
                CandidateWithItsList candidateWithItsList = candidates.get(index);
                analyzedCandidates.add(new RecommendationCandidate(candidateWithItsList.candidate().id(),
                        candidateWithItsList.hotStock(), candidateWithItsList.analystTarget(), finnhubStocks.get(index),
                        outcome.reply().result()));
            }
        }
        RecommendationCalculator recommendationCalculator = new RecommendationCalculator(analyzedCandidates, today);
        List<RecommendationCandidate> picks = recommendationCalculator.picks();
        printPicks(picks, analyzedCandidates.size(), shortList);
        return picks.stream().map(pick -> pickResponseOf(pick, recommendationCalculator, today)).toList();
    }

    /**
     * A pick always has Finnhub's answer and a target with enough upside: a stock without either is never picked.
     */
    private RecommendationPickResponse pickResponseOf(RecommendationCandidate pick,
                                                      RecommendationCalculator recommendationCalculator,
                                                      LocalDate today) {
        return new RecommendationPickResponse(pick.candidateId(), pick.stock().symbol(), pick.riskSigns(),
                pick.reportsWithin14Days(today), pick.newsScore(today), recommendationCalculator.scoreOf(pick),
                pick.analystTarget().upsidePercent());
    }

    /**
     * For example "[ai] recommended ABCD, WXYZ (of 7 analyzed; 7 of 41 hot stocks have the analysts' target 15% above
     * the price)", or "[ai] no recommendation: none of 7 qualified (…)".
     */
    private void printPicks(List<RecommendationCandidate> picks, int analyzedCount, ShortList shortList) {
        String upsideCounts = shortList.withEnoughUpsideCount() + " of " + shortList.hotStockCount()
                + " hot stocks have the analysts' target 15% above the price";
        if (picks.isEmpty()) {
            System.out.println("[ai] no recommendation: none of " + analyzedCount + " qualified (" + upsideCounts + ")");
            return;
        }
        String symbols = picks.stream().map(pick -> pick.stock().symbol()).collect(Collectors.joining(", "));
        System.out.println("[ai] recommended " + symbols + " (of " + analyzedCount + " analyzed; " + upsideCounts + ")");
    }
}
