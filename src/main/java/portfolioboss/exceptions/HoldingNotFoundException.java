package portfolioboss.exceptions;

/** No holding has the id asked for. The API answers it with 404 ({@code api.ApiErrorHandler}). */
public class HoldingNotFoundException extends RuntimeException {

    public HoldingNotFoundException(long holdingId) {
        super("No holding with id " + holdingId);
    }
}
