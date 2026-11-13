package portfolioboss.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;

import java.util.stream.Collectors;

/**
 * Makes the API's errors something the UI can show as they are. {@code @RestControllerAdvice} applies it to every
 * controller. The class it extends already answers each web error with a {@code ProblemDetail} — a standard JSON
 * error body ({@code status}, {@code title}, {@code detail}) — including a 404 thrown as
 * {@code ResponseStatusException}, malformed JSON (400) and a wrong {@code Content-Type} (415).
 *
 * <p>Changed here: the {@code detail} of a failed {@code @Valid} — Spring's own is just "Invalid request content.",
 * which doesn't say which field was wrong —, and the answers to TWS's two failures, the same for every endpoint that
 * reads TWS (Scan again, Sync now), with the user's message as the {@code detail}.
 */
@RestControllerAdvice
class ApiErrorHandler extends ResponseEntityExceptionHandler {

    /** Another reading runs: not a failure, the button can be pressed again in a moment. */
    @ExceptionHandler(TwsBusyException.class)
    private ProblemDetail answerTwsBusy(TwsBusyException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, exception.getMessage());
    }

    /** TWS is closed, logged out, or silent; the console already has the detail. */
    @ExceptionHandler(TwsUnavailableException.class)
    private ProblemDetail answerTwsUnavailable(TwsUnavailableException exception) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, describeInvalidFields(exception));
        return handleExceptionInternal(exception, problemDetail, headers, status, request);
    }

    /** For example "quantity: must be greater than 0, tradeDate: must not be null" — sorted, so always the same. */
    private String describeInvalidFields(MethodArgumentNotValidException exception) {
        return exception.getBindingResult().getFieldErrors().stream()
                .map(this::describeInvalidField)
                .sorted()
                .collect(Collectors.joining(", "));
    }

    private String describeInvalidField(FieldError fieldError) {
        return fieldError.getField() + ": " + fieldError.getDefaultMessage();
    }
}
