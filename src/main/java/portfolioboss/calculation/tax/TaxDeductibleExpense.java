package portfolioboss.calculation.tax;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * An expense that lowers the account's taxable gain, and so makes a tax shield of its own (CAPITAL_GAINS_TAX_TODO.md,
 * decision 5): one of IB's Other Fees — the market data fees — in the Flex reports, or IB cancelling one. Phoenix's own
 * fees lower it too, but they are not in IB's reports — a small gap the correction field of session 4 covers.
 *
 * @param ibTransactionId IB's {@code transactionID}
 * @param settlementDay   the day it was settled, whose rate turns it into shekels
 * @param currency        the amount's currency
 * @param amount          with IB's sign: negative for a fee, positive when IB cancelled one
 * @param description     IB's words for it
 */
public record TaxDeductibleExpense(String ibTransactionId, LocalDate settlementDay, String currency, BigDecimal amount,
                            String description) {
}
