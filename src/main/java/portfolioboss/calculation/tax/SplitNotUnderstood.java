package portfolioboss.calculation.tax;

import java.time.LocalDateTime;

/**
 * A corporate action of a split's type in IB's Flex reports that was not understood as a split
 * ({@link SplitsInFlexCorporateActions}): not stored, and printed on every sync.
 *
 * @param ibActionId IB's {@code actionID}
 * @param symbol     the stock's symbol, as IB names it in the action's first row
 * @param actionTime when IB booked the action, in New York's time
 * @param reason     why it was not understood
 */
public record SplitNotUnderstood(String ibActionId, String symbol, LocalDateTime actionTime,
                                 WhySplitNotUnderstood reason) {
}
