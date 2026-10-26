package portfolioboss.api;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.response.RecommendationsResponse;

import java.util.Optional;

/**
 * Serves the Recommendations page the latest scan the sync stored, as JSON (RECOMMENDATIONS_TODO.md, session 1).
 *
 * <p>Read-only by construction — a single GET endpoint; Spring answers 405 to every other method.
 */
@RestController
class RecommendationsController {

    private final RecommendationsReadService recommendationsReadService;

    private RecommendationsController(RecommendationsReadService recommendationsReadService) {
        this.recommendationsReadService = recommendationsReadService;
    }

    @GetMapping("/api/recommendations")
    private ResponseEntity<RecommendationsResponse> recommendations() {
        Optional<RecommendationsResponse> latestScan = recommendationsReadService.currentRecommendations();
        if (latestScan.isEmpty()) {
            // Before the first scan: ./run.sh has not scanned yet, or no scan has ever answered.
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(latestScan.get());
    }
}
