package portfolioboss.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.model.PortfolioSnapshot;

import java.util.Optional;

/**
 * The local HTTP API the UI reads from: serves the portfolio snapshot as JSON.
 *
 * <p>Read-only by construction — a single GET endpoint; Spring answers 405 to every other method.
 * The server is bound to the loopback interface (see {@code application.properties}).
 */
@RestController
class PortfolioController {

    private final SnapshotStore snapshotStore;

    PortfolioController(SnapshotStore snapshotStore) {
        this.snapshotStore = snapshotStore;
    }

    @GetMapping("/api/portfolio")
    ResponseEntity<PortfolioResponse> portfolio() {
        Optional<PortfolioSnapshot> snapshot = snapshotStore.current();
        if (snapshot.isEmpty()) {
            // The web server is already up while Main is still reading TWS (up to ~15s).
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(PortfolioResponse.from(snapshot.get()));
    }
}
