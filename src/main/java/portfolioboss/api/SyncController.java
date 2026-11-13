package portfolioboss.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.request.SyncRequest;
import portfolioboss.api.response.SyncRunResponse;

/**
 * {@code POST /api/syncs}: the Sync now button. The second endpoint that reads TWS, after {@code POST /api/scans} — the
 * startup's reading, read-only, through the same {@code TwsReader} — and, like the startup, it asks Finnhub about the
 * holdings in dollars (their symbols only). It writes only what a sync writes. JSON only, like the other writes
 * ({@code consumes}): a page on another site could otherwise start one with a plain form.
 */
@RestController
class SyncController {

    private final SyncService syncService;

    private SyncController(SyncService syncService) {
        this.syncService = syncService;
    }

    /** {@code request} says nothing: it is there so that only a JSON request is accepted (see {@link SyncRequest}). */
    @PostMapping(path = "/api/syncs", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<SyncRunResponse> syncNow(@RequestBody SyncRequest request) throws InterruptedException {
        return ResponseEntity.ok(syncService.syncNow());
    }
}
