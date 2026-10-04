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
import portfolioboss.api.request.CashMovementRequest;
import portfolioboss.api.request.InvestorRequest;
import portfolioboss.api.response.AddedInvestorResponse;
import portfolioboss.api.response.CashMovementResponse;
import portfolioboss.db.CashMovementType;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of the investors and their deposits: status codes, the validation messages the UI will show, and the
 * JSON-only rule. {@link InvestorWriteService} is replaced by a stand-in, as in {@code HoldingWriteControllerTest};
 * what actually gets stored is tested in {@code InvestorWriteServiceTest}.
 */
@WebMvcTest(InvestorWriteController.class)
class InvestorWriteControllerTest {

    private static final long ACCOUNT_OWNER_ID = 1;
    private static final long AVI_ID = 2;
    private static final long MOVEMENT_ID = 7;
    private static final String DEPOSIT = """
            {"movementDate": "2026-01-10", "type": "DEPOSIT", "amount": 30000}""";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InvestorWriteService investorWriteService;

    // ── investors ───────────────────────────────────────────────────────────────────────────────

    @Test
    void addsAnInvestorAndAnswers201WithIt() throws Exception {
        given(investorWriteService.addInvestor(new InvestorRequest("Avi")))
                .willReturn(new AddedInvestorResponse(AVI_ID, "Avi"));

        postInvestor("""
                {"name": "Avi"}""")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(AVI_ID))
                .andExpect(jsonPath("$.name").value("Avi"));
    }

    @Test
    void rejectsABlankName() throws Exception {
        postInvestor("""
                {"name": "   "}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name: must not be blank"));
        verifyNoInteractions(investorWriteService);
    }

    @Test
    void rejectsANameLongerThanTheColumn() throws Exception {
        postInvestor("{\"name\": \"" + "A".repeat(61) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("name: size must be between 0 and 60"));
        verifyNoInteractions(investorWriteService);
    }

    @Test
    void answers409WithTheReasonWhenTheNameIsTaken() throws Exception {
        given(investorWriteService.addInvestor(any()))
                .willThrow(new ResponseStatusException(HttpStatus.CONFLICT, "An investor named Avi already exists."));

        postInvestor("""
                {"name": "Avi"}""")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An investor named Avi already exists."));
    }

    @Test
    void refusesACrossSiteTextPostWith415() throws Exception {
        mockMvc.perform(post("/api/investors").header("Origin", "https://evil.example")
                        .contentType(MediaType.TEXT_PLAIN).content("""
                                {"name": "Avi"}"""))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(investorWriteService);
    }

    @Test
    void renamesAnInvestorAndAnswers204() throws Exception {
        mockMvc.perform(put("/api/investors/1").contentType(MediaType.APPLICATION_JSON).content("""
                        {"name": "Omri"}"""))
                .andExpect(status().isNoContent());
        then(investorWriteService).should().renameInvestor(ACCOUNT_OWNER_ID, new InvestorRequest("Omri"));
    }

    // ── deposits and withdrawals ────────────────────────────────────────────────────────────────

    @Test
    void addsADepositAndAnswers201WithIt() throws Exception {
        CashMovementRequest expectedRequest = new CashMovementRequest(LocalDate.of(2026, 1, 10),
                CashMovementType.DEPOSIT, new BigDecimal("30000"), null);
        given(investorWriteService.addCashMovement(AVI_ID, expectedRequest)).willReturn(new CashMovementResponse(
                MOVEMENT_ID, LocalDate.of(2026, 1, 10), CashMovementType.DEPOSIT, new BigDecimal("30000"), null));

        postCashMovement(DEPOSIT)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(MOVEMENT_ID))
                .andExpect(jsonPath("$.movementDate").value("2026-01-10"))
                .andExpect(jsonPath("$.type").value("DEPOSIT"))
                .andExpect(jsonPath("$.amount").value(30000));
    }

    @Test
    void listsEveryMissingRequiredFieldInOneMessage() throws Exception {
        postCashMovement("{}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("amount: must not be null, movementDate: must not be null, type: must not be null"));
        verifyNoInteractions(investorWriteService);
    }

    @Test
    void rejectsAnAmountOfZero() throws Exception {
        postCashMovement("""
                {"movementDate": "2026-01-10", "type": "WITHDRAWAL", "amount": 0}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("amount: must be greater than 0"));
        verifyNoInteractions(investorWriteService);
    }

    @Test
    void rejectsADepositDatedInTheFuture() throws Exception {
        postCashMovement("""
                {"movementDate": "2999-01-01", "type": "DEPOSIT", "amount": 100}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("movementDate: must be a date in the past or in the present"));
        verifyNoInteractions(investorWriteService);
    }

    @Test
    void answers400WithTheReasonForADepositOfTheAccountOwner() throws Exception {
        given(investorWriteService.addCashMovement(anyLong(), any())).willThrow(new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "The account owner's cash comes from IB, so no deposits are entered for them."));

        mockMvc.perform(post("/api/investors/1/cash-movements").contentType(MediaType.APPLICATION_JSON).content(DEPOSIT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("The account owner's cash comes from IB, so no deposits are entered for them."));
    }

    @Test
    void changesADepositAndAnswers200WithIt() throws Exception {
        CashMovementRequest expectedRequest = new CashMovementRequest(LocalDate.of(2026, 1, 12),
                CashMovementType.WITHDRAWAL, new BigDecimal("500.5"), "Back to his bank");
        given(investorWriteService.changeCashMovement(MOVEMENT_ID, expectedRequest)).willReturn(new CashMovementResponse(
                MOVEMENT_ID, LocalDate.of(2026, 1, 12), CashMovementType.WITHDRAWAL, new BigDecimal("500.5"),
                "Back to his bank"));

        mockMvc.perform(put("/api/cash-movements/7").contentType(MediaType.APPLICATION_JSON).content("""
                        {"movementDate": "2026-01-12", "type": "WITHDRAWAL", "amount": 500.5,
                         "note": "Back to his bank"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("WITHDRAWAL"))
                .andExpect(jsonPath("$.note").value("Back to his bank"));
    }

    @Test
    void deletesADepositAndAnswers204() throws Exception {
        mockMvc.perform(delete("/api/cash-movements/7"))
                .andExpect(status().isNoContent());
        then(investorWriteService).should().deleteCashMovement(MOVEMENT_ID);
    }

    @Test
    void answers404WithTheReasonWhenTheDepositDoesNotExist() throws Exception {
        willThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "No deposit or withdrawal with id 7"))
                .given(investorWriteService).deleteCashMovement(MOVEMENT_ID);

        mockMvc.perform(delete("/api/cash-movements/7"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No deposit or withdrawal with id 7"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private ResultActions postInvestor(String json) throws Exception {
        return mockMvc.perform(post("/api/investors").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    /** Avi's. */
    private ResultActions postCashMovement(String json) throws Exception {
        return mockMvc.perform(post("/api/investors/2/cash-movements").contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
