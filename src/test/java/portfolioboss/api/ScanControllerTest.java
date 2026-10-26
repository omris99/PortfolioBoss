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
import portfolioboss.api.response.ScanRunResponse;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of {@code POST /api/scans}: status codes, the answer's JSON, and the JSON-only rule that keeps another
 * site from starting a scan. {@link ScanService} is replaced by a stand-in; what a scan stores is tested in
 * {@code ScanServiceTest}.
 */
@WebMvcTest(ScanController.class)
class ScanControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScanService scanService;

    @Test
    void scansAndAnswers200WithWhenAndHowManyStocks() throws Exception {
        given(scanService.scanAgain()).willReturn(new ScanRunResponse(Instant.parse("2026-10-26T21:05:00Z"), 68));

        postScan("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedAt").value("2026-10-26T21:05:00Z"))
                .andExpect(jsonPath("$.candidateCount").value(68));
    }

    /** A plain form, or text, is what a page on another site can send without asking: never a scan. */
    @Test
    void refusesAFormOrTextWith415() throws Exception {
        mockMvc.perform(post("/api/scans").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("scan=1"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/scans").header("Origin", "https://evil.example")
                        .contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(scanService);
    }

    @Test
    void answers503WhenTwsCantBeRead() throws Exception {
        given(scanService.scanAgain()).willThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "TWS is not reachable: open TWS and log in, then scan again."));

        postScan("{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("TWS is not reachable: open TWS and log in, then scan again."));
    }

    @Test
    void answers409WhileAnotherReadingRuns() throws Exception {
        given(scanService.scanAgain()).willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "A scan is already running, or the startup is still reading TWS: try again in a moment."));

        postScan("{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("A scan is already running, or the startup is still reading TWS: try again in a moment."));
    }

    @Test
    void acceptsOnlyPost() throws Exception {
        mockMvc.perform(get("/api/scans")).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions postScan(String body) throws Exception {
        return mockMvc.perform(post("/api/scans").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
