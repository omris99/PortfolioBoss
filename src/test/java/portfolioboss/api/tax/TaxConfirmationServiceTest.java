package portfolioboss.api.tax;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.tax.request.TaxConfirmationCorrectionRequest;
import portfolioboss.api.tax.request.TaxConfirmationRequest;
import portfolioboss.api.tax.request.TaxConfirmationsRequest;
import portfolioboss.api.tax.response.TaxConfirmationResponse;
import portfolioboss.calculation.tax.TaxConfirmation;
import portfolioboss.db.tax.TaxConfirmationEntity;
import portfolioboss.db.tax.TaxConfirmationRepository;
import portfolioboss.sync.PortfolioSyncService;
import portfolioboss.tax.CapitalGainsTaxService;
import portfolioboss.tax.FlexReportStorageService;
import portfolioboss.tax.TaxTestDataInTheDatabase;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static portfolioboss.tax.TaxTestDataInTheDatabase.FEE_SETTLEMENT_DAY;
import static portfolioboss.tax.TaxTestDataInTheDatabase.SALE_SETTLEMENT_DAY;

/**
 * What {@link TaxConfirmationService} stores, against the real PostgreSQL test database, on the made-up year of
 * {@link TaxTestDataInTheDatabase}: two days with a calculated entry, the fee's on 1 April and the sale's on 2 June.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({TaxConfirmationService.class, CapitalGainsTaxService.class, FlexReportStorageService.class,
        PortfolioSyncService.class, TaxTestDataInTheDatabase.class})
class TaxConfirmationServiceTest {

    private static final long UNKNOWN_CONFIRMATION_ID = 999_999_999L;

    @Autowired
    private TaxConfirmationService taxConfirmationService;

    @Autowired
    private TaxConfirmationRepository taxConfirmationRepository;

    @Autowired
    private TaxTestDataInTheDatabase storedData;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void confirmsADayWithWhatPhoenixRecorded() {
        storedData.storeTheAccountsYear();
        Instant beforeTheConfirmation = Instant.now();

        List<TaxConfirmationResponse> confirmed = taxConfirmationService.confirm(
                requestOf(lineOf(SALE_SETTLEMENT_DAY, "410.73", "  as in the June report  ")));

        assertThat(confirmed).singleElement().satisfies(confirmation -> {
            assertThat(confirmation.settlementDay()).isEqualTo(SALE_SETTLEMENT_DAY);
            assertThat(confirmation.taxAmount()).isEqualByComparingTo("410.73");
            assertThat(confirmation.note()).isEqualTo("as in the June report");
            assertThat(confirmation.confirmedAt()).isAfterOrEqualTo(beforeTheConfirmation);
        });
        assertThat(storedConfirmationOf(confirmed.get(0).id()).taxAmount()).isEqualByComparingTo("410.73");
    }

    /** "Confirm the whole month": several days in one request, a blank note stored as none. */
    @Test
    void confirmsSeveralDaysInOneRequest() {
        storedData.storeTheAccountsYear();

        taxConfirmationService.confirm(requestOf(
                lineOf(FEE_SETTLEMENT_DAY, "-9.25", " "),
                lineOf(SALE_SETTLEMENT_DAY, "410.73", null)));

        assertThat(taxConfirmationRepository.findAllByOrderBySettlementDay())
                .extracting(TaxConfirmationEntity::toTaxConfirmation)
                .extracting(TaxConfirmation::settlementDay, TaxConfirmation::note)
                .containsExactly(tuple(FEE_SETTLEMENT_DAY, null), tuple(SALE_SETTLEMENT_DAY, null));
    }

    @Test
    void aDayConfirmedAlreadyIsAConflictAndNothingOfTheRequestIsStored() {
        storedData.storeTheAccountsYear();
        taxConfirmationService.confirm(requestOf(lineOf(SALE_SETTLEMENT_DAY, "410.73", null)));

        assertThatThrownBy(() -> taxConfirmationService.confirm(requestOf(
                lineOf(FEE_SETTLEMENT_DAY, "-9.25", null),
                lineOf(SALE_SETTLEMENT_DAY, "400.00", null))))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                    assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(refusal.getReason())
                            .isEqualTo("2026-06-02 confirmed already: correct the confirmation instead");
                });
        assertThat(taxConfirmationRepository.findAll()).singleElement()
                .satisfies(confirmation -> assertThat(confirmation.toTaxConfirmation().taxAmount())
                        .isEqualByComparingTo("410.73"));
    }

    /** Phoenix's own fee on 15 May, a day IB's reports have nothing of: not worth a day of its own (the user's call). */
    @Test
    void aDayWithoutACalculatedEntryIsRefused() {
        storedData.storeTheAccountsYear();

        assertThatThrownBy(() -> taxConfirmationService.confirm(requestOf(
                lineOf(SALE_SETTLEMENT_DAY, "410.73", null),
                lineOf(LocalDate.of(2026, 5, 15), "-5.00", "Phoenix's fee"))))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal -> {
                    assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(refusal.getReason()).isEqualTo("no tax is calculated for 2026-05-15: only a settlement "
                            + "day with a calculated entry can be confirmed");
                });
        assertThat(taxConfirmationRepository.findAll()).isEmpty();
    }

    @Test
    void beforeTheFirstSyncNoDayCanBeConfirmed() {
        assertThatThrownBy(() -> taxConfirmationService.confirm(requestOf(lineOf(SALE_SETTLEMENT_DAY, "410.73", null))))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal ->
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void correctsTheAmountAndTheNote() {
        storedData.storeTheAccountsYear();
        TaxConfirmationResponse confirmed =
                taxConfirmationService.confirm(requestOf(lineOf(SALE_SETTLEMENT_DAY, "410.73", null))).get(0);

        taxConfirmationService.correct(confirmed.id(),
                new TaxConfirmationCorrectionRequest(new BigDecimal("400.00"), " Phoenix wrote 400 "));

        TaxConfirmation corrected = storedConfirmationOf(confirmed.id());
        assertThat(corrected.settlementDay()).isEqualTo(SALE_SETTLEMENT_DAY);
        assertThat(corrected.taxAmount()).isEqualByComparingTo("400.00");
        assertThat(corrected.note()).isEqualTo("Phoenix wrote 400");
        assertThat(corrected.confirmedAt()).isAfterOrEqualTo(confirmed.confirmedAt());
    }

    @Test
    void cancelsAConfirmation() {
        storedData.storeTheAccountsYear();
        TaxConfirmationResponse confirmed =
                taxConfirmationService.confirm(requestOf(lineOf(SALE_SETTLEMENT_DAY, "410.73", null))).get(0);

        taxConfirmationService.cancel(confirmed.id());

        assertThat(taxConfirmationRepository.findAll()).isEmpty();
    }

    @Test
    void anUnknownConfirmationIsNotFound() {
        assertThatThrownBy(() -> taxConfirmationService.correct(UNKNOWN_CONFIRMATION_ID,
                new TaxConfirmationCorrectionRequest(BigDecimal.TEN, null)))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal ->
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> taxConfirmationService.cancel(UNKNOWN_CONFIRMATION_ID))
                .isInstanceOfSatisfying(ResponseStatusException.class, refusal ->
                        assertThat(refusal.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    private TaxConfirmationsRequest requestOf(TaxConfirmationRequest... lines) {
        return new TaxConfirmationsRequest(List.of(lines));
    }

    private TaxConfirmationRequest lineOf(LocalDate settlementDay, String taxAmount, String note) {
        return new TaxConfirmationRequest(settlementDay, new BigDecimal(taxAmount), note);
    }

    /** Read again from the table: the persistence context is written out and emptied first. */
    private TaxConfirmation storedConfirmationOf(long confirmationId) {
        entityManager.flush();
        entityManager.clear();
        return taxConfirmationRepository.findById(confirmationId).orElseThrow().toTaxConfirmation();
    }
}
