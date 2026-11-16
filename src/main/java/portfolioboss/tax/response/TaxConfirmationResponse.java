package portfolioboss.tax.response;

import portfolioboss.tax.TaxConfirmation;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * What the user confirmed for one of the account's settlement days — final (decision 7). The answer of {@code POST
 * /api/taxes/confirmations} too, which gives each new one's id.
 *
 * @param id            the confirmation's id, for {@code PUT} and {@code DELETE /api/taxes/confirmations/{id}}
 * @param settlementDay the day
 * @param taxAmount     what Phoenix recorded, in shekels: positive — tax to pay —, negative — a tax shield
 * @param confirmedAt   when it was confirmed, or last corrected
 * @param note          the user's note; {@code null} when there is none
 */
public record TaxConfirmationResponse(long id, LocalDate settlementDay, BigDecimal taxAmount, Instant confirmedAt,
                                      String note) {

    public TaxConfirmationResponse(TaxConfirmation confirmation) {
        this(confirmation.id(), confirmation.settlementDay(), confirmation.taxAmount(), confirmation.confirmedAt(),
                confirmation.note());
    }
}
