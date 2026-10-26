package portfolioboss.api;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.request.ScanRequest;
import portfolioboss.api.response.ScanRunResponse;

/**
 * {@code POST /api/scans}: the Scan again button. The one endpoint that reads TWS — a scan, read-only, through the same
 * {@code TwsReader} as {@code ./run.sh} — and it writes only {@code scan} rows and their stocks' daily closes. JSON
 * only, like the other writes ({@code consumes}): a page on another site could otherwise start one with a plain form.
 */
@RestController
class ScanController {

    private final ScanService scanService;

    private ScanController(ScanService scanService) {
        this.scanService = scanService;
    }

    /** {@code request} says nothing: it is there so that only a JSON request is accepted (see {@link ScanRequest}). */
    @PostMapping(path = "/api/scans", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<ScanRunResponse> scanAgain(@RequestBody ScanRequest request) throws InterruptedException {
        return ResponseEntity.ok(scanService.scanAgain());
    }
}
