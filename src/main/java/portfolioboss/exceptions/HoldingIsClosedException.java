package portfolioboss.exceptions;

/**
 * A closed holding was asked to be analyzed: only open holdings are. The API answers it with 400
 * ({@code api.ApiErrorHandler}).
 */
public class HoldingIsClosedException extends RuntimeException {

    public HoldingIsClosedException(String symbol) {
        super(symbol + " is closed: only open holdings are analyzed");
    }
}
