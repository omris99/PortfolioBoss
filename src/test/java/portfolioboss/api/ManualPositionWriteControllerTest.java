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
import portfolioboss.api.request.ManualPositionRequest;
import portfolioboss.api.request.NewManualPositionRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.ManualPositionResponse;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of the manual positions: status codes, the validation messages the UI will show, and the JSON-only
 * rule. {@link ManualPositionWriteService} is replaced by a stand-in, as in {@code HoldingWriteControllerTest}; what
 * actually gets stored is tested in {@code ManualPositionWriteServiceTest}.
 */
@WebMvcTest(ManualPositionWriteController.class)
class ManualPositionWriteControllerTest {

    private static final long MANUAL_POSITION_ID = 3;
    private static final String MICROSOFT_ROUND_TRIP = """
            {"symbol": "MSFT", "currency": "USD", "sector": "Technology", "note": "Sold before PortfolioBoss",
             "quantity": 5, "buyDate": "2022-01-10", "buyPrice": 300, "buyCommission": 2,
             "sellDate": "2023-05-01", "sellPrice": 310}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ManualPositionWriteService manualPositionWriteService;

    // ── adding ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void addsAManualPositionAndAnswers201WithIt() throws Exception {
        NewManualPositionRequest expectedRequest = new NewManualPositionRequest("MSFT", "USD", "Technology",
                "Sold before PortfolioBoss", new BigDecimal("5"), LocalDate.of(2022, 1, 10), new BigDecimal("300"),
                new BigDecimal("2"), LocalDate.of(2023, 5, 1), new BigDecimal("310"), null, null);
        given(manualPositionWriteService.addManualPosition(expectedRequest)).willReturn(
                new ManualPositionResponse(MANUAL_POSITION_ID, "MSFT", "USD", "Technology", "Sold before PortfolioBoss"));

        postManualPosition(MICROSOFT_ROUND_TRIP)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MANUAL_POSITION_ID))
                .andExpect(jsonPath("$.symbol").value("MSFT"));
    }

    @Test
    void rejectsASellDateBeforeTheBuyDate() throws Exception {
        postManualPosition("""
                {"symbol": "MSFT", "currency": "USD", "quantity": 5,
                 "buyDate": "2023-05-01", "buyPrice": 300, "sellDate": "2022-01-10", "sellPrice": 310}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("sellDateOnOrAfterBuyDate: the sell date is before the buy date"));
        verifyNoInteractions(manualPositionWriteService);
    }

    @Test
    void listsEveryMissingRequiredFieldInOneMessage() throws Exception {
        postManualPosition("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("buyDate: must not be null, buyPrice: must not be null, "
                        + "currency: must not be blank, quantity: must not be null, sellDate: must not be null, "
                        + "sellPrice: must not be null, symbol: must not be blank"));
        verifyNoInteractions(manualPositionWriteService);
    }

    @Test
    void rejectsANegativeCommission() throws Exception {
        postManualPosition("""
                {"symbol": "MSFT", "currency": "USD", "quantity": 5, "buyDate": "2022-01-10", "buyPrice": 300,
                 "sellDate": "2023-05-01", "sellPrice": 310, "sellCommission": -1}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("sellCommission: must be greater than or equal to 0"));
        verifyNoInteractions(manualPositionWriteService);
    }

    @Test
    void rejectsAQuantityThatIsNotAWholeNumber() throws Exception {
        postManualPosition("""
                {"symbol": "MSFT", "currency": "USD", "quantity": 2.5, "buyDate": "2022-01-10", "buyPrice": 300,
                 "sellDate": "2023-05-01", "sellPrice": 310}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("quantity: must be a whole number (at most 14 digits)"));
        verifyNoInteractions(manualPositionWriteService);
    }

    @Test
    void refusesACrossSiteTextPostWith415() throws Exception {
        mockMvc.perform(post("/api/manual-positions").header("Origin", "https://evil.example")
                        .contentType(MediaType.TEXT_PLAIN).content(MICROSOFT_ROUND_TRIP))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(manualPositionWriteService);
    }

    // ── its details ─────────────────────────────────────────────────────────────────────────────

    @Test
    void changesItsDetailsAndAnswers204() throws Exception {
        mockMvc.perform(put("/api/manual-positions/3").contentType(MediaType.APPLICATION_JSON).content("""
                        {"symbol": "9988.HK", "currency": "HKD", "sector": "E-commerce", "note": null}"""))
                .andExpect(status().isNoContent());
        then(manualPositionWriteService).should().changeManualPosition(MANUAL_POSITION_ID,
                new ManualPositionRequest("9988.HK", "HKD", "E-commerce", null));
    }

    @Test
    void rejectsDetailsWithoutASymbol() throws Exception {
        mockMvc.perform(put("/api/manual-positions/3").contentType(MediaType.APPLICATION_JSON).content("""
                        {"symbol": "  ", "currency": "HKD"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("symbol: must not be blank"));
        verifyNoInteractions(manualPositionWriteService);
    }

    @Test
    void deletesAManualPositionAndAnswers204() throws Exception {
        mockMvc.perform(delete("/api/manual-positions/3"))
                .andExpect(status().isNoContent());
        then(manualPositionWriteService).should().deleteManualPosition(MANUAL_POSITION_ID);
    }

    // ── its trades ──────────────────────────────────────────────────────────────────────────────

    @Test
    void addsATradeAndAnswers201WithIt() throws Exception {
        TradeRequest expectedRequest = new TradeRequest(LocalDate.of(2021, 8, 19), TradeSide.BUY,
                new BigDecimal("100"), new BigDecimal("162.1"), null, null, null);
        given(manualPositionWriteService.addTrade(MANUAL_POSITION_ID, expectedRequest)).willReturn(new TradeResponse(
                42, LocalDate.of(2021, 8, 19), TradeSide.BUY, new BigDecimal("100"), new BigDecimal("162.1"), null,
                new BigDecimal("5"), 1));

        mockMvc.perform(post("/api/manual-positions/3/trades").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tradeDate": "2021-08-19", "side": "BUY", "quantity": 100, "price": 162.1}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(42))
                .andExpect(jsonPath("$.commission").value(5));
    }

    @Test
    void answers404WithTheReasonWhenTheManualPositionDoesNotExist() throws Exception {
        given(manualPositionWriteService.addTrade(anyLong(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No manual position with id 3"));

        mockMvc.perform(post("/api/manual-positions/3/trades").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tradeDate": "2021-08-19", "side": "BUY", "quantity": 100}"""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No manual position with id 3"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private ResultActions postManualPosition(String json) throws Exception {
        return mockMvc.perform(post("/api/manual-positions").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
