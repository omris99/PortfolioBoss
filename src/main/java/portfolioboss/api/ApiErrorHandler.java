package portfolioboss.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.stream.Collectors;

/**
 * Makes the API's errors something the UI can show as they are. {@code @RestControllerAdvice} applies it to every
 * controller. The class it extends already answers each web error with a {@code ProblemDetail} — a standard JSON
 * error body ({@code status}, {@code title}, {@code detail}) — including a 404 thrown as
 * {@code ResponseStatusException}, malformed JSON (400) and a wrong {@code Content-Type} (415).
 *
 * <p>The one thing changed here is the {@code detail} of a failed {@code @Valid}: Spring's own is just
 * "Invalid request content.", which doesn't say which field was wrong.
 */
@RestControllerAdvice
class ApiErrorHandler extends ResponseEntityExceptionHandler {

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
