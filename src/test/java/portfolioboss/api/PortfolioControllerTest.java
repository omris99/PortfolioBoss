package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import portfolioboss.api.response.HoldingResponse;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.db.HoldingStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.closeTo;
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
 * Pins the JSON contract the UI depends on ({@code ui/src/types/portfolio.ts}) using made-up responses, so it
 * needs neither TWS nor a database. {@code @WebMvcTest} starts only Spring's web layer; {@code MockMvc} sends
 * it fake HTTP requests without a real server; {@code @MockitoBean} replaces {@link PortfolioReadService} with
 * a stand-in whose answers each test chooses. The path from the database to the response is tested in
 * {@code PortfolioReadServiceTest}.
 */
@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    private static final String ACCOUNT = "U1234567";
    private static final Instant AS_OF = Instant.parse("2026-09-19T08:05:00Z");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PortfolioReadService portfolioReadService;

    @Test
    void answers503WhenNothingHasBeenSyncedYet() throws Exception {
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void servesThePortfolioWithTheFieldNamesTheUiExpects() throws Exception {
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(portfolioWith(apple())));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.account").value(ACCOUNT))
                .andExpect(jsonPath("$.netLiquidation").value(100_000.0))
                .andExpect(jsonPath("$.totalCashValue").value(25_000.0))
                .andExpect(jsonPath("$.holdings[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.holdings[0].secType").value("STK"))
                .andExpect(jsonPath("$.holdings[0].currency").value("USD"))
                .andExpect(jsonPath("$.holdings[0].position").value(10.0))
                .andExpect(jsonPath("$.holdings[0].averageCost").value(150.0))
                .andExpect(jsonPath("$.holdings[0].marketPrice").value(200.0))
                .andExpect(jsonPath("$.holdings[0].marketValue").value(2000.0))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnl").value(500.0))
                .andExpect(jsonPath("$.holdings[0].realizedPnl").value(25.0))
                .andExpect(jsonPath("$.holdings[0].account").value(ACCOUNT))
                .andExpect(jsonPath("$.holdings[0].costBasis").value(1500.0))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnlPercent").value(closeTo(33.333, 0.001)))
                .andExpect(jsonPath("$.holdings[0].id").value(7))
                .andExpect(jsonPath("$.holdings[0].conId").value(265598))
                .andExpect(jsonPath("$.holdings[0].sector").value("Technology"))
                .andExpect(jsonPath("$.holdings[0].status").value("OPEN"));
    }

    @Test
    void writesAsOfAsAnIsoString() throws Exception {
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(portfolioWith()));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.asOf").value("2026-09-19T08:05:00Z"));
    }

    @Test
    void keepsFiguresIbDidNotReportInTheJsonAsNull() throws Exception {
        PortfolioResponse withoutFigures = new PortfolioResponse(ACCOUNT, AS_OF, null, null, List.of(withoutCostData()));
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(withoutFigures));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.netLiquidation").value(nullValue()))
                .andExpect(jsonPath("$.totalCashValue").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].position").value(5.0))
                .andExpect(jsonPath("$.holdings[0].averageCost").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].costBasis").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnlPercent").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].sector").value(nullValue()));
    }

    @Test
    void acceptsOnlyGet() throws Exception {
        mockMvc.perform(post("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/portfolio")).andExpect(status().isMethodNotAllowed());
    }

    private static PortfolioResponse portfolioWith(HoldingResponse... holdings) {
        return new PortfolioResponse(ACCOUNT, AS_OF, 100_000.0, 25_000.0, List.of(holdings));
    }

    private static HoldingResponse apple() {
        return new HoldingResponse("AAPL", "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 25.0, ACCOUNT,
                1500.0, 33.333, 7, 265598, "Technology", HoldingStatus.OPEN);
    }

    /** IB sent a position but no cost or price figures for it, and no sector has been entered. */
    private static HoldingResponse withoutCostData() {
        return new HoldingResponse("MSFT", "STK", "USD", 5.0, null, null, null, null, 0.0, ACCOUNT,
                null, null, 8, 272093, null, HoldingStatus.OPEN);
    }
}
