package portfolioboss.alerts;

import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.alerts.request.AlertSettingsRequest;
import portfolioboss.alerts.request.AlertsSeenRequest;
import portfolioboss.alerts.response.AlertsResponse;

/**
 * The bell and its settings drawer: {@code GET /api/alerts}, {@code POST /api/alerts/seen} and {@code PUT
 * /api/alerts/settings}. The two writes touch only PortfolioBoss's own tables, and accept JSON only ({@code consumes}),
 * like every other write: a page on another site could otherwise send them with a plain form.
 */
@RestController
class AlertController {

    private final AlertService alertService;

    private AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    /** {@code no-store}, like {@code /api/portfolio}: the bell must never show an answer the browser kept. */
    @GetMapping("/api/alerts")
    private ResponseEntity<AlertsResponse> currentAlerts() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(alertService.currentAlerts());
    }

    /** {@code request} says nothing: it is there so that only a JSON request is accepted. */
    @PostMapping(path = "/api/alerts/seen", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<Void> markAllAsSeen(@RequestBody AlertsSeenRequest request) {
        alertService.markAllAsSeen();
        return ResponseEntity.noContent().build();
    }

    /** 400 with the fields named ({@code ApiErrorHandler}) when a line is wrong. */
    @PutMapping(path = "/api/alerts/settings", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<Void> saveSettings(@Valid @RequestBody AlertSettingsRequest request) {
        alertService.saveSettings(request);
        return ResponseEntity.noContent().build();
    }
}
