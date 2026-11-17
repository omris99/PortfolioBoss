package portfolioboss.api.tax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.tax.response.InvestorTaxResponse;
import portfolioboss.api.tax.response.MonthlySettlementResponse;
import portfolioboss.api.tax.response.SaleResponse;
import portfolioboss.api.tax.response.SettlementDayResponse;
import portfolioboss.api.tax.response.SharesSoldFromOneBuyResponse;
import portfolioboss.api.tax.response.TaxConfirmationResponse;
import portfolioboss.api.tax.response.TaxDeductibleExpenseResponse;
import portfolioboss.api.tax.response.TaxYearResponse;
import portfolioboss.api.tax.response.TaxesResponse;
import portfolioboss.model.tax.SaleNotComputedReason;
import portfolioboss.model.tax.SettlementDayStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of the Taxes page: {@code GET /api/taxes} and the confirmations under {@code /api/taxes/confirmations} —
 * the JSON the UI will read, the status codes, the validation messages, and the JSON-only rule of the writes. The two
 * services are replaced by stand-ins; what they work out and store is tested in {@code TaxesReadServiceTest} and
 * {@code TaxConfirmationServiceTest}. Made-up figures.
 */
@WebMvcTest(TaxController.class)
class TaxControllerTest {

    private static final LocalDate SALE_SETTLEMENT_DAY = LocalDate.of(2026, 6, 2);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TaxesReadService taxesReadService;

    @MockitoBean
    private TaxConfirmationService taxConfirmationService;

