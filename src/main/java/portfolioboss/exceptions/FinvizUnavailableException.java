package portfolioboss.exceptions;

/**
 * Finviz could not give a screen (RECOMMENDATIONS_FINVIZ_TODO.md): it refused the token — a subscription that ended —,
 * its limit of requests was passed, no answer came, or the answer could not be read as a screen's CSV — a column missing
 * from it, or a value that isn't a figure.
 *
 * <p>The message is the console's. It never holds the address the call went to, since the token is part of it.
 */
public class FinvizUnavailableException extends RuntimeException {

    public FinvizUnavailableException(String message) {
        super(message);
    }
}
