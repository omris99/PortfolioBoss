package portfolioboss.api.response;

import java.math.BigDecimal;

/**
 * What analyzing one stock is expected to cost, quoted by the UI's Analyze buttons before a paid run — in place of a
 * figure written by hand. It is the average over the latest analysis of every holding that has one: those are the
 * runs of today's searches and prices, where every analysis ever stored would also count the older, cheaper runs. The
 * component names are JSON keys.
 *
 * @param costUsdPerStock       Claude's dollars for one stock
 * @param tavilyCreditsPerStock Tavily's credits for one stock
 * @param basedOnAnalyses       how many analyses the average is over
 */
public record EstimatedAnalysisCostResponse(BigDecimal costUsdPerStock, BigDecimal tavilyCreditsPerStock,
                                            int basedOnAnalyses) {
}
