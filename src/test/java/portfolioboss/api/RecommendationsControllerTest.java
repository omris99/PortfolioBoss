package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import portfolioboss.ai.AnalystAction;
import portfolioboss.ai.AnalystActionType;
import portfolioboss.ai.EventMagnitude;
import portfolioboss.ai.MoveReason;
import portfolioboss.ai.NewsEvent;
import portfolioboss.ai.NewsEventType;
import portfolioboss.ai.Sentiment;
import portfolioboss.api.response.CorrelationResponse;
import portfolioboss.api.response.HotStockResponse;
import portfolioboss.api.response.LatestRecommendationResponse;
import portfolioboss.api.response.MomentumResponse;
import portfolioboss.api.response.PickedStockResponse;
import portfolioboss.api.response.RecommendationsResponse;
import portfolioboss.api.response.StockAnalysisResponse;
import portfolioboss.model.HotStockLabel;
import portfolioboss.model.MomentumLabel;
import portfolioboss.model.RiskSign;
import portfolioboss.model.WeighedAnalystMove;
import portfolioboss.model.WeighedNewsEvent;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.nullValue;
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
        LatestRecommendationResponse latestRecommendation = new LatestRecommendationResponse(
                Instant.parse("2026-10-26T18:30:00Z"), Instant.parse("2026-10-25T21:30:00Z"),
                List.of(new PickedStockResponse(8, 9001, "HOTA")));
        RecommendationsResponse recommendations = new RecommendationsResponse(SCANNED_AT, 3,
                List.of(hotStock()), List.of(), List.of(correctingStock()), latestRecommendation);
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
                .andExpect(jsonPath("$.hotStocks[0].analysis.analyzedAt").value("2026-10-26T18:30:00Z"))
                .andExpect(jsonPath("$.hotStocks[0].analysis.sentiment").value("POSITIVE"))
                .andExpect(jsonPath("$.hotStocks[0].analysis.moveReason.reason")
                        .value("A new contract lifted the shares."))
                .andExpect(jsonPath("$.hotStocks[0].nextEarningsDate.date").value("2026-11-05"))
                .andExpect(jsonPath("$.hotStocks[0].nextEarningsDate.hour").value("amc"))
                .andExpect(jsonPath("$.hotStocks[0].companyFacts.revenueGrowthPercent").value(-4.5))
                .andExpect(jsonPath("$.hotStocks[0].companyFacts.netMarginPercent").value(12.0))
                .andExpect(jsonPath("$.hotStocks[0].companyFacts.debtToEquity").value(0.6))
                .andExpect(jsonPath("$.hotStocks[0].companyFacts.peRatio").value(38.0))
                .andExpect(jsonPath("$.hotStocks[0].riskSigns[0]").value("REVENUE_FALLING"))
                .andExpect(jsonPath("$.hotStocks[0].reportsWithin14Days").value(true))
                .andExpect(jsonPath("$.hotStocks[0].newsScore").value(0))
                .andExpect(jsonPath("$.hotStocks[0].analysis.newsEvents[0].type").value("MAJOR_CONTRACT"))
                .andExpect(jsonPath("$.hotStocks[0].analysis.newsEvents[0].magnitude").value("MEDIUM"))
                .andExpect(jsonPath("$.hotStocks[0].analysis.newsEvents[0].title").value("HOTA wins a contract"))
                .andExpect(jsonPath("$.hotStocks[0].weighedNewsEvents[0].event.type").value("MAJOR_CONTRACT"))
                .andExpect(jsonPath("$.hotStocks[0].weighedNewsEvents[0].points").value(2))
                .andExpect(jsonPath("$.hotStocks[0].countedAnalystMoves[0].action.firm").value("Some Firm"))
                .andExpect(jsonPath("$.hotStocks[0].countedAnalystMoves[0].action.action").value("DOWNGRADE"))
                .andExpect(jsonPath("$.hotStocks[0].countedAnalystMoves[0].points").value(-2))
                .andExpect(jsonPath("$.hotStocks[0].riskWeight").value(3))
                .andExpect(jsonPath("$.hotStocks[0].score").value(-3))
                .andExpect(jsonPath("$.hotStocks[0].canBePicked").value(false))
                .andExpect(jsonPath("$.tooHotStocks").value(empty()))
                .andExpect(jsonPath("$.correctingStocks[0].label").value("CORRECTING"))
                .andExpect(jsonPath("$.correctingStocks[0].percentAboveSma50").value(-3.0))
                .andExpect(jsonPath("$.correctingStocks[0].analysis").value(nullValue()))
                .andExpect(jsonPath("$.correctingStocks[0].riskSigns").value(nullValue()))
                .andExpect(jsonPath("$.correctingStocks[0].reportsWithin14Days").value(false))
                .andExpect(jsonPath("$.correctingStocks[0].newsScore").value(nullValue()))
                .andExpect(jsonPath("$.correctingStocks[0].weighedNewsEvents").value(empty()))
                .andExpect(jsonPath("$.correctingStocks[0].countedAnalystMoves").value(empty()))
                .andExpect(jsonPath("$.correctingStocks[0].riskWeight").value(nullValue()))
                .andExpect(jsonPath("$.correctingStocks[0].score").value(nullValue()))
                .andExpect(jsonPath("$.correctingStocks[0].canBePicked").value(false))
                .andExpect(jsonPath("$.latestRecommendation.recommendedAt").value("2026-10-26T18:30:00Z"))
                .andExpect(jsonPath("$.latestRecommendation.scannedAt").value("2026-10-25T21:30:00Z"))
                .andExpect(jsonPath("$.latestRecommendation.picks[0].candidateId").value(8))
                .andExpect(jsonPath("$.latestRecommendation.picks[0].conId").value(9001))
                .andExpect(jsonPath("$.latestRecommendation.picks[0].symbol").value("HOTA"));
    }

    @Test
    void isReadOnly() throws Exception {
        mockMvc.perform(post("/api/recommendations")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/recommendations")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/recommendations")).andExpect(status().isMethodNotAllowed());
    }

    /**
     * 6.5% above its 50-day average, 8% below its year high, moving with a holding, held by investor 1 — analyzed, with
     * falling revenue (3, over the limit) and a report in ten days; a contract (+2) and a downgrade (−2).
     */
    private HotStockResponse hotStock() {
        StockAnalysisResponse analysis = analysis();
        AnalystAction downgrade = new AnalystAction(LocalDate.of(2026, 10, 20), "Some Firm",
                AnalystActionType.DOWNGRADE, "Buy", "Hold", 130.0, 115.0, "https://example.com/hota-downgrade", null);
        return new HotStockResponse(11, 9001, "HOTA", "HOTA INC", "NASDAQ", "Technology", "Software",
                HotStockLabel.HOT, momentumAt(110.0), 6.5, true, false,
                List.of(new CorrelationResponse(7, "AAPL", 0.82, true)), List.of(1L), analysis,
                new EarningsDate(LocalDate.of(2026, 11, 5), "amc"), new CompanyFacts(-4.5, 12.0, 0.6, 38.0),
                List.of(RiskSign.REVENUE_FALLING), true, 0,
                List.of(new WeighedNewsEvent(analysis.newsEvents().getFirst(), 2)),
                List.of(new WeighedAnalystMove(downgrade, -2)), 3, -3, false);
    }

    /** Never analyzed. */
    private HotStockResponse correctingStock() {
        return new HotStockResponse(12, 9002, "HOTB", "HOTB CORP", "NYSE", "Industrials", "Machinery",
                HotStockLabel.CORRECTING, momentumAt(97.0), -3.0, false, true, List.of(), List.of(), null, null, null,
                null, false, null, List.of(), List.of(), null, null, false);
    }

    /** Its shape is the holdings', pinned in {@code PortfolioControllerTest}; here only that it is there. */
    private StockAnalysisResponse analysis() {
        return new StockAnalysisResponse(Instant.parse("2026-10-26T18:30:00Z"), "claude-sonnet-5-5", null, List.of(),
                null, List.of(), List.of(), Sentiment.POSITIVE, "A new contract.", 0, 0,
                new MoveReason("A new contract lifted the shares.", "HOTA wins a contract", "https://example.com/hota"),
                List.of(new NewsEvent(NewsEventType.MAJOR_CONTRACT, EventMagnitude.MEDIUM,
                        "HOTA won a contract with a well-known carrier.", "HOTA wins a contract",
                        "https://example.com/hota")));
    }

    private MomentumResponse momentumAt(double lastClose) {
        return new MomentumResponse(LocalDate.of(2026, 10, 23), lastClose, 105.0, 103.0, 90.0, 115.0, 4.0, 2.0,
                true, true, true, true, true, 4.3, 5, MomentumLabel.STRONG,
                LocalDate.of(2025, 10, 24), 120.0, 35.0, 12.0, 12, 8.3);
    }
}
