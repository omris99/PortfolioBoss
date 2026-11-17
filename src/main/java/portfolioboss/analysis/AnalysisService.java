package portfolioboss.analysis;

import org.springframework.stereotype.Service;
import portfolioboss.ai.AiKeys;
import portfolioboss.ai.ClaudeReply;
import portfolioboss.ai.StockAnalysisOutcome;
import portfolioboss.ai.StockAnalysisPipeline;
import portfolioboss.ai.StockAnalysisRun;
import portfolioboss.ai.StockToAnalyze;
import portfolioboss.db.analysis.StockAnalysisEntity;
import portfolioboss.db.analysis.StockAnalysisRepository;
import portfolioboss.db.portfolio.HoldingEntity;
import portfolioboss.db.portfolio.HoldingRepository;
import portfolioboss.exceptions.AnalysisOffException;
import portfolioboss.exceptions.HoldingIsClosedException;
import portfolioboss.exceptions.HoldingNotFoundException;
import portfolioboss.model.portfolio.HoldingStatus;
import portfolioboss.utils.Utils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * One run of the holdings' analysis (AI_ANALYSIS_TODO.md, session 2), started only by the user's button (decision 10):
 * the holdings go through {@link StockAnalysisPipeline} — Tavily's four searches and Claude, every stock at once — and
 * every analysis that succeeded is stored as a {@code stock_analysis} row. It writes nothing else, and never reaches
 * Interactive Brokers.
 *
 * <p>Not {@code @Transactional} on purpose: the searches and Claude take seconds, and no database connection should
 * wait on them. The reads and the final save each go through a repository, which is a short transaction of its own.
 * A stock that fails is not stored and doesn't stop the others; it keeps its previous analysis.
 */
@Service
public class AnalysisService {

    private final AiKeys aiKeys;
    private final StockAnalysisPipeline stockAnalysisPipeline;
    private final HoldingRepository holdingRepository;
    private final StockAnalysisRepository stockAnalysisRepository;

    public AnalysisService(AiKeys aiKeys, StockAnalysisPipeline stockAnalysisPipeline,
                           HoldingRepository holdingRepository, StockAnalysisRepository stockAnalysisRepository) {
        this.aiKeys = aiKeys;
        this.stockAnalysisPipeline = stockAnalysisPipeline;
        this.holdingRepository = holdingRepository;
        this.stockAnalysisRepository = stockAnalysisRepository;
    }

    /**
     * {@code holdingIds} empty or {@code null}: every open holding. A missing key, an id that doesn't exist and a closed
     * holding are refused before anything is searched.
     *
     * @return what the run did and cost — the outcomes in the order of the holdings
     * @throws AnalysisOffException      while Tavily's or Claude's key is missing
     * @throws HoldingNotFoundException  for an id no holding has
     * @throws HoldingIsClosedException  for a closed holding
     */
    public StockAnalysisRun analyze(List<Long> holdingIds) {
        refuseWhileAKeyIsMissing();
        List<HoldingEntity> holdings = findHoldingsToAnalyze(holdingIds);
        Instant analyzedAt = Instant.now();
        LocalDate today = analyzedAt.atZone(ZoneId.systemDefault()).toLocalDate();

        StockAnalysisRun run = stockAnalysisPipeline.analyzeAll(
                holdings.stream().map(this::stockToAnalyze).toList(), today);

        stockAnalysisRepository.saveAll(analysesToStore(holdings, run, analyzedAt));
        return run;
    }

    private void refuseWhileAKeyIsMissing() {
        String missingKeyName = aiKeys.findMissingKeyName();
        if (missingKeyName != null) {
            throw new AnalysisOffException(
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
                .orElseThrow(() -> new HoldingNotFoundException(holdingId));
        if (holding.status() != HoldingStatus.OPEN) {
            throw new HoldingIsClosedException(holding.symbol());
        }
        return holding;
    }

    /** The symbol, the currency and IB's price — all Claude is told about the holding (decision 8). */
    private StockToAnalyze stockToAnalyze(HoldingEntity holding) {
        Double marketPrice = Utils.finiteOrNull(holding.toIbHolding().marketPrice());
        return new StockToAnalyze(holding.symbol(), holding.currency(), marketPrice);
    }

    /** The run's outcomes are in the order of the holdings; a stock that failed stores nothing. */
    private List<StockAnalysisEntity> analysesToStore(List<HoldingEntity> holdings, StockAnalysisRun run,
                                                      Instant analyzedAt) {
        List<StockAnalysisEntity> analyses = new ArrayList<>();
        for (int index = 0; index < holdings.size(); index++) {
            StockAnalysisOutcome outcome = run.outcomes().get(index);
            if (outcome.succeeded()) {
                ClaudeReply reply = outcome.reply();
                analyses.add(new StockAnalysisEntity(holdings.get(index).id(), analyzedAt, reply.model(),
                        reply.result(), outcome.searches(), Math.toIntExact(reply.inputTokens()),
                        Math.toIntExact(reply.outputTokens()), outcome.tavilyCredits(), reply.costUsd()));
            }
        }
        return analyses;
    }
}