    @Test
    void answersTheTaxesNotToBeKept() throws Exception {
        given(taxesReadService.taxes()).willReturn(Optional.of(madeUpTaxes()));

        mockMvc.perform(get("/api/taxes"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.lastSyncDay").value("2026-11-13"))
                .andExpect(jsonPath("$.accountTaxYears[0].year").value(2026))
                .andExpect(jsonPath("$.accountTaxYears[0].taxOfTheYearSoFar").value(401.48))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[0].settlementDay").value("2026-04-01"))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[0].confirmation").isEmpty())
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[0].expenses[0].amountInShekels").value(-37.00))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].computedTaxAmount").value(410.73))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].confirmation.id").value(7))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].confirmation.taxAmount").value(400.00))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].confirmation.confirmedAt")
                        .value("2026-07-01T08:00:00Z"))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].sales[0].notComputedReason").isEmpty())
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].sales[0].sharesSoldFromEachBuy[0]"
                        + ".gainInShekels").value(1642.90))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].sales[1].notComputedReason")
                        .value("NO_RATE_FOR_THE_CURRENCY"))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].sales[1].taxableGain").isEmpty())
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].sales[1].sharesSoldFromEachBuy[0]"
                        + ".buyRate").isEmpty())
                .andExpect(jsonPath("$.accountTaxYears[0].monthlySettlements[0].month").value("2026-06"))
                .andExpect(jsonPath("$.accountTaxYears[0].monthlySettlements[0].settled").value(true))
                .andExpect(jsonPath("$.otherInvestorsTax[0].investorName").value("Avi"))
                .andExpect(jsonPath("$.otherInvestorsTax[0].taxYears[0].taxOfTheYearSoFar").value(163.23));
    }

    /** What the Taxes page added (session 5): each day's status, the cost check of a sale, and IB's reports. */
    @Test
    void answersWhatThePageNeedsBeyondTheTax() throws Exception {
        given(taxesReadService.taxes()).willReturn(Optional.of(madeUpTaxes()));

        mockMvc.perform(get("/api/taxes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flexConnected").value(true))
                .andExpect(jsonPath("$.flexReportsStoredUpTo").value("2026-11-13"))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[0].status").value("CALCULATED"))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].status").value("CONFIRMED"))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].sales[0].fifoCostDiffersFromAverageCost")
                        .value(true))
                .andExpect(jsonPath("$.accountTaxYears[0].settlementDays[1].sales[1].fifoCostDiffersFromAverageCost")
                        .value(false));
    }

    @Test
    void answers503BeforeTheFirstSync() throws Exception {
        given(taxesReadService.taxes()).willReturn(Optional.empty());

        mockMvc.perform(get("/api/taxes")).andExpect(status().isServiceUnavailable());
    }

    @Test
    void confirmsWith201AndEachNewConfirmation() throws Exception {
        given(taxConfirmationService.confirm(any())).willReturn(List.of(new TaxConfirmationResponse(7,
                SALE_SETTLEMENT_DAY, new BigDecimal("410.73"), Instant.parse("2026-07-01T08:00:00Z"), null)));

        postConfirmations(confirmationsJsonWith(lineJson("2026-06-02", "410.73", null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].settlementDay").value("2026-06-02"))
                .andExpect(jsonPath("$[0].taxAmount").value(410.73));
        verify(taxConfirmationService).confirm(any());
    }

    @Test
    void refusesAnEmptyRequest() throws Exception {
        postConfirmations("{\"confirmations\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("confirmations: must not be empty"));
        verifyNoInteractions(taxConfirmationService);
    }

    @Test
    void refusesTheSameDayTwice() throws Exception {
        postConfirmations(confirmationsJsonWith(lineJson("2026-06-02", "410.73", null),
                lineJson("2026-06-02", "400.00", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("everyDayGivenOnce: each settlement day can be confirmed only once"));
        verifyNoInteractions(taxConfirmationService);
    }

    @Test
    void refusesADayInTheFuture() throws Exception {
        String tomorrow = LocalDate.now().plusDays(1).toString();

        postConfirmations(confirmationsJsonWith(lineJson(tomorrow, "410.73", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail")
                        .value("confirmations[0].settlementDay: must be a date in the past or in the present"));
        verifyNoInteractions(taxConfirmationService);
    }

    @Test
    void refusesMoreThanAgorot() throws Exception {
        postConfirmations(confirmationsJsonWith(lineJson("2026-06-02", "410.735", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("confirmations[0].taxAmount: must have at most 2 decimal places "
                        + "(agorot) and 18 digits before the point"));
        verifyNoInteractions(taxConfirmationService);
    }

    @Test
    void refusesAMissingAmountAndANoteTooLong() throws Exception {
        String noteTooLong = "x".repeat(501);

        postConfirmations("{\"confirmations\": [{\"settlementDay\": \"2026-06-02\", \"note\": \"" + noteTooLong + "\"}]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("confirmations[0].note: size must be between 0 and 500, "
                        + "confirmations[0].taxAmount: must not be null"));
        verifyNoInteractions(taxConfirmationService);
    }

    /** The service's refusals — a day confirmed already, a day without a calculated entry — reach the UI as they are. */
    @Test
    void passesTheServicesRefusalOn() throws Exception {
        given(taxConfirmationService.confirm(any())).willThrow(new ResponseStatusException(HttpStatus.CONFLICT,
                "2026-06-02 confirmed already: correct the confirmation instead"));

        postConfirmations(confirmationsJsonWith(lineJson("2026-06-02", "410.73", null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("2026-06-02 confirmed already: correct the confirmation instead"));
    }

    @Test
    void correctsWith204() throws Exception {
        putCorrection(7, "{\"taxAmount\": -12.50, \"note\": \"a shield\"}")
                .andExpect(status().isNoContent());
        verify(taxConfirmationService).correct(eq(7L), any());
    }

    @Test
    void aCorrectionNeedsItsAmount() throws Exception {
        putCorrection(7, "{\"note\": \"a shield\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("taxAmount: must not be null"));
        verifyNoInteractions(taxConfirmationService);
    }

    @Test
    void cancelsWith204() throws Exception {
        mockMvc.perform(delete("/api/taxes/confirmations/7")).andExpect(status().isNoContent());
        verify(taxConfirmationService).cancel(7L);
    }

    @Test
    void anUnknownConfirmationIs404() throws Exception {
        ResponseStatusException notFound = new ResponseStatusException(HttpStatus.NOT_FOUND, "no tax confirmation 99");
        willThrow(notFound).given(taxConfirmationService).correct(eq(99L), any());
        willThrow(notFound).given(taxConfirmationService).cancel(99L);

        putCorrection(99, "{\"taxAmount\": 10}").andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/taxes/confirmations/99")).andExpect(status().isNotFound());
    }

    /** A plain form, or text, is what a page on another site can send without asking. */
    @Test
    void refusesAFormOrTextWith415() throws Exception {
        mockMvc.perform(post("/api/taxes/confirmations").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("settlementDay=2026-06-02&taxAmount=410.73"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(put("/api/taxes/confirmations/7").contentType(MediaType.TEXT_PLAIN)
                        .content("{\"taxAmount\": 10}"))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(taxConfirmationService);
    }

    @Test
    void eachAddressAcceptsOnlyItsMethods() throws Exception {
        mockMvc.perform(delete("/api/taxes")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/taxes/confirmations")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/taxes/confirmations/7")).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions postConfirmations(String json) throws Exception {
        return mockMvc.perform(post("/api/taxes/confirmations").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions putCorrection(long confirmationId, String json) throws Exception {
        return mockMvc.perform(put("/api/taxes/confirmations/" + confirmationId)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String confirmationsJsonWith(String... lines) {
        return "{\"confirmations\": [" + String.join(", ", lines) + "]}";
    }

    private String lineJson(String settlementDay, String taxAmount, String note) {
        String noteJson = note == null ? "null" : "\"" + note + "\"";
        return "{\"settlementDay\": \"%s\", \"taxAmount\": %s, \"note\": %s}".formatted(settlementDay, taxAmount,
                noteJson);
    }

    /**
     * A year of the account with a fee day waiting and a sale day confirmed — a sale in dollars computed and one in Hong
     * Kong dollars not —, and an investor's year.
     */
    private TaxesResponse madeUpTaxes() {
        SharesSoldFromOneBuyResponse sharesFromTheBuy = new SharesSoldFromOneBuyResponse("1001",
                LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 3), new BigDecimal("10"), new BigDecimal("1001"),
                new BigDecimal("1499"), new BigDecimal("3.60"), new BigDecimal("3.50"), new BigDecimal("1743.00"),
                new BigDecimal("1642.90"), new BigDecimal("1642.90"));
        SaleResponse saleInDollars = new SaleResponse("1002", "ABCD", "USD", LocalDate.of(2026, 6, 1),
                SALE_SETTLEMENT_DAY, new BigDecimal("10"), new BigDecimal("150"), BigDecimal.ONE, BigDecimal.ZERO,
                null, new BigDecimal("1642.90"), List.of(sharesFromTheBuy), true);
        SharesSoldFromOneBuyResponse sharesWithoutTheirShekels = new SharesSoldFromOneBuyResponse("1000",
                LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 4), new BigDecimal("100"), new BigDecimal("2000"),
                new BigDecimal("2500"), null, null, null, null, null);
        SaleResponse saleInHongKongDollars = new SaleResponse("1003", "WXYZ", "HKD", LocalDate.of(2026, 6, 1),
                SALE_SETTLEMENT_DAY, new BigDecimal("100"), new BigDecimal("25"), new BigDecimal("3"), BigDecimal.ZERO,
                SaleNotComputedReason.NO_RATE_FOR_THE_CURRENCY, null, List.of(sharesWithoutTheirShekels), false);
        TaxDeductibleExpenseResponse fee = new TaxDeductibleExpenseResponse("3001", "USD", new BigDecimal("-10"),
                "MARKETDATA FOR APR 2026", new BigDecimal("3.70"), new BigDecimal("-37.00"));

        SettlementDayResponse feeDay = new SettlementDayResponse(LocalDate.of(2026, 4, 1), List.of(), List.of(fee),
                new BigDecimal("-37.00"), new BigDecimal("-9.25"), null, SettlementDayStatus.CALCULATED);
        SettlementDayResponse saleDay = new SettlementDayResponse(SALE_SETTLEMENT_DAY,
                List.of(saleInDollars, saleInHongKongDollars), List.of(), new BigDecimal("1642.90"),
                new BigDecimal("410.73"), new TaxConfirmationResponse(7, SALE_SETTLEMENT_DAY, new BigDecimal("400.00"),
                Instant.parse("2026-07-01T08:00:00Z"), "as in Phoenix's report"), SettlementDayStatus.CONFIRMED);
        MonthlySettlementResponse june = new MonthlySettlementResponse(YearMonth.of(2026, 6), new BigDecimal("400.00"),
                new BigDecimal("390.75"), BigDecimal.ZERO, true);
        TaxYearResponse accountsYear = new TaxYearResponse(2026, new BigDecimal("1605.90"), new BigDecimal("401.48"),
                BigDecimal.ZERO, List.of(feeDay, saleDay), List.of(june));

        TaxYearResponse avisYear = new TaxYearResponse(2026, new BigDecimal("652.90"), new BigDecimal("163.23"),
                BigDecimal.ZERO, List.of(), List.of());
        return new TaxesResponse(LocalDate.of(2026, 11, 13), List.of(accountsYear),
                List.of(new InvestorTaxResponse(3, "Avi", List.of(avisYear))), true, LocalDate.of(2026, 11, 13));
    }
}
