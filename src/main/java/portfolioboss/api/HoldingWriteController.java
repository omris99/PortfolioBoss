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
import portfolioboss.api.request.SectorRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.TradeResponse;

/**
 * The endpoints that write what the user enters by hand — a holding's sector and its trades — to PortfolioBoss's own
 * database. They never reach Interactive Brokers: the account is still only read, by the sync. Holdings themselves
 * are never created or deleted here; only the sync does that. After a write the UI reloads {@code /api/portfolio}.
 *
 * <p>Every endpoint with a body accepts JSON only ({@code consumes}). A page on another site can make the browser
 * send a request to localhost without asking first only as text or a form, and those get 415. JSON, PUT and DELETE
 * from another site need the browser to ask first (a CORS preflight), and with no CORS configuration the answer
 * is no.
 *
 * <p>{@code @Valid} checks the request's annotations before the method runs; a failure answers 400 without calling
 * it (the message is written by {@link ApiErrorHandler}).
 */
@RestController
class HoldingWriteController {

    private final HoldingWriteService holdingWriteService;

    protected HoldingWriteController(HoldingWriteService holdingWriteService) {
        this.holdingWriteService = holdingWriteService;
    }

    @PutMapping(path = "/api/holdings/{holdingId}/sector", consumes = MediaType.APPLICATION_JSON_VALUE)
    protected ResponseEntity<Void> changeSector(@PathVariable long holdingId,
                                                @Valid @RequestBody SectorRequest sectorRequest) {
        holdingWriteService.changeSector(holdingId, sectorRequest);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/api/holdings/{holdingId}/trades", consumes = MediaType.APPLICATION_JSON_VALUE)
    protected ResponseEntity<TradeResponse> addTrade(@PathVariable long holdingId,
                                                     @Valid @RequestBody TradeRequest tradeRequest) {
        TradeResponse addedTrade = holdingWriteService.addTrade(holdingId, tradeRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(addedTrade);
    }

    @PutMapping(path = "/api/trades/{tradeId}", consumes = MediaType.APPLICATION_JSON_VALUE)
    protected ResponseEntity<TradeResponse> changeTrade(@PathVariable long tradeId,
                                                        @Valid @RequestBody TradeRequest tradeRequest) {
        TradeResponse changedTrade = holdingWriteService.changeTrade(tradeId, tradeRequest);
        return ResponseEntity.ok(changedTrade);
    }

    @DeleteMapping("/api/trades/{tradeId}")
    protected ResponseEntity<Void> deleteTrade(@PathVariable long tradeId) {
        holdingWriteService.deleteTrade(tradeId);
        return ResponseEntity.noContent().build();
    }
}
