package portfolioboss.flex;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of a Flex report's Cash Transactions section, as IB wrote it: money in or out of the account that is not a
 * trade — a dividend, tax withheld on it at source, one of IB's Other Fees (market data), or IB cancelling a fee. The
 * amount keeps IB's sign: negative when it left the account, positive when it came in.
 *
 * @param ibTransactionId IB's {@code transactionID}, unique to the row
 * @param transactionType IB's {@code type}: {@code Dividends}, {@code Withholding Tax}, {@code Other Fees}, …
 * @param conId           the stock it belongs to; {@code null} for a fee, which belongs to none
 * @param symbol          that stock's symbol; {@code null} for a fee
 * @param currency        the amount's currency
 * @param amount          the amount, with IB's sign
 * @param transactionTime when IB booked it, in New York's time, as IB writes it
 * @param settleDate      the day it was settled
 * @param description     IB's words for it; {@code null} when IB gives none
 */
public record FlexReportCashTransaction(
        String ibTransactionId,
        String transactionType,
        Integer conId,
        String symbol,
        String currency,
        BigDecimal amount,
        LocalDateTime transactionTime,
        LocalDate settleDate,
        String description) {
}
