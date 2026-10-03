package portfolioboss.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.request.ManualClosedPositionRequest;
import portfolioboss.api.response.ClosedPositionResponse;

/**
 * The endpoints that write the closed positions entered by hand to PortfolioBoss's own database. Like
 * {@link HoldingWriteController} they never reach Interactive Brokers, accept JSON only ({@code consumes}, for the same
 * reason: a page on another site can't send JSON to localhost without a CORS permission nobody grants), and are checked
 * by {@code @Valid} before the method runs. A closed position derived from trades is not written here: it is corrected
 * through its holding's trades. After a write the UI reloads {@code /api/portfolio}.
 */
@RestController
class ClosedPositionWriteController {

    private final ClosedPositionWriteService closedPositionWriteService;

    protected ClosedPositionWriteController(ClosedPositionWriteService closedPositionWriteService) {
        this.closedPositionWriteService = closedPositionWriteService;
    }

    @PostMapping(path = "/api/manual-closed-positions", consumes = MediaType.APPLICATION_JSON_VALUE)
    protected ResponseEntity<ClosedPositionResponse> addManualClosedPosition(
            @Valid @RequestBody ManualClosedPositionRequest request) {
        ClosedPositionResponse addedRow = closedPositionWriteService.addManualClosedPosition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(addedRow);
    }

    @PutMapping(path = "/api/manual-closed-positions/{manualClosedPositionId}",
            consumes = MediaType.APPLICATION_JSON_VALUE)
    protected ResponseEntity<ClosedPositionResponse> changeManualClosedPosition(
            @PathVariable long manualClosedPositionId, @Valid @RequestBody ManualClosedPositionRequest request) {
        ClosedPositionResponse changedRow =
                closedPositionWriteService.changeManualClosedPosition(manualClosedPositionId, request);
        return ResponseEntity.ok(changedRow);
    }

    @DeleteMapping("/api/manual-closed-positions/{manualClosedPositionId}")
    protected ResponseEntity<Void> deleteManualClosedPosition(@PathVariable long manualClosedPositionId) {
        closedPositionWriteService.deleteManualClosedPosition(manualClosedPositionId);
        return ResponseEntity.noContent().build();
    }
}
