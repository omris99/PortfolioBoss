package portfolioboss.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import portfolioboss.api.response.CashMovementResponse;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.api.response.ClosedPositionSource;
import portfolioboss.api.response.HoldingResponse;
import portfolioboss.api.response.InvestorQuantityResponse;
import portfolioboss.api.response.InvestorResponse;
import portfolioboss.api.response.PortfolioResponse;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.db.CashMovementType;
import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeSide;
import portfolioboss.domain.HoldingWarning;
import portfolioboss.domain.HoldingWarningType;
import portfolioboss.domain.InvestorWarning;
import portfolioboss.domain.InvestorWarningType;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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
    private static final String SOLD_TOO_MANY = "Sold 7 shares but bought 5 in this period: check its trades.";
    private static final String NEGATIVE_CASH_MESSAGE = "The cash comes to -120.00 USD: a deposit may be missing.";
    private static final long ACCOUNT_OWNER_ID = 1;
    private static final long OTHER_INVESTOR_ID = 2;

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
                .andExpect(jsonPath("$.holdings[0].trades[0].commission").value(5.0))
                .andExpect(jsonPath("$.holdings[0].trades[0].investorId").value(ACCOUNT_OWNER_ID))
                .andExpect(jsonPath("$.holdings[0].warnings").value(empty()))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[0].investorId").value(ACCOUNT_OWNER_ID))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[0].quantity").value(4))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[0].sharesValue").value(800))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[0].sharesCost").value(600))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[0].unrealizedPnl").value(200))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[0].unrealizedPnlPercent")
                        .value(closeTo(33.333, 0.001)))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[1].investorId").value(OTHER_INVESTOR_ID))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[1].quantity").value(6))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[1].sharesValue").value(1200))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[1].sharesCost").value(900))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[1].unrealizedPnl").value(300))
                .andExpect(jsonPath("$.holdings[0].investorQuantities[1].unrealizedPnlPercent")
                        .value(closeTo(33.333, 0.001)));
    }

    @Test
    void servesTheInvestorsWithTheFieldNamesTheUiExpects() throws Exception {
        PortfolioResponse withInvestors = new PortfolioResponse(ACCOUNT, AS_OF, 100_000.0, 40_000.0, List.of(),
                List.of(), List.of(accountOwner(), otherInvestor()));
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(withInvestors));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.investors[0].id").value(ACCOUNT_OWNER_ID))
                .andExpect(jsonPath("$.investors[0].name").value("Me"))
                .andExpect(jsonPath("$.investors[0].accountOwner").value(true))
                .andExpect(jsonPath("$.investors[0].depositsMinusWithdrawals").value(nullValue()))
                .andExpect(jsonPath("$.investors[0].realizedPnlByCurrency.USD").value(500))
                .andExpect(jsonPath("$.investors[0].realizedPnlByCurrency.HKD").value(950))
                .andExpect(jsonPath("$.investors[0].cashMovements").value(empty()))
                .andExpect(jsonPath("$.investors[1].accountOwner").value(false))
                .andExpect(jsonPath("$.investors[1].depositsMinusWithdrawals").value(30000))
                .andExpect(jsonPath("$.investors[1].cash").value(27120))
                .andExpect(jsonPath("$.investors[1].sharesValue").value(4320))
                .andExpect(jsonPath("$.investors[1].totalValue").value(31440))
                .andExpect(jsonPath("$.investors[1].sharesCost").value(2880))
                .andExpect(jsonPath("$.investors[1].unrealizedPnl").value(1440))
                .andExpect(jsonPath("$.investors[1].unrealizedPnlPercent").value(50))
                .andExpect(jsonPath("$.investors[1].realizedPnlByCurrency").isEmpty())
                .andExpect(jsonPath("$.investors[1].totalPnl").value(1440))
                .andExpect(jsonPath("$.investors[1].cashMovements[0].id").value(1))
                .andExpect(jsonPath("$.investors[1].cashMovements[0].movementDate").value("2026-01-10"))
                .andExpect(jsonPath("$.investors[1].cashMovements[0].type").value("DEPOSIT"))
                .andExpect(jsonPath("$.investors[1].cashMovements[0].amount").value(30000))
                .andExpect(jsonPath("$.investors[1].cashMovements[0].note").value(nullValue()))
                .andExpect(jsonPath("$.investors[1].warnings[0].type").value("NEGATIVE_CASH"))
                .andExpect(jsonPath("$.investors[1].warnings[0].message").value(NEGATIVE_CASH_MESSAGE));
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
                new PortfolioResponse(ACCOUNT, AS_OF, null, null, List.of(withoutCostData()), List.of(), List.of());
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
                List.of(appleBoughtAndSold(), microsoftSoldWithNoPricesEntered(), teslaEnteredByHand()), List.of());
        given(portfolioReadService.currentPortfolio()).willReturn(Optional.of(withClosedPositions));

        mockMvc.perform(get("/api/portfolio"))
                .andExpect(jsonPath("$.closedPositions[0].source").value("TRADES"))
                .andExpect(jsonPath("$.closedPositions[0].holdingId").value(7))
                .andExpect(jsonPath("$.closedPositions[0].manualPositionId").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[0].remainingQuantity").value(0))
                .andExpect(jsonPath("$.closedPositions[0].trades[0].side").value("BUY"))
                .andExpect(jsonPath("$.closedPositions[0].trades[1].side").value("SELL"))
                .andExpect(jsonPath("$.closedPositions[0].trades[1].price").value(180.0))
                .andExpect(jsonPath("$.closedPositions[0].symbol").value("AAPL"))
                .andExpect(jsonPath("$.closedPositions[0].currency").value("USD"))
                .andExpect(jsonPath("$.closedPositions[0].sector").value("Technology"))
                .andExpect(jsonPath("$.closedPositions[0].openDate").value("2024-03-01"))
                .andExpect(jsonPath("$.closedPositions[0].closeDate").value("2025-06-01"))
                .andExpect(jsonPath("$.closedPositions[0].holdingDays").value(457))
                .andExpect(jsonPath("$.closedPositions[0].quantity").value(10))
                .andExpect(jsonPath("$.closedPositions[0].averageBuyPrice").value(150.0))
                .andExpect(jsonPath("$.closedPositions[0].averageSellPrice").value(180.0))
                .andExpect(jsonPath("$.closedPositions[0].realizedPnl").value(290.0))
                .andExpect(jsonPath("$.closedPositions[0].realizedPnlPercent").value(closeTo(19.333, 0.001)))
                .andExpect(jsonPath("$.closedPositions[0].warning").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[0].commissions").value(10.0))
                .andExpect(jsonPath("$.closedPositions[0].note").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[0].investorId").value(ACCOUNT_OWNER_ID))
                .andExpect(jsonPath("$.closedPositions[1].warning").value(SOLD_TOO_MANY))
                .andExpect(jsonPath("$.closedPositions[1].sector").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[1].averageBuyPrice").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[1].realizedPnl").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[1].realizedPnlPercent").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[2].source").value("MANUAL"))
                .andExpect(jsonPath("$.closedPositions[2].holdingId").value(nullValue()))
                .andExpect(jsonPath("$.closedPositions[2].manualPositionId").value(3))
                .andExpect(jsonPath("$.closedPositions[2].note").value("Sold before PortfolioBoss"))
                .andExpect(jsonPath("$.closedPositions[2].remainingQuantity").value(2));
    }

    @Test
    void acceptsOnlyGet() throws Exception {
        mockMvc.perform(post("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(put("/api/portfolio")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/portfolio")).andExpect(status().isMethodNotAllowed());
    }

    private PortfolioResponse portfolioWith(HoldingResponse... holdings) {
        return new PortfolioResponse(ACCOUNT, AS_OF, 100_000.0, 25_000.0, List.of(holdings), List.of(), List.of());
    }

    /** 10 bought at 150 and sold at 180, $5 commission on each: +290, or +19.33%. */
    private ClosedPositionResponse appleBoughtAndSold() {
        TradeResponse buy = new TradeResponse(1, LocalDate.of(2024, 3, 1), TradeSide.BUY, new BigDecimal("10"),
                new BigDecimal("150.00"), null, new BigDecimal("5"), ACCOUNT_OWNER_ID);
        TradeResponse sell = new TradeResponse(2, LocalDate.of(2025, 6, 1), TradeSide.SELL, new BigDecimal("10"),
                new BigDecimal("180.00"), null, new BigDecimal("5"), ACCOUNT_OWNER_ID);
        return new ClosedPositionResponse(7L, "AAPL", "USD", "Technology", LocalDate.of(2024, 3, 1),
                LocalDate.of(2025, 6, 1), 457, new BigDecimal("10"), new BigDecimal("150.00"), new BigDecimal("180.00"),
                new BigDecimal("290.00"), new BigDecimal("19.33333333333333"), null, new BigDecimal("10"),
                ClosedPositionSource.TRADES, null, null, BigDecimal.ZERO, List.of(buy, sell), ACCOUNT_OWNER_ID);
    }

    /** Bought and sold with no price entered for either, no sector, and more sold than bought. */
    private ClosedPositionResponse microsoftSoldWithNoPricesEntered() {
        return new ClosedPositionResponse(8L, "MSFT", "USD", null, LocalDate.of(2025, 1, 1), LocalDate.of(2025, 2, 1),
                31, new BigDecimal("7"), null, null, null, null, SOLD_TOO_MANY, new BigDecimal("10"),
                ClosedPositionSource.TRADES, null, null, BigDecimal.ZERO, List.of(), ACCOUNT_OWNER_ID);
    }

    /** A manual position sold in part: no holding, an id of its own, a note, and 2 shares still held. */
    private ClosedPositionResponse teslaEnteredByHand() {
        return new ClosedPositionResponse(null, "TSLA", "USD", null, LocalDate.of(2022, 1, 10),
                LocalDate.of(2023, 5, 1), 476, new BigDecimal("5"), new BigDecimal("300"), new BigDecimal("310"),
                new BigDecimal("40"), new BigDecimal("2.666666666666667"), null, new BigDecimal("10"),
                ClosedPositionSource.MANUAL, 3L, "Sold before PortfolioBoss", new BigDecimal("2"), List.of(),
                OTHER_INVESTOR_ID);
    }

    /**
     * Bought once, never sold — still OPEN, so holdingDays counts to a made-up snapshot date. 4 of the 10 are the
     * account owner's, 6 the other investor's — bought at 150, now worth 200 each.
     */
    private HoldingResponse apple() {
        TradeResponse buy = new TradeResponse(1, LocalDate.of(2024, 3, 14), TradeSide.BUY,
                new BigDecimal("10"), new BigDecimal("150.00"), "Initial position", new BigDecimal("5"),
                ACCOUNT_OWNER_ID);
        List<InvestorQuantityResponse> investorQuantities = List.of(
                new InvestorQuantityResponse(ACCOUNT_OWNER_ID, new BigDecimal("4"), new BigDecimal("800"),
                        new BigDecimal("600"), new BigDecimal("200"), new BigDecimal("33.33333333333333")),
                new InvestorQuantityResponse(OTHER_INVESTOR_ID, new BigDecimal("6"), new BigDecimal("1200"),
                        new BigDecimal("900"), new BigDecimal("300"), new BigDecimal("33.33333333333333")));
        return new HoldingResponse("AAPL", "STK", "USD", 10.0, 150.0, 200.0, 2000.0, 500.0, 25.0, ACCOUNT,
                1500.0, 33.333, 7, 265598, "Technology", HoldingStatus.OPEN,
                LocalDate.of(2024, 3, 14), null, 920L, List.of(buy), List.of(), investorQuantities);
    }

    /** IB sent a position but no cost or price figures for it, no sector and no trades entered. */
    private HoldingResponse withoutCostData() {
        HoldingWarning noTrades = new HoldingWarning(HoldingWarningType.NO_TRADES_LOGGED, NO_TRADES_MESSAGE);
        return new HoldingResponse("MSFT", "STK", "USD", 5.0, null, null, null, null, 0.0, ACCOUNT,
                null, null, 8, 272093, null, HoldingStatus.OPEN, null, null, null, List.of(), List.of(noTrades),
                List.of());
    }

    /** The account owner of INVESTORS_TODO.md's example, with a realized P&amp;L in two currencies. */
    private InvestorResponse accountOwner() {
        return new InvestorResponse(ACCOUNT_OWNER_ID, "Me", true, null, new BigDecimal("12880"),
                new BigDecimal("55680"), new BigDecimal("68560"), new BigDecimal("47120"), new BigDecimal("8560"),
                new BigDecimal("18.16638370118846"), Map.of("USD", new BigDecimal("500"), "HKD", new BigDecimal("950")),
                new BigDecimal("9060"), List.of(), List.of());
    }

    /** The other investor of the example — with a made-up warning, to pin its shape. */
    private InvestorResponse otherInvestor() {
        CashMovementResponse deposit = new CashMovementResponse(1, LocalDate.of(2026, 1, 10),
                CashMovementType.DEPOSIT, new BigDecimal("30000"), null);
        InvestorWarning negativeCash = new InvestorWarning(InvestorWarningType.NEGATIVE_CASH, NEGATIVE_CASH_MESSAGE);
        return new InvestorResponse(OTHER_INVESTOR_ID, "Avi", false, new BigDecimal("30000"), new BigDecimal("27120"),
                new BigDecimal("4320"), new BigDecimal("31440"), new BigDecimal("2880"), new BigDecimal("1440"),
                new BigDecimal("50"), Map.of(), new BigDecimal("1440"), List.of(deposit), List.of(negativeCash));
    }
}
