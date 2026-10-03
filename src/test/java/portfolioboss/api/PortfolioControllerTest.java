package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.api.response.HoldingResponse;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeSide;
import portfolioboss.domain.HoldingWarning;
import portfolioboss.domain.HoldingWarningType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.closeTo;
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
 * Pins the JSON contract the UI depends on ({@code ui/src/types/portfolio.ts}) using made-up responses, so it
 * needs neither TWS nor a database. {@code @WebMvcTest} starts only Spring's web layer; {@code MockMvc} sends
 * it fake HTTP requests without a real server; {@code @MockitoBean} replaces {@link PortfolioReadService} with
 * a stand-in whose answers each test chooses. The path from the database to the response — including how
 * {@code firstBuyDate} / {@code lastSellDate} / {@code holdingDays} are actually derived — is tested in
 * {@code PortfolioReadServiceTest}; here the values are just made up to pin the JSON shape.
 */
@WebMvcTest(PortfolioController.class)
class PortfolioControllerTest {

    private static final String ACCOUNT = "U1234567";
    private static final Instant AS_OF = Instant.parse("2026-09-19T08:05:00Z");
    private static final String NO_TRADES_MESSAGE = "No buy entered yet, so the buy date and holding period are unknown.";

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
                .andExpect(jsonPath("$.holdings[0].status").value("OPEN"))
                .andExpect(jsonPath("$.holdings[0].firstBuyDate").value("2024-03-14"))
                .andExpect(jsonPath("$.holdings[0].lastSellDate").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].holdingDays").value(920))
                .andExpect(jsonPath("$.holdings[0].trades[0].id").value(1))
                .andExpect(jsonPath("$.holdings[0].trades[0].tradeDate").value("2024-03-14"))
                .andExpect(jsonPath("$.holdings[0].trades[0].side").value("BUY"))
                .andExpect(jsonPath("$.holdings[0].trades[0].quantity").value(10))
                .andExpect(jsonPath("$.holdings[0].trades[0].price").value(150.0))
                .andExpect(jsonPath("$.holdings[0].trades[0].note").value("Initial position"))
                .andExpect(jsonPath("$.holdings[0].warnings").value(empty()));
    }

    @Test
    void servesEachWarningWithItsTypeAndMessage() throws Exception {
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(portfolioWith(withoutCostData())));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.holdings[0].warnings[0].type").value("NO_TRADES_LOGGED"))
                .andExpect(jsonPath("$.holdings[0].warnings[0].message").value(NO_TRADES_MESSAGE));
    }

    @Test
    void writesAsOfAsAnIsoString() throws Exception {
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(portfolioWith()));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.asOf").value("2026-09-19T08:05:00Z"));
    }

    @Test
    void keepsFiguresIbDidNotReportInTheJsonAsNull() throws Exception {
        PortfolioResponse withoutFigures =
                new PortfolioResponse(ACCOUNT, AS_OF, null, null, List.of(withoutCostData()), List.of());
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(withoutFigures));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.netLiquidation").value(nullValue()))
                .andExpect(jsonPath("$.totalCashValue").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].position").value(5.0))
                .andExpect(jsonPath("$.holdings[0].averageCost").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].costBasis").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].unrealizedPnlPercent").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].sector").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].firstBuyDate").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].holdingDays").value(nullValue()))
                .andExpect(jsonPath("$.holdings[0].trades").value(empty()));
    }

    @Test
    void servesClosedPositionsWithTheFieldNamesTheUiExpects() throws Exception {
        PortfolioResponse withClosedPositions = new PortfolioResponse(ACCOUNT, AS_OF, 100_000.0, 25_000.0, List.of(),
                List.of(appleBoughtAndSold(), microsoftSoldWithNoPricesEntered()));
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(withClosedPositions));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.closedPositions[0].holdingId").value(7))
                .andExpect(jsonPath("$.closedPositions[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.closedPositions[0].currency").value("USD"))
                .andExpect(jsonPath("$.closedPositions[0].sector").value("Technology"))
                .andExpect(jsonPath("$.closedPositions[0].openDate").value("2024-03-01"))
                .andExpect(jsonPath("$.closedPositions[0].closeDate").value("2025-06-01"))
                .andExpect(jsonPath("$.closedPositions[0].holdingDays").value(457))
                .andExpect(jsonPath("$.closedPositions[0].quantity").value(10))
                .andExpect(jsonPath("$.closedPositions[0].averageBuyPrice").value(150.0))
                .andExpect(jsonPath("$.closedPositions[0].averageSellPrice").value(180.0))
                .andExpect(jsonPath("$.closedPositions[0].realizedPnl").value(300.0))
                .andExpect(jsonPath("$.closedPositions[0].realizedPnlPercent").value(20.0))
                .andExpect(jsonPath("$.closedPositions[1].sector").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[1].averageBuyPrice").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[1].realizedPnl").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[1].realizedPnlPercent").value(nullValue()));
    }

    @Test
    void acceptsOnlyGet() throws Exception {
        mockMvc.perform(post("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/portfolio")).andExpect(status().isMethodNotAllowed());
    }

    private PortfolioResponse portfolioWith(HoldingResponse... holdings) {
        return new PortfolioResponse(ACCOUNT, AS_OF, 100_000.0, 25_000.0, List.of(holdings), List.of());
    }

    /** 10 bought at 150 and sold at 180: +300, or +20%. */
    private ClosedPositionResponse appleBoughtAndSold() {
        return new ClosedPositionResponse(7, "AAPL", "USD", "Technology", LocalDate.of(2024, 3, 1),
                LocalDate.of(2025, 6, 1), 457, new BigDecimal("10"), new BigDecimal("150.00"), new BigDecimal("180.00"),
                new BigDecimal("300.00"), new BigDecimal("20"));
    }

    /** Bought and sold with no price entered for either, and no sector. */
    private ClosedPositionResponse microsoftSoldWithNoPricesEntered() {
        return new ClosedPositionResponse(8, "MSFT", "USD", null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1),
                31, new BigDecimal("5"), null, null, null, null);
    }

    /** Bought once, never sold — still OPEN, so holdingDays counts to a made-up snapshot date. */
    private HoldingResponse apple() {
        TradeResponse buy = new TradeResponse(1, LocalDate.of(2024, 3, 14), TradeSide.BUY,
                new BigDecimal("10"), new BigDecimal("150.00"), "Initial position");
        return new HoldingResponse("AAPL", "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 25.0, ACCOUNT,
                1500.0, 33.333, 7, 265598, "Technology", HoldingStatus.OPEN,
                LocalDate.of(2024, 3, 14), null, 920L, List.of(buy), List.of());
    }

    /** IB sent a position but no cost or price figures for it, no sector and no trades entered. */
    private HoldingResponse withoutCostData() {
        HoldingWarning noTrades = new HoldingWarning(HoldingWarningType.NO_TRADES_LOGGED, NO_TRADES_MESSAGE);
        return new HoldingResponse("MSFT", "STK", "USD", 5.0, null, null, null, null, 0.0, ACCOUNT,
                null, null, 8, 272093, null, HoldingStatus.OPEN, null, null, null, List.of(), List.of(noTrades));
    }
}
