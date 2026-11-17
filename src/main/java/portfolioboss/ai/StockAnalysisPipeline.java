package portfolioboss.ai;

import org.springframework.stereotype.Component;

import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The analysis of stocks from start to end, the same for the holdings' Analyze and the Recommendations page's: for each
 * stock Tavily's four searches, only the results that name it (AI_ANALYSIS_TODO.md, decision 17), then Claude's reading
 * of them — every stock at once. A stock that fails doesn't stop the others: its outcome says why. It stores nothing;
 * what to keep is the caller's.
 */
@Component
public class StockAnalysisPipeline {

    private final TavilyClient tavilyClient;
    private final StockAnalyzer stockAnalyzer;

    private StockAnalysisPipeline(TavilyClient tavilyClient, StockAnalyzer stockAnalyzer) {
        this.tavilyClient = tavilyClient;
        this.stockAnalyzer = stockAnalyzer;
    }

    /**
     * Every stock at once, each on a virtual thread of its own — Java 21's cheap threads, made for waiting on the
     * network. Closing the executor waits until every stock is done; each call's own time limit keeps that short. Prints
     * the run's {@code [ai]} line, and an {@code [ai error]} line for each stock that failed.
     *
     * @return an outcome per stock, in the order of {@code stocks}
     */
    public StockAnalysisRun analyzeAll(List<StockToAnalyze> stocks, LocalDate today) {
        List<Future<StockAnalysisOutcome>> pendingOutcomes;
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            pendingOutcomes = stocks.stream()
                    .map(stock -> executor.submit(() -> analyzeOne(stock, today)))
                    .toList();
        }
        StockAnalysisRun run = new StockAnalysisRun(pendingOutcomes.stream().map(Future::resultNow).toList());
        printSummary(run);
        return run;
    }

    /**
     * Never throws: a failure becomes an outcome with its reason, so one stock can't stop the others. Only the results
     * that name the stock go to Claude (decision 17), and the very same are kept with its answer (decision 22).
     */
    private StockAnalysisOutcome analyzeOne(StockToAnalyze stock, LocalDate today) {
        String symbol = stock.symbol();
        try {
            StockSearches searchesAboutTheStock = new StockSearches(
                    tavilyClient.searchAnalystForecasts(symbol),
                    tavilyClient.searchMarketBeatAnalystActions(symbol),
                    tavilyClient.searchLatestAnalystActions(symbol),
                    tavilyClient.searchNews(symbol))
                    .mentioning(symbol);
            ClaudeReply reply = stockAnalyzer.analyze(stock, today, searchesAboutTheStock);
            return new StockAnalysisOutcome(symbol, reply, searchesAboutTheStock, null);
        } catch (RuntimeException e) {
            String failureMessage = describeFailure(e);
            System.err.println("[ai error] " + symbol + ": " + failureMessage);
            return new StockAnalysisOutcome(symbol, null, null, failureMessage);
        }
    }

    private String describeFailure(RuntimeException exception) {
        return exception.getMessage() == null ? exception.getClass().getSimpleName() : exception.getMessage();
    }

    /** For example "[ai] analyzed 7 of 7 stocks: 21 Tavily credits, 73,500 input and 9,800 output tokens, $0.2450". */
    private void printSummary(StockAnalysisRun run) {
        System.out.printf("[ai] analyzed %d of %d stocks: %d Tavily credits, %,d input and %,d output tokens, $%s%n",
                run.analyzedOutcomes().size(), run.outcomes().size(), run.tavilyCredits(), run.inputTokens(),
                run.outputTokens(), run.costUsd().setScale(4, RoundingMode.HALF_UP).toPlainString());
    }
}
