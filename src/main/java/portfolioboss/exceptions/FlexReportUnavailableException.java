package portfolioboss.exceptions;

/**
 * A report could not be downloaded from IB's Flex Web Service: IB refused the request (a token that expired, an address
 * it doesn't allow, a query that isn't valid), the report was not ready in time, no answer came, or the answer could not
 * be read — a field missing from it, most likely one not ticked in the Flex query.
 *
 * <p>The message is the console's: IB's own code and words when it gave some. It never holds the address the call went
 * to, since the token is part of it.
 */
public class FlexReportUnavailableException extends RuntimeException {

    public FlexReportUnavailableException(String message) {
        super(message);
    }
}
