package portfolioboss.api;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.request.RecommendationAnalysisRequest;
import portfolioboss.api.response.RecommendationAnalysisRunResponse;

/**
 * {@code POST /api/recommendations/analysis}: analyzes the Recommendations page's short list — or one stock of its
 * lists — with Tavily, Claude and Finnhub, and after the short list picks the recommendation (RECOMMENDATIONS_TODO.md,
 * session 3). Like {@code POST /api/analysis} it reaches outside the machine — Tavily and Finnhub get the symbol, Claude
 * the symbol, currency, last close and search results — and it writes only {@code stock_analysis} rows, never anything
 * at Interactive Brokers. JSON only ({@code consumes}): a page on another site could otherwise start a paid run with a
 * plain form. Its own controller, so that {@code RecommendationsController} stays GET-only.
 */
@RestController
class RecommendationAnalysisController {

    private final RecommendationAnalysisService recommendationAnalysisService;

    private RecommendationAnalysisController(RecommendationAnalysisService recommendationAnalysisService) {
        this.recommendationAnalysisService = recommendationAnalysisService;
    }

    @PostMapping(path = "/api/recommendations/analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<RecommendationAnalysisRunResponse> analyze(
            @Valid @RequestBody RecommendationAnalysisRequest request) {
        return ResponseEntity.ok(recommendationAnalysisService.analyze(request.candidateId()));
    }
}
