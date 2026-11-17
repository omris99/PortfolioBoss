package portfolioboss.exceptions;

/**
 * The Bank of Israel's exchange rates could not be downloaded: no answer came, the Bank of Israel answered with an HTTP
 * error, it published no rate in a period too long to be holidays (its series may have been renamed), or its answer could
 * not be read.
 *
 * <p>The message is the console's, short: never the HTML page the Bank of Israel answers an error with.
 */
public class ExchangeRatesUnavailableException extends RuntimeException {

    public ExchangeRatesUnavailableException(String message) {
        super(message);
    }
}
