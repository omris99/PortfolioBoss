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
import portfolioboss.api.request.ManualPositionRequest;
import portfolioboss.api.request.NewManualPositionRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.ManualPositionResponse;
import portfolioboss.api.response.TradeResponse;

/**
 * The endpoints that write the manual positions — sold before PortfolioBoss saw them — to PortfolioBoss's own database.
 * Like {@link HoldingWriteController} they never reach Interactive Brokers, accept JSON only ({@code consumes}, for the
 * same reason: a page on another site can't send JSON to localhost without a CORS permission nobody grants), and are
 * checked by {@code @Valid} before the method runs. A manual position's trades are corrected or deleted through
 * {@code /api/trades/{id}}, like a holding's. After a write the UI reloads {@code /api/portfolio}.
 */
@RestController
class ManualPositionWriteController {

    private final ManualPositionWriteService manualPositionWriteService;

    private ManualPositionWriteController(ManualPositionWriteService manualPositionWriteService) {
        this.manualPositionWriteService = manualPositionWriteService;
    }

    @PostMapping(path = "/api/manual-positions", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<ManualPositionResponse> addManualPosition(
            @Valid @RequestBody NewManualPositionRequest request) {
        ManualPositionResponse addedPosition = manualPositionWriteService.addManualPosition(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(addedPosition);
    }

    @PutMapping(path = "/api/manual-positions/{manualPositionId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<Void> changeManualPosition(@PathVariable long manualPositionId,
                                                      @Valid @RequestBody ManualPositionRequest request) {
        manualPositionWriteService.changeManualPosition(manualPositionId, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/manual-positions/{manualPositionId}")
    private ResponseEntity<Void> deleteManualPosition(@PathVariable long manualPositionId) {
        manualPositionWriteService.deleteManualPosition(manualPositionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/api/manual-positions/{manualPositionId}/trades", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<TradeResponse> addTrade(@PathVariable long manualPositionId,
                                                   @Valid @RequestBody TradeRequest tradeRequest) {
        TradeResponse addedTrade = manualPositionWriteService.addTrade(manualPositionId, tradeRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(addedTrade);
    }
}
