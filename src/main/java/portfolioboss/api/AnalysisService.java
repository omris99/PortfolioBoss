package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.ai.AiKeys;
import portfolioboss.ai.ClaudeReply;
import portfolioboss.ai.StockAnalyzer;
import portfolioboss.ai.StockSearches;
import portfolioboss.ai.StockToAnalyze;
import portfolioboss.ai.TavilyClient;
import portfolioboss.api.response.AnalysisRunResponse;
import portfolioboss.api.response.FailedAnalysisResponse;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.StockAnalysisEntity;
import portfolioboss.db.StockAnalysisRepository;
import portfolioboss.model.HoldingStatus;
import portfolioboss.utils.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * One run of the stock analysis (AI_ANALYSIS_TODO.md, session 2), started only by the user's button (decision 10): for
 * each holding, Tavily's four searches and one call to Claude, all the stocks at once; then every analysis that
 * succeeded is stored as a {@code stock_analysis} row. It writes nothing else, and never reaches Interactive Brokers.
 *
 * <p>Not {@code @Transactional} on purpose: the searches and Claude take seconds, and no database connection should
 * wait on them. The reads and the final save each go through a repository, which is a short transaction of its own.
 * A stock that fails is not stored and doesn't stop the others; it keeps its previous analysis.
 */
@Service
public class AnalysisService {

    private final AiKeys aiKeys;
    private final TavilyClient tavilyClient;
    private final StockAnalyzer stockAnalyzer;
    private final HoldingRepository holdingRepository;
    private final StockAnalysisRepository stockAnalysisRepository;

    public AnalysisService(AiKeys aiKeys, TavilyClient tavilyClient, StockAnalyzer stockAnalyzer,
                           HoldingRepository holdingRepository, StockAnalysisRepository stockAnalysisRepository) {
        this.aiKeys = aiKeys;
        this.tavilyClient = tavilyClient;
        this.stockAnalyzer = stockAnalyzer;
        this.holdingRepository = holdingRepository;
        this.stockAnalysisRepository = stockAnalysisRepository;
    }

    /**
     * {@code holdingIds} empty or {@code null}: every open holding. A missing key is 503, an id that doesn't exist 404,
     * a closed holding 400 — all before anything is searched.
     */
    public AnalysisRunResponse analyze(List<Long> holdingIds) {
        refuseWhileAKeyIsMissing();
        List<HoldingEntity> holdings = findHoldingsToAnalyze(holdingIds);
        Instant analyzedAt = Instant.now();
        LocalDate today = analyzedAt.atZone(ZoneId.systemDefault()).toLocalDate();

        List<StockOutcome> outcomes = analyzeInParallel(holdings, today);

        List<StockAnalysisEntity> analyses = outcomes.stream()
                .filter(StockOutcome::succeeded)
                .map(outcome -> outcome.toEntity(analyzedAt))
                .toList();
        stockAnalysisRepository.saveAll(analyses);
        AnalysisRunResponse summary = summarize(outcomes);
        printSummary(summary, holdings.size());
        return summary;
    }

    private void refuseWhileAKeyIsMissing() {
        String missingKeyName = aiKeys.findMissingKeyName();
        if (missingKeyName != null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The analysis is off: " + missingKeyName + " is not set in config/local.env");
        }
    }

    private List<HoldingEntity> findHoldingsToAnalyze(List<Long> holdingIds) {
        if (holdingIds == null || holdingIds.isEmpty()) {
            return holdingRepository.findByStatusOrderById(HoldingStatus.OPEN);
        }
        return holdingIds.stream().distinct().map(this::findOpenHolding).toList();
    }

