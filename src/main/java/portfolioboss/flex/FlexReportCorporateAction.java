package portfolioboss.flex;


import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of a Flex report's Corporate Actions section, as IB wrote it (CAPITAL_GAINS_TAX_TODO.md, decision 15) — a split,
 * a reverse split, a change of contract. IB writes one action as several rows sharing an {@code actionID}: a reverse
 * split of 1 for 4 is the old contract's shares leaving ({@code quantity} −2,000) and the new contract's coming in
 * (+500), at the same time. Which of it the tax can follow is {@code SplitAdjustedFlexTrades}'s question, not this
 * record's.
 *
 * @param ibTransactionId IB's {@code transactionID}, unique to the row
 * @param ibActionId      IB's {@code actionID}: the rows of one action share it
 * @param actionType      IB's {@code type}: {@code RS} for a reverse split, {@code FS} for a split, …
 * @param conId           the contract the row moves shares of
 * @param symbol          that contract's symbol, as IB names it in the row ({@code ABCD.OLD} for the old one)
 * @param assetCategory   {@code STK} for a stock
 * @param actionTime      when the action happened, in New York's time, as IB writes it
 * @param quantity        the shares the row adds (positive) or takes away (negative)
 * @param proceeds        money the action brought, as IB reports it — 0 for a split
 * @param amount          the action's cash amount, as IB reports it — 0 for a split
 * @param description     IB's words for it: "ABCD(US0000000000) SPLIT 1 FOR 4 (…)"; {@code null} when IB gives none
 */
public record FlexReportCorporateAction(
        String ibTransactionId,
        String ibActionId,
        String actionType,
        int conId,
        String symbol,
        String assetCategory,
        LocalDateTime actionTime,
        BigDecimal quantity,
        BigDecimal proceeds,
        BigDecimal amount,
        String description) {
}
