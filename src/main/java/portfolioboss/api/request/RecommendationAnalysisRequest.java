package portfolioboss.api.request;

/**
 * The body of {@code POST /api/recommendations/analysis}: which scanned stock to analyze — missing for the short list
 * and the recommendation, so that run is {@code {}}. A body even then, like {@link AnalysisRequest}: the endpoint
 * accepts JSON only, so a page on another site can't start a paid run with a form.
 *
 * @param candidateId the stock's row in the latest scan; {@code null} for the short list
 */
public record RecommendationAnalysisRequest(Long candidateId) {
}
