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
import portfolioboss.api.request.SectorRequest;
import portfolioboss.api.request.TradeRequest;
import portfolioboss.api.response.TradeResponse;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of the write endpoints: status codes, the validation messages the UI will show, and the JSON-only rule
 * that keeps other websites out. {@link HoldingWriteService} is replaced by a stand-in, as in
 * {@code PortfolioControllerTest}; what actually gets stored is tested in {@code HoldingWriteServiceTest}.
 */
@WebMvcTest(HoldingWriteController.class)
class HoldingWriteControllerTest {

    private static final long APPLE_HOLDING_ID = 7;
    private static final long TRADE_ID = 42;
    private static final String OTHER_SITE = "https://evil.example";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private HoldingWriteService holdingWriteService;

    // ── adding a trade ──────────────────────────────────────────────────────────────────────────

    @Test
    void addsATradeAndAnswers201WithIt() throws Exception {
        TradeRequest expectedRequest = new TradeRequest(LocalDate.of(2025, 1, 15), TradeSide.BUY,
                new BigDecimal("10.5"), new BigDecimal("150.25"), "Initial position");
        given(holdingWriteService.addTrade(APPLE_HOLDING_ID, expectedRequest)).willReturn(new TradeResponse(TRADE_ID,
                LocalDate.of(2025, 1, 15), TradeSide.BUY, new BigDecimal("10.5"), new BigDecimal("150.25"),
                "Initial position"));

        mockMvc.perform(post("/api/holdings/7/trades").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tradeDate": "2025-01-15", "side": "BUY", "quantity": 10.5, "price": 150.25,
                         "note": "Initial position"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TRADE_ID))
                .andExpect(jsonPath("$.tradeDate").value("2025-01-15"))
                .andExpect(jsonPath("$.side").value("BUY"))
                .andExpect(jsonPath("$.quantity").value(10.5));
    }

    @Test
    void rejectsATradeDatedInTheFuture() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        postTrade("{\"tradeDate\": \"" + tomorrow + "\", \"side\": \"BUY\", \"quantity\": 10}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("tradeDate: must be a date in the past or in the present"));
        verifyNoInteractions(holdingWriteService);
    }

    @Test
    void rejectsAZeroQuantityAndNamesTheField() throws Exception {
        postTrade("""
                {"tradeDate": "2025-01-15", "side": "BUY", "quantity": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("quantity: must be greater than 0"));
        verifyNoInteractions(holdingWriteService);
    }

    @Test
    void listsEveryMissingRequiredFieldInOneMessage() throws Exception {
        postTrade("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("quantity: must not be null, side: must not be null, tradeDate: must not be null"));
    }

    @Test
    void rejectsMoreDecimalPlacesThanTheDatabaseKeeps() throws Exception {
        postTrade("""
                {"tradeDate": "2025-01-15", "side": "BUY", "quantity": 0.0000001}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("quantity: must have at most 6 decimal places (and 14 digits before the point)"));
    }

    /** IB's average cost, as the UI once prefilled it unrounded (the UI now rounds it to 4 places). */
    @Test
    void rejectsAPriceWithMoreDecimalPlacesThanTheDatabaseKeeps() throws Exception {
        postTrade("""
                {"tradeDate": "2025-01-15", "side": "BUY", "quantity": 10, "price": 188.2990476}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("price: must have at most 6 decimal places (and 14 digits before the point)"));
        verifyNoInteractions(holdingWriteService);
    }

    @Test
    void rejectsAnUnknownSide() throws Exception {
        postTrade("""
                {"tradeDate": "2025-01-15", "side": "HOLD", "quantity": 10}""")
                .andExpect(status().isBadRequest());
        verifyNoInteractions(holdingWriteService);
    }

    @Test
    void answers404WithTheReasonWhenTheHoldingDoesNotExist() throws Exception {
        given(holdingWriteService.addTrade(anyLong(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No holding with id 7"));

        postTrade("""
                {"tradeDate": "2025-01-15", "side": "BUY", "quantity": 10}""")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No holding with id 7"));
    }

    // ── keeping other websites out ──────────────────────────────────────────────────────────────

    @Test
    void refusesACrossSiteTextPostWith415() throws Exception {
        mockMvc.perform(post("/api/holdings/7/trades").header("Origin", OTHER_SITE)
                        .contentType(MediaType.TEXT_PLAIN).content("""
                        {"tradeDate": "2025-01-15", "side": "BUY", "quantity": 10}"""))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(holdingWriteService);
    }

    @Test
    void givesAnotherSiteNoCorsPermissionToSendJson() throws Exception {
        mockMvc.perform(options("/api/holdings/7/trades")
                        .header("Origin", OTHER_SITE)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(holdingWriteService);
    }

    // ── sector ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void changesTheSectorAndAnswers204() throws Exception {
        mockMvc.perform(put("/api/holdings/7/sector").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sector": "Technology"}"""))
                .andExpect(status().isNoContent());
        then(holdingWriteService).should().changeSector(APPLE_HOLDING_ID, new SectorRequest("Technology"));
    }

    @Test
    void rejectsASectorLongerThanTheColumn() throws Exception {
        String sixtyOneCharacters = "x".repeat(61);

        mockMvc.perform(put("/api/holdings/7/sector").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sector\": \"" + sixtyOneCharacters + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("sector: size must be between 0 and 60"));
        verifyNoInteractions(holdingWriteService);
    }

    // ── changing and deleting a trade ───────────────────────────────────────────────────────────

    @Test
    void changesATradeAndAnswers200WithIt() throws Exception {
        TradeRequest expectedRequest = new TradeRequest(LocalDate.of(2025, 2, 1), TradeSide.SELL,
                new BigDecimal("4"), null, null);
        given(holdingWriteService.changeTrade(TRADE_ID, expectedRequest)).willReturn(new TradeResponse(TRADE_ID,
                LocalDate.of(2025, 2, 1), TradeSide.SELL, new BigDecimal("4"), null, null));

        mockMvc.perform(put("/api/trades/42").contentType(MediaType.APPLICATION_JSON).content("""
                        {"tradeDate": "2025-02-01", "side": "SELL", "quantity": 4}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TRADE_ID))
                .andExpect(jsonPath("$.side").value("SELL"));
    }

    @Test
    void deletesATradeAndAnswers204() throws Exception {
        mockMvc.perform(delete("/api/trades/42"))
                .andExpect(status().isNoContent());
        then(holdingWriteService).should().deleteTrade(TRADE_ID);
    }

    @Test
    void answers404WhenDeletingATradeThatDoesNotExist() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No trade with id 42"))
                .given(holdingWriteService).deleteTrade(TRADE_ID);

        mockMvc.perform(delete("/api/trades/42"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No trade with id 42"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private ResultActions postTrade(String json) throws Exception {
        return mockMvc.perform(post("/api/holdings/7/trades").contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
