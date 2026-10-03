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
import portfolioboss.api.request.ManualClosedPositionRequest;
import portfolioboss.api.response.ClosedPositionResponse;
import portfolioboss.api.response.ClosedPositionSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.hamcrest.Matchers.nullValue;
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
 * The HTTP side of the closed positions entered by hand: status codes, the validation messages the UI will show, and
 * the JSON-only rule. {@link ClosedPositionWriteService} is replaced by a stand-in, as in {@code HoldingWriteControllerTest};
 * what actually gets stored is tested in {@code ClosedPositionWriteServiceTest}.
 */
@WebMvcTest(ClosedPositionWriteController.class)
class ClosedPositionWriteControllerTest {

    private static final long MANUAL_ID = 3;
    private static final String OTHER_SITE = "https://evil.example";
    private static final String MICROSOFT_ROUND_TRIP = """
            {"symbol": "MSFT", "currency": "USD", "sector": "Technology", "quantity": 5,
             "buyDate": "2022-01-10", "buyPrice": 300, "sellDate": "2023-05-01", "sellPrice": 310,
             "commission": 2, "note": "Sold before PortfolioBoss"}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ClosedPositionWriteService closedPositionWriteService;

    // ── adding ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void addsAManualClosedPositionAndAnswers201WithIt() throws Exception {
        given(closedPositionWriteService.addManualClosedPosition(microsoftRoundTrip())).willReturn(microsoftResponse());

        postManualClosedPosition(MICROSOFT_ROUND_TRIP)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.manualClosedPositionId").value(MANUAL_ID))
                .andExpect(jsonPath("$.holdingId").value(nullValue()))
                .andExpect(jsonPath("$.realizedPnl").value(48));
    }

    @Test
    void rejectsASellDateBeforeTheBuyDate() throws Exception {
        postManualClosedPosition("""
                {"symbol": "MSFT", "currency": "USD", "quantity": 5,
                 "buyDate": "2023-05-01", "buyPrice": 300, "sellDate": "2022-01-10", "sellPrice": 310}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("sellDateOnOrAfterBuyDate: the sell date is before the buy date"));
        verifyNoInteractions(closedPositionWriteService);
    }

    @Test
    void acceptsABuyAndASellOnTheSameDate() throws Exception {
        postManualClosedPosition("""
                {"symbol": "MSFT", "currency": "USD", "quantity": 5,
                 "buyDate": "2023-05-01", "buyPrice": 300, "sellDate": "2023-05-01", "sellPrice": 310}""")
                .andExpect(status().isCreated());
    }

    @Test
    void listsEveryMissingRequiredFieldInOneMessage() throws Exception {
        postManualClosedPosition("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("buyDate: must not be null, buyPrice: must not be null, "
                        + "currency: must not be blank, quantity: must not be null, sellDate: must not be null, "
                        + "sellPrice: must not be null, symbol: must not be blank"));
        verifyNoInteractions(closedPositionWriteService);
    }

    @Test
    void rejectsASellDateInTheFutureAndANegativeCommission() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        postManualClosedPosition("""
                {"symbol": "MSFT", "currency": "USD", "quantity": 5, "buyDate": "2022-01-10", "buyPrice": 300,
                 "sellDate": "%s", "sellPrice": 310, "commission": -1}""".formatted(tomorrow))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("commission: must be greater than or equal to 0, "
                        + "sellDate: must be a date in the past or in the present"));
        verifyNoInteractions(closedPositionWriteService);
    }

    @Test
    void refusesACrossSiteTextPostWith415() throws Exception {
        mockMvc.perform(post("/api/manual-closed-positions").header("Origin", OTHER_SITE)
                        .contentType(MediaType.TEXT_PLAIN).content(MICROSOFT_ROUND_TRIP))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(closedPositionWriteService);
    }

    @Test
    void givesAnotherSiteNoCorsPermissionToSendJson() throws Exception {
        mockMvc.perform(options("/api/manual-closed-positions")
                        .header("Origin", OTHER_SITE)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Origin"));
        verifyNoInteractions(closedPositionWriteService);
    }

    // ── changing and deleting ───────────────────────────────────────────────────────────────────

    @Test
    void changesAManualClosedPositionAndAnswers200WithIt() throws Exception {
        given(closedPositionWriteService.changeManualClosedPosition(MANUAL_ID, microsoftRoundTrip()))
                .willReturn(microsoftResponse());

        mockMvc.perform(put("/api/manual-closed-positions/3").contentType(MediaType.APPLICATION_JSON)
                        .content(MICROSOFT_ROUND_TRIP))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.manualClosedPositionId").value(MANUAL_ID));
    }

    @Test
    void answers404WithTheReasonWhenChangingOneThatDoesNotExist() throws Exception {
        given(closedPositionWriteService.changeManualClosedPosition(anyLong(), any()))
                .willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No manual closed position with id 3"));

        mockMvc.perform(put("/api/manual-closed-positions/3").contentType(MediaType.APPLICATION_JSON)
                        .content(MICROSOFT_ROUND_TRIP))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No manual closed position with id 3"));
    }

    @Test
    void deletesAManualClosedPositionAndAnswers204() throws Exception {
        mockMvc.perform(delete("/api/manual-closed-positions/3"))
                .andExpect(status().isNoContent());
        then(closedPositionWriteService).should().deleteManualClosedPosition(MANUAL_ID);
    }

    @Test
    void answers404WhenDeletingOneThatDoesNotExist() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No manual closed position with id 3"))
                .given(closedPositionWriteService).deleteManualClosedPosition(MANUAL_ID);

        mockMvc.perform(delete("/api/manual-closed-positions/3"))
                .andExpect(status().isNotFound());
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private ResultActions postManualClosedPosition(String json) throws Exception {
        return mockMvc.perform(post("/api/manual-closed-positions").contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** {@link #MICROSOFT_ROUND_TRIP} as the controller should hand it to the service. */
    private ManualClosedPositionRequest microsoftRoundTrip() {
        return new ManualClosedPositionRequest("MSFT", "USD", "Technology", new BigDecimal("5"),
                LocalDate.of(2022, 1, 10), new BigDecimal("300"), LocalDate.of(2023, 5, 1), new BigDecimal("310"),
                new BigDecimal("2"), "Sold before PortfolioBoss");
    }

    /** 5 bought at 300 and sold at 310, $2 commission: +48. */
    private ClosedPositionResponse microsoftResponse() {
        return new ClosedPositionResponse(null, "MSFT", "USD", "Technology", LocalDate.of(2022, 1, 10),
                LocalDate.of(2023, 5, 1), 476, new BigDecimal("5"), new BigDecimal("300"), new BigDecimal("310"),
                new BigDecimal("48"), new BigDecimal("3.2"), null, new BigDecimal("2"), ClosedPositionSource.MANUAL,
                MANUAL_ID, "Sold before PortfolioBoss");
    }
}
