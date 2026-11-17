package portfolioboss.exceptions;

/**
 * No stock of the latest scan's three lists has the candidate id asked for — an id of an earlier scan, or of a stock in
 * no list. The API answers it with 404 ({@code api.ApiErrorHandler}).
 */
public class CandidateNotFoundException extends RuntimeException {

    public CandidateNotFoundException(long candidateId) {
        super("No candidate with id " + candidateId + " in the latest scan's lists");
    }
}
