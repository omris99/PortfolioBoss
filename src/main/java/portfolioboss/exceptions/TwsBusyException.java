package portfolioboss.exceptions;

/**
 * Another reading from TWS is running — a scan, a sync, or the startup's — and TWS takes one connection per client id.
 * The API answers it with 409 ({@code api.ApiErrorHandler}).
 */
public class TwsBusyException extends RuntimeException {

    public TwsBusyException(String message) {
        super(message);
    }
}
