package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import portfolioboss.api.response.CorrelationResponse;
import portfolioboss.api.response.HotStockResponse;
import portfolioboss.api.response.MomentumResponse;
import portfolioboss.api.response.RecommendationsResponse;
import portfolioboss.model.HotStockLabel;
import portfolioboss.model.MomentumLabel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.empty;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the JSON of {@code GET /api/recommendations} — what the UI of session 4 will read — with a made-up response, so it
 * needs neither TWS nor a database; how the lists are worked out is tested in {@code RecommendationsReadServiceTest}.
 */
@WebMvcTest(RecommendationsController.class)
class RecommendationsControllerTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-10-26T14:00:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RecommendationsReadService recommendationsReadService;

    @Test
    void answers503BeforeTheFirstScan() throws Exception {
        given(recommendationsReadService.currentRecommendations()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void servesTheThreeListsWithTheFieldNamesTheUiWillUse() throws Exception {
        RecommendationsResponse recommendations = new RecommendationsResponse(SCANNED_AT, 3,
                List.of(hotStock()), List.of(), List.of(correctingStock()));
        given(recommendationsReadService.currentRecommendations()).willReturn(Optional.of(recommendations));

        mockMvc.perform(get("/api/recommendations"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.scannedAt").value("2026-10-26T14:00:00Z"))
                .andExpect(jsonPath("$.candidateCount").value(3))
                .andExpect(jsonPath("$.hotStocks[0].candidateId").value(11))
                .andExpect(jsonPath("$.hotStocks[0].conId").value(9001))
                .andExpect(jsonPath("$.hotStocks[0].symbol").value("HOTA"))
                .andExpect(jsonPath("$.hotStocks[0].companyName").value("HOTA INC"))
                .andExpect(jsonPath("$.hotStocks[0].exchange").value("NASDAQ"))
                .andExpect(jsonPath("$.hotStocks[0].industry").value("Technology"))
                .andExpect(jsonPath("$.hotStocks[0].category").value("Software"))
                .andExpect(jsonPath("$.hotStocks[0].label").value("HOT"))
                .andExpect(jsonPath("$.hotStocks[0].momentum.lastClose").value(110.0))
                .andExpect(jsonPath("$.hotStocks[0].momentum.monthsInMarket").value(12))
                .andExpect(jsonPath("$.hotStocks[0].percentAboveSma50").value(6.5))
                .andExpect(jsonPath("$.hotStocks[0].pulledBack").value(true))
                .andExpect(jsonPath("$.hotStocks[0].recovering").value(false))
                .andExpect(jsonPath("$.hotStocks[0].correlations[0].holdingId").value(7))
                .andExpect(jsonPath("$.hotStocks[0].correlations[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.hotStocks[0].correlations[0].coefficient").value(0.82))
                .andExpect(jsonPath("$.hotStocks[0].correlations[0].movesTogether").value(true))
                .andExpect(jsonPath("$.hotStocks[0].heldByInvestorIds[0]").value(1))
                .andExpect(jsonPath("$.tooHotStocks").value(empty()))
                .andExpect(jsonPath("$.correctingStocks[0].label").value("CORRECTING"))
                .andExpect(jsonPath("$.correctingStocks[0].percentAboveSma50").value(-3.0));
    }

    @Test
    void isReadOnly() throws Exception {
        mockMvc.perform(post("/api/recommendations")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/recommendations")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/recommendations")).andExpect(status().isMethodNotAllowed());
    }

    /** 6.5% above its 50-day average, 8% below its year high, moving with a holding, held by investor 1. */
    private HotStockResponse hotStock() {
        return new HotStockResponse(11, 9001, "HOTA", "HOTA INC", "NASDAQ", "Technology", "Software",
                HotStockLabel.HOT, momentumAt(110.0), 6.5, true, false,
                List.of(new CorrelationResponse(7, "AAPL", 0.82, true)), List.of(1L));
    }

    private HotStockResponse correctingStock() {
        return new HotStockResponse(12, 9002, "HOTB", "HOTB CORP", "NYSE", "Industrials", "Machinery",
                HotStockLabel.CORRECTING, momentumAt(97.0), -3.0, false, true, List.of(), List.of());
    }

    private MomentumResponse momentumAt(double lastClose) {
        return new MomentumResponse(LocalDate.of(2026, 10, 23), lastClose, 105.0, 103.0, 90.0, 115.0, 4.0, 2.0,
                true, true, true, true, true, 4.3, 5, MomentumLabel.STRONG,
                LocalDate.of(2025, 10, 24), 120.0, 35.0, 12.0, 12, 8.3);
    }
}
