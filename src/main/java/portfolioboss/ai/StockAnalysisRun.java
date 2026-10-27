package portfolioboss.ai;

import java.math.BigDecimal;
import java.util.List;

/**
 * One run of the analysis: an outcome per stock, in the order the stocks were given, and what the run cost — the
 * figures of its {@code [ai]} line. The costs are those of the stocks analyzed; a stock that failed may have spent some
 * too, uncounted.
 */
public record StockAnalysisRun(List<StockAnalysisOutcome> outcomes) {

    public List<StockAnalysisOutcome> analyzedOutcomes() {
        return outcomes.stream().filter(StockAnalysisOutcome::succeeded).toList();
    }

    public List<StockAnalysisOutcome> failedOutcomes() {
        return outcomes.stream().filter(outcome -> !outcome.succeeded()).toList();
    }

    public int tavilyCredits() {
        return analyzedOutcomes().stream().mapToInt(StockAnalysisOutcome::tavilyCredits).sum();
    }

    public long inputTokens() {
        return analyzedOutcomes().stream().mapToLong(outcome -> outcome.reply().inputTokens()).sum();
    }

    public long outputTokens() {
        return analyzedOutcomes().stream().mapToLong(outcome -> outcome.reply().outputTokens()).sum();
    }

    /** What Claude's tokens cost, in dollars. */
    public BigDecimal costUsd() {
        return analyzedOutcomes().stream()
                .map(outcome -> outcome.reply().costUsd())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
