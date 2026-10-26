package portfolioboss.exceptions;

/**
 * TWS could not be read: no socket, the connection refused, no portfolio in time, or no scan answered.
 *
 * <p>The message is the user's — what the Scan again button shows; {@link #reason()} is the console's, with the detail.
 */
public class TwsUnavailableException extends RuntimeException {

    private final String reason;

    public TwsUnavailableException(String message, String reason) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
