package portfolioboss.api.recommendations;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;
import portfolioboss.recommendations.ScanService;

import java.time.Instant;
import java.util.List;
import java.util.Map;

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
        given(scanService.scanAgain()).willReturn(scanOfTwoStocks());

        postScan("{}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scannedAt").value("2026-10-26T21:05:00Z"))
                .andExpect(jsonPath("$.candidateCount").value(2));
    }

    /** Two made-up stocks on NASDAQ and NYSE, both kept: only when and how many reach the answer. */
    private Scan scanOfTwoStocks() {
        return new Scan(Instant.parse("2026-10-26T21:05:00Z"),
                List.of(new ScanRow("SCAN_histPerfRatio3m_DESC", 0, 1, "HOTA"),
                        new ScanRow("SCAN_histPerfRatio3m_DESC", 1, 2, "HOTB")),
                Map.of(1, new StockContractDetails(1, "NASDAQ", "HOTA INC", "Technology", "Software"),
                        2, new StockContractDetails(2, "NYSE", "HOTB CORP", "Industrials", "Machinery")));
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

    /** The user's message is the answer's {@code detail}; the console's reason is not sent. */
    @Test
    void answers503WhenTwsCantBeRead() throws Exception {
        given(scanService.scanAgain()).willThrow(new TwsUnavailableException(
                "TWS is not reachable: open TWS and log in, then try again.",
                "Connection was refused by TWS. Check the port and that API access is enabled."));

        postScan("{}")
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("TWS is not reachable: open TWS and log in, then try again."));
    }

    @Test
    void answers409WhileAnotherReadingRuns() throws Exception {
        given(scanService.scanAgain()).willThrow(new TwsBusyException(
                "TWS is already being read — by a sync, a scan or the startup: try again in a moment."));

        postScan("{}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("TWS is already being read — by a sync, a scan or the startup: try again in a moment."));
    }

    @Test
    void acceptsOnlyPost() throws Exception {
        mockMvc.perform(get("/api/scans")).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions postScan(String body) throws Exception {
        return mockMvc.perform(post("/api/scans").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
