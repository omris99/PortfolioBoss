package portfolioboss.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import portfolioboss.model.Holding;
import portfolioboss.model.PortfolioSnapshot;

import java.time.Instant;
import java.util.List;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins the JSON contract the UI depends on ({@code ui/src/types/portfolio.ts}) using made-up
 * holdings, so it needs no TWS. {@code @WebMvcTest} starts only Spring's web layer;
 * {@code MockMvc} sends it fake HTTP requests without a real server.
 */
@WebMvcTest(PortfolioController.class)
@Import(SnapshotStore.class)
class PortfolioControllerTest {

    private static final String ACCOUNT = "U1234567";
    private static final Instant AS_OF = Instant.parse("2026-09-19T08:05:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SnapshotStore snapshotStore;

    @BeforeEach
    void startWithoutASnapshot() {
        snapshotStore.set(null);
    }

    @Test
    void answers503UntilTheSnapshotHasBeenRead() throws Exception {
        mockMvc.perform(get("/api/portfolio"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void servesTheSnapshotWithTheFieldNamesTheUiExpects() throws Exception {
        snapshotStore.set(new PortfolioSnapshot(ACCOUNT, AS_OF, 100_000.0, 25_000.0, List.of(apple())));

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
                .andExpect(jsonPath("$.holdings[0].unrealizedPnlPercent").value(closeTo(33.333, 0.001)));
    }

    @Test
    void writesAsOfAsAnIsoString() throws Exception {
        snapshotStore.set(new PortfolioSnapshot(ACCOUNT, AS_OF, 100_000.0, 25_000.0, List.of()));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.asOf").value("2026-09-19T08:05:00Z"));
    }

    @Test
    void writesFiguresIbDidNotReportAsNullNotAsTheStringNaN() throws Exception {
        snapshotStore.set(new PortfolioSnapshot(ACCOUNT, AS_OF, Double.NaN, Double.NaN, List.of(withoutCostData())));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.netLiquidation").value(nullValue()))
                .andExpect(jsonPath("$.totalCashValue").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].position").value(5.0))
                .andExpect(jsonPath("$.holdings[0].averageCost").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].costBasis").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnlPercent").value(nullValue()));
    }

    @Test
    void acceptsOnlyGet() throws Exception {
        mockMvc.perform(post("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/portfolio")).andExpect(status().isMethodNotAllowed());
    }

    private static Holding apple() {
        return new Holding("AAPL", "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 25.0, ACCOUNT);
    }

    /** IB sent a position but no cost or price figures for it. */
    private static Holding withoutCostData() {
        return new Holding("MSFT", "STK", "USD", 5.0, Double.NaN, Double.NaN, Double.NaN, Double.NaN, 0.0, ACCOUNT);
    }
}
