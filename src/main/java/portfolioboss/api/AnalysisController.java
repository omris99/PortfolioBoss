package portfolioboss.api;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import portfolioboss.api.request.AnalysisRequest;
import portfolioboss.api.response.AnalysisRunResponse;

/**
 * {@code POST /api/analysis}: analyzes holdings with Tavily and Claude (AI_ANALYSIS_TODO.md, session 2) and answers
 * with what the run did and cost. The one endpoint that reaches outside the machine — it sends each stock's symbol to
 * Tavily, and the symbol, currency, IB price and search results to Claude — and it writes only {@code stock_analysis}
 * rows, never anything at Interactive Brokers. JSON only, like the other writes ({@code consumes}): a page on another
 * site could otherwise start a paid run with a plain form (decision 13).
 */
@RestController
class AnalysisController {

    private final AnalysisService analysisService;

    private AnalysisController(AnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping(path = "/api/analysis", consumes = MediaType.APPLICATION_JSON_VALUE)
    private ResponseEntity<AnalysisRunResponse> analyze(@Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.ok(analysisService.analyze(request.holdingIds()));
    }
}
