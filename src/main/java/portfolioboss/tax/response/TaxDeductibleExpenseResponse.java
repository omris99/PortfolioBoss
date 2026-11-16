package portfolioboss.tax.response;

import java.math.BigDecimal;

/**
 * One of IB's fees that lowers the account's taxable gain (decision 5), or IB cancelling one.
 *
 * @param ibTransactionId IB's {@code transactionID}
 * @param currency        the amount's currency
 * @param amount          with IB's sign: negative for a fee, positive when IB cancelled one
 * @param description     IB's words for it
 * @param rate            shekels for one unit of the currency on its settlement day; {@code null} while unknown — then
 *                        the expense waits, out of the day's entry
 * @param amountInShekels the amount at that rate; {@code null} with it
 */
public record TaxDeductibleExpenseResponse(String ibTransactionId, String currency, BigDecimal amount, String description,
                                           BigDecimal rate, BigDecimal amountInShekels) {
}
