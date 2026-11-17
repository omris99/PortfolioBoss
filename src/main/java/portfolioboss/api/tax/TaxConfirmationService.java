package portfolioboss.api.tax;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.tax.request.TaxConfirmationCorrectionRequest;
import portfolioboss.api.tax.request.TaxConfirmationRequest;
import portfolioboss.api.tax.request.TaxConfirmationsRequest;
import portfolioboss.api.tax.response.TaxConfirmationResponse;
import portfolioboss.calculation.tax.SettlementDayEntry;
import portfolioboss.db.tax.TaxConfirmationEntity;
import portfolioboss.db.tax.TaxConfirmationRepository;
import portfolioboss.tax.CapitalGainsTaxService;
import portfolioboss.utils.Utils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Writes the confirmations of the capital gains tax (CAPITAL_GAINS_TAX_TODO.md, session 4, decision 7): what Phoenix
 * recorded for a settlement day of the account, as the user confirmed it — only what the user entered; the calculated
 * amount is never stored. Only a day the calculation has an entry for can be confirmed: Phoenix's own fees, the one thing
 * it records that IB's reports don't have, are too small to be worth a day of their own (the user's call).
 */
@Service
public class TaxConfirmationService {

    private final TaxConfirmationRepository taxConfirmationRepository;
    private final CapitalGainsTaxService capitalGainsTaxService;

    public TaxConfirmationService(TaxConfirmationRepository taxConfirmationRepository,
                                  CapitalGainsTaxService capitalGainsTaxService) {
        this.taxConfirmationRepository = taxConfirmationRepository;
        this.capitalGainsTaxService = capitalGainsTaxService;
    }

    /**
     * Every day of the request, together or none: 400 when one has no calculated entry, 409 when one is confirmed already
     * — a confirmation is changed by correcting it, not by confirming its day again.
     */
    @Transactional
    public List<TaxConfirmationResponse> confirm(TaxConfirmationsRequest request) {
        List<LocalDate> settlementDays = request.confirmations().stream()
                .map(TaxConfirmationRequest::settlementDay)
                .toList();
        refuseDaysWithoutACalculatedEntry(settlementDays);
        refuseDaysConfirmedAlready(settlementDays);

        Instant confirmedAt = Instant.now();
        List<TaxConfirmationEntity> newConfirmations = request.confirmations().stream()
                .map(confirmation -> new TaxConfirmationEntity(confirmation.settlementDay(), confirmation.taxAmount(),
                        Utils.trimmedOrNull(confirmation.note()), confirmedAt))
                .toList();
        return taxConfirmationRepository.saveAll(newConfirmations).stream()
                .map(TaxConfirmationEntity::toTaxConfirmation)
                .map(TaxConfirmationResponse::new)
                .toList();
    }

    /** The amount and the note as the user typed them now; {@code confirmed_at} becomes now. 404 for an unknown id. */
    @Transactional
    public void correct(long confirmationId, TaxConfirmationCorrectionRequest request) {
        confirmationOrNotFound(confirmationId)
                .correct(request.taxAmount(), Utils.trimmedOrNull(request.note()), Instant.now());
    }

    /** The day waits for its confirmation again. 404 for an unknown id. */
    @Transactional
    public void cancel(long confirmationId) {
        taxConfirmationRepository.delete(confirmationOrNotFound(confirmationId));
    }

    private void refuseDaysWithoutACalculatedEntry(List<LocalDate> settlementDays) {
        Set<LocalDate> calculatedSettlementDays = calculatedSettlementDaysOfTheAccount();
        List<LocalDate> daysWithoutAnEntry = settlementDays.stream()
                .filter(settlementDay -> !calculatedSettlementDays.contains(settlementDay))
                .toList();
        if (!daysWithoutAnEntry.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "no tax is calculated for "
                    + daysInOrder(daysWithoutAnEntry) + ": only a settlement day with a calculated entry can be confirmed");
        }
    }

    private void refuseDaysConfirmedAlready(List<LocalDate> settlementDays) {
        List<LocalDate> daysConfirmedAlready = taxConfirmationRepository.findBySettlementDayIn(settlementDays).stream()
                .map(confirmation -> confirmation.toTaxConfirmation().settlementDay())
                .toList();
        if (!daysConfirmedAlready.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, daysInOrder(daysConfirmedAlready)
                    + " confirmed already: correct the confirmation instead");
        }
    }

    /** Every settlement day the account's calculation has an entry for; none before the first sync. */
    private Set<LocalDate> calculatedSettlementDaysOfTheAccount() {
        return capitalGainsTaxService.capitalGainsTaxOfTheAccountAndInvestors()
                .map(capitalGainsTax -> capitalGainsTax.accountTax().settlementDayEntries().stream()
                        .map(SettlementDayEntry::settlementDay)
                        .collect(Collectors.toSet()))
                .orElse(Set.of());
    }

    private TaxConfirmationEntity confirmationOrNotFound(long confirmationId) {
        return taxConfirmationRepository.findById(confirmationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "no tax confirmation " + confirmationId));
    }

    /** "2026-03-03, 2026-03-10". */
    private String daysInOrder(List<LocalDate> settlementDays) {
        return settlementDays.stream().sorted().map(LocalDate::toString).collect(Collectors.joining(", "));
    }
}
