package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.response.AnalysisRunResponse;
import portfolioboss.api.response.FailedAnalysisResponse;

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
        AnalysisRunResponse summary = new AnalysisRunResponse(6,
                List.of(new FailedAnalysisResponse("AEVA", "Claude declined to answer")), 18, 63_000, 5_400,
                new BigDecimal("0.180000"));
        given(analysisService.analyze(List.of(7L, 8L))).willReturn(summary);

        postAnalysis("""
                {"holdingIds": [7, 8]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analyzed").value(6))
                .andExpect(jsonPath("$.failed[0].symbol").value("AEVA"))
                .andExpect(jsonPath("$.failed[0].message").value("Claude declined to answer"))
                .andExpect(jsonPath("$.tavilyCredits").value(18))
                .andExpect(jsonPath("$.inputTokens").value(63_000))
                .andExpect(jsonPath("$.outputTokens").value(5_400))
                .andExpect(jsonPath("$.costUsd").value(0.18));
    }

    /** {@code {}} is how the UI asks for every open holding. */
    @Test
    void anEmptyBodyAnalyzesEveryOpenHolding() throws Exception {
        given(analysisService.analyze(any())).willReturn(new AnalysisRunResponse(0, List.of(), 0, 0, 0,
                BigDecimal.ZERO));

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
        given(analysisService.analyze(any())).willThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "The analysis is off: ANTHROPIC_API_KEY is not set in config/local.env"));

        postAnalysis("{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail")
                        .value("The analysis is off: ANTHROPIC_API_KEY is not set in config/local.env"));
    }

    @Test
    void answers404ForAnUnknownHoldingAnd400ForAClosedOne() throws Exception {
        given(analysisService.analyze(List.of(999L)))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No holding with id 999"));
        given(analysisService.analyze(List.of(9L))).willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "INTC is closed: only open holdings are analyzed"));

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
