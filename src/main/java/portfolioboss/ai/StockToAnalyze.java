package portfolioboss.ai;

/**
 * Everything Claude is told about a holding (AI_ANALYSIS_TODO.md, decision 8): never a quantity, a cost, an investor or
 * the account number — {@link StockAnalyzer} gets nothing else to tell.
 *
 * @param marketPrice IB's last price; {@code null} when IB did not report one
 */
public record StockToAnalyze(String symbol, String currency, Double marketPrice) {
}
