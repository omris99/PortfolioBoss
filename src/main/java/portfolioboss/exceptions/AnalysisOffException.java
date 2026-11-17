package portfolioboss.exceptions;

/**
 * A paid analysis was asked for while a key it needs is missing from {@code config/local.env} — Tavily's or Claude's,
 * or, for the recommendation, Finnhub's. Nothing was searched or sent. The API answers it with 503
 * ({@code api.ApiErrorHandler}); the message names the key.
 */
public class AnalysisOffException extends RuntimeException {

    public AnalysisOffException(String message) {
        super(message);
    }
}
