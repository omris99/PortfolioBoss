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
import portfolioboss.api.response.RecommendationAnalysisRunResponse;
import portfolioboss.api.response.RecommendationPickResponse;
import portfolioboss.model.RiskSign;

import java.math.BigDecimal;
import java.util.List;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of {@code POST /api/recommendations/analysis}: status codes, the answer's JSON, and the JSON-only rule
 * that keeps another site from starting a paid run. {@link RecommendationAnalysisService} is replaced by a stand-in;
 * what a run does is tested in {@code RecommendationAnalysisServiceTest}.
 */
@WebMvcTest(RecommendationAnalysisController.class)
class RecommendationAnalysisControllerTest {

    private static final AnalysisRunResponse TEN_ANALYZED = new AnalysisRunResponse(9,
            List.of(new FailedAnalysisResponse("ABCD", "Claude declined to answer")), 45, 94_500, 8_100,
            new BigDecimal("0.270000"));

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationAnalysisService recommendationAnalysisService;

    /** {@code {}} is how the page asks for the short list and the recommendation. */
    @Test
    void anEmptyBodyAnalyzesTheShortListAndAnswersWithThePicks() throws Exception {
        given(recommendationAnalysisService.analyze(null)).willReturn(new RecommendationAnalysisRunResponse(
                TEN_ANALYZED, List.of(new RecommendationPickResponse(41, "WXYZ", List.of(), false, 3, 3, 27.1),
                        new RecommendationPickResponse(43, "EFGH", List.of(RiskSign.LOSES_MONEY), true, 0, -1, 18.2))));

        postAnalysis("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.analyzed").value(9))
                .andExpect(jsonPath("$.analysis.failed[0].symbol").value("ABCD"))
                .andExpect(jsonPath("$.analysis.failed[0].message").value("Claude declined to answer"))
                .andExpect(jsonPath("$.analysis.tavilyCredits").value(45))
                .andExpect(jsonPath("$.analysis.inputTokens").value(94_500))
                .andExpect(jsonPath("$.analysis.outputTokens").value(8_100))
                .andExpect(jsonPath("$.analysis.costUsd").value(0.27))
                .andExpect(jsonPath("$.picks[0].candidateId").value(41))
                .andExpect(jsonPath("$.picks[0].symbol").value("WXYZ"))
                .andExpect(jsonPath("$.picks[0].riskSigns").isEmpty())
                .andExpect(jsonPath("$.picks[0].reportsWithin14Days").value(false))
                .andExpect(jsonPath("$.picks[0].newsScore").value(3))
                .andExpect(jsonPath("$.picks[0].score").value(3))
                .andExpect(jsonPath("$.picks[0].targetUpsidePercent").value(27.1))
                .andExpect(jsonPath("$.picks[1].riskSigns[0]").value("LOSES_MONEY"))
                .andExpect(jsonPath("$.picks[1].reportsWithin14Days").value(true))
                .andExpect(jsonPath("$.picks[1].score").value(-1));
        then(recommendationAnalysisService).should().analyze(null);
    }

    /** One candidate's analysis makes no recommendation: {@code picks} is {@code null}. */
    @Test
    void aCandidateIdAnalyzesThatCandidateAlone() throws Exception {
        given(recommendationAnalysisService.analyze(7L)).willReturn(new RecommendationAnalysisRunResponse(
                new AnalysisRunResponse(1, List.of(), 5, 10_500, 900, new BigDecimal("0.030000")), null));

        postAnalysis("""
                {"candidateId": 7}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.analysis.analyzed").value(1))
                .andExpect(jsonPath("$.picks").value(nullValue()));
        then(recommendationAnalysisService).should().analyze(7L);
    }

    /** A plain form, or text, is what a page on another site can send without asking: never a paid run. */
    @Test
    void refusesAFormOrTextWith415() throws Exception {
        mockMvc.perform(post("/api/recommendations/analysis").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("candidateId=7"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/recommendations/analysis").header("Origin", "https://evil.example")
                        .contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(recommendationAnalysisService);
    }

    @Test
    void answers503WithTheMissingKey() throws Exception {
        String reason = "The recommendation is off: FINNHUB_API_KEY is not set in config/local.env, and without the "
                + "company figures no stock can be picked";
        given(recommendationAnalysisService.analyze(any()))
                .willThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, reason));

        postAnalysis("{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(reason));
    }

    @Test
    void answers404ForACandidateNotInTheLists() throws Exception {
        given(recommendationAnalysisService.analyze(999L)).willThrow(new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No candidate with id 999 in the latest scan's lists"));

        postAnalysis("""
                {"candidateId": 999}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No candidate with id 999 in the latest scan's lists"));
    }

    @Test
    void acceptsOnlyPost() throws Exception {
        mockMvc.perform(get("/api/recommendations/analysis")).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions postAnalysis(String body) throws Exception {
        return mockMvc.perform(post("/api/recommendations/analysis").contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
