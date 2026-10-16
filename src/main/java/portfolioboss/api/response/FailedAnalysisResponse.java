package portfolioboss.api.response;

/**
 * A stock the run could not analyze, and why — a search that failed, Claude declining, a call that took too long. It
 * keeps its previous analysis. The component names are JSON keys.
 */
public record FailedAnalysisResponse(String symbol, String message) {
}
