package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.calculation.splits.StockSplit;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.flex.FlexReportTrade;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which corporate actions in IB's Flex reports are understood as splits (STOCK_SPLITS_TODO.md, decision 3): IB's words
 * and the shares must agree. Made-up rows in the shape of IB's, none of the account's.
 */
class SplitsInFlexCorporateActionsTest {

    private static final int OLD_CON_ID = 1111;
    private static final int NEW_CON_ID = 2222;
    private static final LocalDateTime ACTION_TIME = LocalDateTime.of(2026, 3, 10, 20, 25);
    private static final LocalDateTime BEFORE_THE_ACTION = LocalDateTime.of(2026, 1, 5, 10, 0);

    /** 10 held, "SPLIT 4 FOR 1", one row adding 30 in the same contract: understood. */
    @Test
    void aSplitInTheSameContractIsUnderstoodWhenTheSharesAddedMatchIbsWords() {
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "FS", NEW_CON_ID, "WXYZ", "30", "WXYZ(US0000000000) SPLIT 4 FOR 1 (WXYZ)")),
                List.of(tradeOf("101", NEW_CON_ID, BEFORE_THE_ACTION, "10")));

        assertThat(splits.splitsUnderstood()).containsExactly(
                new StockSplit("A1", "WXYZ", NEW_CON_ID, NEW_CON_ID, ACTION_TIME, 1, 4));
        assertThat(splits.splitsNotUnderstood()).isEmpty();
    }

    /** 1,000 held, "SPLIT 1 FOR 10": −1,000 of the old contract and +100 of the new — the new symbol is the split's. */
    @Test
    void aSplitIntoANewContractIsUnderstoodWhenBothRowsMatchIbsWords() {
        String words = "WXYZ(US0000000000) SPLIT 1 FOR 10 (WXYZ)";
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "RS", NEW_CON_ID, "WXYZ", "100", words),
                        rowOf("902", "A1", "RS", OLD_CON_ID, "WXYZ.OLD", "-1000", words)),
                List.of(tradeOf("101", OLD_CON_ID, BEFORE_THE_ACTION, "1000")));

        assertThat(splits.splitsUnderstood()).containsExactly(
                new StockSplit("A1", "WXYZ", OLD_CON_ID, NEW_CON_ID, ACTION_TIME, 10, 1));
    }

    /** 10 held, "SPLIT 4 FOR 1" — but 20 added: the words and the shares disagree, so nothing is guessed. */
    @Test
    void sharesThatDontMatchIbsWordsAreNotUnderstood() {
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "FS", NEW_CON_ID, "WXYZ", "20", "WXYZ SPLIT 4 FOR 1")),
                List.of(tradeOf("101", NEW_CON_ID, BEFORE_THE_ACTION, "10")));

        assertThat(splits.splitsUnderstood()).isEmpty();
        assertThat(splits.splitsNotUnderstood()).containsExactly(new SplitNotUnderstood("A1", "WXYZ", ACTION_TIME,
                WhySplitNotUnderstood.SHARES_DONT_MATCH));
    }

    /** The old contract's trades add up to 1,500, the action takes away 2,000: a buy is missing. */
    @Test
    void tradesThatDontAddUpToTheSharesTakenAwayAreNotUnderstood() {
        String words = "WXYZ SPLIT 1 FOR 4";
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "RS", NEW_CON_ID, "WXYZ", "500", words),
                        rowOf("902", "A1", "RS", OLD_CON_ID, "WXYZ.OLD", "-2000", words)),
                List.of(tradeOf("101", OLD_CON_ID, BEFORE_THE_ACTION, "1500")));

        assertThat(splits.splitsNotUnderstood()).extracting(SplitNotUnderstood::reason)
                .containsExactly(WhySplitNotUnderstood.SHARES_DONT_MATCH);
    }

    /** Nothing held before it: a trade is missing, and IB books no split of a stock not held. */
    @Test
    void aSplitOfAStockNotHeldIsNotUnderstood() {
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "FS", NEW_CON_ID, "WXYZ", "30", "WXYZ SPLIT 4 FOR 1")), List.of());

        assertThat(splits.splitsNotUnderstood()).extracting(SplitNotUnderstood::reason)
                .containsExactly(WhySplitNotUnderstood.SHARES_DONT_MATCH);
    }

    @Test
    void ibsWordsWithoutSplitNForMAreNotUnderstood() {
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "FS", NEW_CON_ID, "WXYZ", "30", "WXYZ STOCK DIVIDEND")),
                List.of(tradeOf("101", NEW_CON_ID, BEFORE_THE_ACTION, "10")));

        assertThat(splits.splitsNotUnderstood()).extracting(SplitNotUnderstood::reason)
                .containsExactly(WhySplitNotUnderstood.NO_SPLIT_IN_IBS_WORDS);
    }

    /** Cash for a fraction of a share is money the split brought. */
    @Test
    void anActionWithMoneyIsNotUnderstood() {
        FlexReportCorporateAction withCash = new FlexReportCorporateAction("901", "A1", "RS", NEW_CON_ID, "WXYZ", "STK",
                ACTION_TIME, new BigDecimal("-7"), BigDecimal.ZERO, new BigDecimal("3.75"), "WXYZ SPLIT 1 FOR 4");

        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(List.of(withCash),
                List.of(tradeOf("101", NEW_CON_ID, BEFORE_THE_ACTION, "10")));

        assertThat(splits.splitsNotUnderstood()).extracting(SplitNotUnderstood::reason)
                .containsExactly(WhySplitNotUnderstood.IT_BRINGS_MONEY);
    }

    /** Two rows that both add shares are of no split's kind. */
    @Test
    void rowsOfAnotherKindAreNotUnderstood() {
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "FS", NEW_CON_ID, "WXYZ", "30", "WXYZ SPLIT 4 FOR 1"),
                        rowOf("902", "A1", "FS", OLD_CON_ID, "WXYZ.OLD", "10", "WXYZ SPLIT 4 FOR 1")),
                List.of(tradeOf("101", NEW_CON_ID, BEFORE_THE_ACTION, "10")));

        assertThat(splits.splitsNotUnderstood()).extracting(SplitNotUnderstood::reason)
                .containsExactly(WhySplitNotUnderstood.ROWS_OF_ANOTHER_KIND);
    }

    /** A spin-off is not a split's type: left to the tax, which computes none of its sales, and not printed here. */
    @Test
    void anActionOfAnotherTypeIsNeitherUnderstoodNorReported() {
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(
                List.of(rowOf("901", "A1", "SO", NEW_CON_ID, "WXYZ", "30", "WXYZ SPIN OFF")),
                List.of(tradeOf("101", NEW_CON_ID, BEFORE_THE_ACTION, "10")));

        assertThat(splits.splitsUnderstood()).isEmpty();
        assertThat(splits.splitsNotUnderstood()).isEmpty();
    }

    /** A split of 2 for 1, then one of 4 for 1: the second is checked against the 20 shares the first left. */
    @Test
    void aLaterSplitIsCheckedAgainstTheSharesAnEarlierOneLeft() {
        LocalDateTime laterActionTime = ACTION_TIME.plusMonths(6);
        SplitsInFlexCorporateActions splits = new SplitsInFlexCorporateActions(List.of(
                rowOf("901", "A1", "FS", NEW_CON_ID, "WXYZ", "10", "WXYZ SPLIT 2 FOR 1"),
                new FlexReportCorporateAction("902", "A2", "FS", NEW_CON_ID, "WXYZ", "STK", laterActionTime,
                        new BigDecimal("60"), BigDecimal.ZERO, BigDecimal.ZERO, "WXYZ SPLIT 4 FOR 1")),
                List.of(tradeOf("101", NEW_CON_ID, BEFORE_THE_ACTION, "10")));

        assertThat(splits.splitsUnderstood()).extracting(StockSplit::ibActionId).containsExactly("A1", "A2");
    }

    private FlexReportCorporateAction rowOf(String ibTransactionId, String ibActionId, String actionType, int conId,
                                            String symbol, String quantity, String description) {
        return new FlexReportCorporateAction(ibTransactionId, ibActionId, actionType, conId, symbol, "STK", ACTION_TIME,
                new BigDecimal(quantity), BigDecimal.ZERO, BigDecimal.ZERO, description);
    }

    private FlexReportTrade tradeOf(String ibTradeId, int conId, LocalDateTime tradeTime, String signedQuantity) {
        BigDecimal quantity = new BigDecimal(signedQuantity);
        return new FlexReportTrade(ibTradeId, "2" + ibTradeId, conId, "WXYZ", "STK", "USD", tradeTime.toLocalDate(),
                tradeTime, tradeTime.toLocalDate().plusDays(1), quantity.signum() < 0 ? "SELL" : "BUY", quantity,
                new BigDecimal("10"), new BigDecimal("-5"), "USD", BigDecimal.ZERO, "ExchTrade", null);
    }
}
