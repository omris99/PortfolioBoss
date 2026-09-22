package portfolioboss.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.response.PortfolioResponse;

import java.util.Optional;

/**
 * The local HTTP API the UI reads from: serves the portfolio the last sync stored in the database, as JSON.
 *
 * <p>Read-only by construction — a single GET endpoint; Spring answers 405 to every other method.
 * The server is bound to the loopback interface (see {@code application.properties}).
 */
@RestController
class PortfolioController {

    private final PortfolioReadService portfolioReadService;

    protected PortfolioController(PortfolioReadService portfolioReadService) {
        this.portfolioReadService = portfolioReadService;
    }

    @GetMapping("/api/portfolio")
    protected ResponseEntity<PortfolioResponse> portfolio() {
        Optional<PortfolioResponse> storedPortfolio = portfolioReadService.currentPortfolio();
        if (storedPortfolio.isEmpty()) {
            // Only on the very first run: the web server is already up while TwsPortfolioRunner is still
            // reading TWS (up to ~15s), and nothing has been synced yet.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(storedPortfolio.get());
    }
}
