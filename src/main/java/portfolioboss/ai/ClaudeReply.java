package portfolioboss.ai;


import java.math.BigDecimal;

/**
 * What Claude answered about one stock, checked against the schema, and what the answer cost.
 *
 * @param model        the model that actually answered — a refusal fallback may have moved the request to another one
 * @param outputTokens the JSON and Claude's thinking together: both are billed as output
 * @param costUsd      what the tokens cost in dollars
 */
public record ClaudeReply(
        StockAnalysisResult result,
        String model,
        long inputTokens,
        long outputTokens,
        BigDecimal costUsd) {
}
