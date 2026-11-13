package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import portfolioboss.api.response.SyncRunResponse;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;

import java.time.Instant;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of {@code POST /api/syncs}: status codes, the answer's JSON, and the JSON-only rule that keeps another
 * site from starting a sync. {@link SyncService} is replaced by a stand-in; what a sync stores is tested in
 * {@code SyncServiceTest}.
 */
@WebMvcTest(SyncController.class)
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SyncService syncService;

    @Test
    void syncsAndAnswers200WithWhenAndHowManyHoldings() throws Exception {
        given(syncService.syncNow()).willReturn(new SyncRunResponse(Instant.parse("2026-11-11T21:40:00Z"), 8));

        postSync("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncedAt").value("2026-11-11T21:40:00Z"))
                .andExpect(jsonPath("$.holdingCount").value(8));
    }

    /** A plain form, or text, is what a page on another site can send without asking: never a sync. */
    @Test
    void refusesAFormOrTextWith415() throws Exception {
        mockMvc.perform(post("/api/syncs").header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED).content("sync=1"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(post("/api/syncs").header("Origin", "https://evil.example")
                        .contentType(MediaType.TEXT_PLAIN).content("{}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(syncService);
    }

    /** The user's message is the answer's {@code detail}; the console's reason is not sent. */
    @Test
    void answers503WhenTwsCantBeRead() throws Exception {
        given(syncService.syncNow()).willThrow(new TwsUnavailableException(
                "TWS is not reachable: open TWS and log in, then try again.",
                "Connection was refused by TWS. Check the port and that API access is enabled."));

        postSync("{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("TWS is not reachable: open TWS and log in, then try again."));
    }

    @Test
    void answers409WhileAnotherReadingRuns() throws Exception {
        given(syncService.syncNow()).willThrow(new TwsBusyException(
                "TWS is already being read — by a sync, a scan or the startup: try again in a moment."));

        postSync("{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("TWS is already being read — by a sync, a scan or the startup: try again in a moment."));
    }

    @Test
    void acceptsOnlyPost() throws Exception {
        mockMvc.perform(get("/api/syncs")).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions postSync(String body) throws Exception {
        return mockMvc.perform(post("/api/syncs").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
