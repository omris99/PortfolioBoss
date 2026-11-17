package portfolioboss.api.analysis;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import portfolioboss.ai.ClaudeReply;
import portfolioboss.ai.SearchResults;
import portfolioboss.ai.StockAnalysisOutcome;
import portfolioboss.ai.StockAnalysisRun;
import portfolioboss.ai.StockSearches;
import portfolioboss.analysis.AnalysisService;
import portfolioboss.exceptions.AnalysisOffException;
import portfolioboss.exceptions.HoldingIsClosedException;
import portfolioboss.exceptions.HoldingNotFoundException;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of {@code POST /api/analysis}: status codes, the summary's JSON, and the JSON-only rule that keeps
 * another site from starting a paid run (AI_ANALYSIS_TODO.md, decision 13). {@link AnalysisService} is replaced by a
 * stand-in; what a run does is tested in {@code AnalysisServiceTest}.
 */
@WebMvcTest(AnalysisController.class)
class AnalysisControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AnalysisService analysisService;

    @Test
    void runsTheAnalysisAndAnswers200WithWhatItDidAndCost() throws Exception {
        given(analysisService.analyze(List.of(7L, 8L))).willReturn(runOfOneAnalyzedAndOneFailed());

        postAnalysis("""
                {"holdingIds": [7, 8]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzed").value(1))
                .andExpect(jsonPath("$.failed[0].symbol").value("AEVA"))
                .andExpect(jsonPath("$.failed[0].message").value("Claude declined to answer"))
                .andExpect(jsonPath("$.tavilyCredits").value(5))
                .andExpect(jsonPath("$.inputTokens").value(21_000))
                .andExpect(jsonPath("$.outputTokens").value(1_800))
                .andExpect(jsonPath("$.costUsd").value(0.06));
    }

    /**
     * A made-up run: AAPL analyzed — 5 credits, 21,000 and 1,800 tokens, $0.06 — and AEVA failed. The answer needs
     * none of Claude's result, so there is none.
     */
    private StockAnalysisRun runOfOneAnalyzedAndOneFailed() {
        StockSearches searchesOfFiveCredits = new StockSearches(new SearchResults(List.of(), 2),
                new SearchResults(List.of(), 1), new SearchResults(List.of(), 1), new SearchResults(List.of(), 1));
        ClaudeReply reply = new ClaudeReply(null, "claude-sonnet-5-5", 21_000, 1_800, new BigDecimal("0.060000"));
        return new StockAnalysisRun(List.of(
                new StockAnalysisOutcome("AAPL", reply, searchesOfFiveCredits, null),
                new StockAnalysisOutcome("AEVA", null, null, "Claude declined to answer")));
    }

    /** {@code {}} is how the UI asks for every open holding. */
    @Test
    void anEmptyBodyAnalyzesEveryOpenHolding() throws Exception {
        given(analysisService.analyze(any())).willReturn(new StockAnalysisRun(List.of()));

        postAnalysis("{}").andExpect(status().isOk());

        then(analysisService).should().analyze(null);
    }

    @Test
    void rejectsAMissingId() throws Exception {
        postAnalysis("""
                {"holdingIds": [7, null]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("holdingIds[1]: must not be null"));
        verifyNoInteractions(analysisService);
    }

    /** A plain form, or text, is what a page on another site can send without asking: never a paid run. */
    @Test
    void refusesAFormOrTextWith415() throws Exception {
        mockMvc.perform(post("/api/analysis").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("holdingIds=7"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/analysis").header("Origin", "https://evil.example")
                        .contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(analysisService);
    }

    @Test
    void answers503WithTheMissingKey() throws Exception {
        given(analysisService.analyze(any())).willThrow(new AnalysisOffException(
                "The analysis is off: ANTHROPIC_API_KEY is not set in config/local.env"));

        postAnalysis("{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("The analysis is off: ANTHROPIC_API_KEY is not set in config/local.env"));
    }

    @Test
    void answers404ForAnUnknownHoldingAnd400ForAClosedOne() throws Exception {
        given(analysisService.analyze(List.of(999L))).willThrow(new HoldingNotFoundException(999L));
        given(analysisService.analyze(List.of(9L))).willThrow(new HoldingIsClosedException("INTC"));

        postAnalysis("""
                {"holdingIds": [999]}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No holding with id 999"));
        postAnalysis("""
                {"holdingIds": [9]}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("INTC is closed: only open holdings are analyzed"));
    }

    @Test
    void acceptsOnlyPost() throws Exception {
        mockMvc.perform(get("/api/analysis")).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions postAnalysis(String body) throws Exception {
        return mockMvc.perform(post("/api/analysis").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