    private HoldingEntity findOpenHolding(long holdingId) {
        HoldingEntity holding = holdingRepository.findById(holdingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No holding with id " + holdingId));
        if (holding.status() != HoldingStatus.OPEN) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    holding.symbol() + " is closed: only open holdings are analyzed");
        }
        return holding;
    }

    /**
     * Every stock at once, each on a virtual thread of its own — Java 21's cheap threads, made for waiting on the
     * network. Closing the executor waits until every stock is done; each call's own time limit keeps that short.
     */
    private List<StockOutcome> analyzeInParallel(List<HoldingEntity> holdings, LocalDate today) {
        List<Future<StockOutcome>> pendingOutcomes;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            pendingOutcomes = holdings.stream()
                    .map(holding -> executor.submit(() -> analyzeOne(holding, today)))
                    .toList();
        }
        return pendingOutcomes.stream().map(Future::resultNow).toList();
    }

    /**
     * Never throws: a failure becomes an outcome with its reason, so one stock can't stop the others. Only the results
     * that name the stock go to Claude (decision 17), and the very same are stored with its answer (decision 22).
     */
    private StockOutcome analyzeOne(HoldingEntity holding, LocalDate today) {
        String symbol = holding.symbol();
        try {
            StockSearches searchesAboutTheStock = new StockSearches(
                    tavilyClient.searchAnalystForecasts(symbol),
                    tavilyClient.searchMarketBeatAnalystActions(symbol),
                    tavilyClient.searchLatestAnalystActions(symbol),
                    tavilyClient.searchNews(symbol))
                    .mentioning(symbol);
            ClaudeReply reply = stockAnalyzer.analyze(stockToAnalyze(holding), today, searchesAboutTheStock);
            return new StockOutcome(holding.id(), symbol, reply, searchesAboutTheStock, null);
        } catch (RuntimeException e) {
            String failureMessage = describeFailure(e);
            System.err.println("[ai error] " + symbol + ": " + failureMessage);
            return new StockOutcome(holding.id(), symbol, null, null, failureMessage);
        }
    }

    /** The symbol, the currency and IB's price — all Claude is told about the holding (decision 8). */
    private StockToAnalyze stockToAnalyze(HoldingEntity holding) {
        Double marketPrice = Utils.finiteOrNull(holding.toIbHolding().marketPrice());
        return new StockToAnalyze(holding.symbol(), holding.currency(), marketPrice);
    }

    private String describeFailure(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    private AnalysisRunResponse summarize(List<StockOutcome> outcomes) {
        List<StockOutcome> analyzedOutcomes = outcomes.stream().filter(StockOutcome::succeeded).toList();
        List<FailedAnalysisResponse> failures = outcomes.stream()
                .filter(outcome -> !outcome.succeeded())
                .map(outcome -> new FailedAnalysisResponse(outcome.symbol(), outcome.failureMessage()))
                .toList();
        return new AnalysisRunResponse(
                analyzedOutcomes.size(),
                failures,
                analyzedOutcomes.stream().mapToInt(StockOutcome::tavilyCredits).sum(),
                analyzedOutcomes.stream().mapToLong(outcome -> outcome.reply().inputTokens()).sum(),
                analyzedOutcomes.stream().mapToLong(outcome -> outcome.reply().outputTokens()).sum(),
                analyzedOutcomes.stream()
                        .map(outcome -> outcome.reply().costUsd())
                        .reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    /** For example "[ai] analyzed 7 of 7 stocks: 21 Tavily credits, 73,500 input and 9,800 output tokens, $0.2450". */
    private void printSummary(AnalysisRunResponse summary, int stockCount) {
        System.out.printf("[ai] analyzed %d of %d stocks: %d Tavily credits, %,d input and %,d output tokens, $%s%n",
                summary.analyzed(), stockCount, summary.tavilyCredits(), summary.inputTokens(),
                summary.outputTokens(), summary.costUsd().setScale(4, RoundingMode.HALF_UP).toPlainString());
    }

    /**
     * What one stock's analysis came to: Claude's reply and the search results it read, or — {@code reply} and
     * {@code searches} being {@code null} — why it failed.
     */
    private record StockOutcome(long holdingId, String symbol, ClaudeReply reply, StockSearches searches,
                                String failureMessage) {

        private boolean succeeded() {
            return reply != null;
        }

        /** What its searches cost; a stock that failed is not counted. */
        private int tavilyCredits() {
            return searches == null ? 0 : searches.credits();
        }

        private StockAnalysisEntity toEntity(Instant analyzedAt) {
            return new StockAnalysisEntity(holdingId, analyzedAt, reply.model(), reply.result(), searches,
                    Math.toIntExact(reply.inputTokens()), Math.toIntExact(reply.outputTokens()), tavilyCredits(),
                    reply.costUsd());
        }
    }
}
