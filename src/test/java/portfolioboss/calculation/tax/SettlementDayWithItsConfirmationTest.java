package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.model.tax.SettlementDayStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.calculation.tax.TaxTestData.entryWithTaxableGainOf;

/**
 * A settlement day and what the user confirmed for it (CAPITAL_GAINS_TAX_TODO.md, decision 7): waiting, or confirmed —
 * and a confirmed amount is final. Made-up figures: a taxable gain of 400, so a calculated tax of 100.
 */
class SettlementDayWithItsConfirmationTest {

    private static final LocalDate SETTLEMENT_DAY = LocalDate.of(2026, 3, 3);
    private static final SettlementDayEntry CALCULATED_TAX_OF_100 = entryWithTaxableGainOf("400", SETTLEMENT_DAY);

    @Test
    void aDayWaitingForItsConfirmationCountsWithItsCalculatedAmount() {
        SettlementDayWithItsConfirmation settlementDay = new SettlementDayWithItsConfirmation(CALCULATED_TAX_OF_100, null);

        assertThat(settlementDay.isConfirmed()).isFalse();
        assertThat(settlementDay.recordedTaxAmount()).isEqualByComparingTo("100.00");
        assertThat(settlementDay.status()).isEqualTo(SettlementDayStatus.CALCULATED);
    }

    @Test
    void aConfirmedDayCountsWithTheConfirmedAmountEvenWhenItIsNotTheCalculatedOne() {
        SettlementDayWithItsConfirmation settlementDay = new SettlementDayWithItsConfirmation(CALCULATED_TAX_OF_100,
                confirmationOf("99.99"));

        assertThat(settlementDay.isConfirmed()).isTrue();
        assertThat(settlementDay.recordedTaxAmount()).isEqualByComparingTo("99.99");
        assertThat(settlementDay.status()).isEqualTo(SettlementDayStatus.CONFIRMED);
    }

    /** A day of a sale in Hong Kong dollars, once the user typed what Phoenix recorded: confirmed, nothing left to do. */
    @Test
    void aDayThatCannotBeCalculatedIsConfirmedOnceItsAmountIsTypedIn() {
        SettlementDayEntry saleWithoutRates = new SettlementDayEntry(SETTLEMENT_DAY,
                List.of(new SaleTaxableGain(CALCULATED_TAX_OF_100.sales().get(0).fifoSale(), null, Map.of())),
                List.of(), Map.of());

        SettlementDayWithItsConfirmation settlementDay =
                new SettlementDayWithItsConfirmation(saleWithoutRates, confirmationOf("182.40"));

        assertThat(saleWithoutRates.status()).isEqualTo(SettlementDayStatus.CANNOT_BE_CALCULATED);
        assertThat(settlementDay.status()).isEqualTo(SettlementDayStatus.CONFIRMED);
    }

    /** A shield confirmed on a day calculated as a tax: Phoenix's word is the one that counts. */
    @Test
    void aConfirmedShieldCountsAsAShield() {
        SettlementDayWithItsConfirmation settlementDay = new SettlementDayWithItsConfirmation(CALCULATED_TAX_OF_100,
                confirmationOf("-20.50"));

        assertThat(settlementDay.recordedTaxAmount()).isEqualByComparingTo("-20.50");
    }

    private TaxConfirmation confirmationOf(String taxAmount) {
        return new TaxConfirmation(1, SETTLEMENT_DAY, new BigDecimal(taxAmount), Instant.parse("2026-04-02T08:00:00Z"),
                null);
    }
}
