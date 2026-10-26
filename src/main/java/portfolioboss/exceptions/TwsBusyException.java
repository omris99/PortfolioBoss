package portfolioboss.exceptions;

/** Another reading from TWS is running — a scan, or the startup's — and TWS takes one connection per client id. */
public class TwsBusyException extends RuntimeException {

    public TwsBusyException(String message) {
        super(message);
    }
}
