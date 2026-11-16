package portfolioboss.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The account's Flex trades in today's shares (CAPITAL_GAINS_TAX_TODO.md, session 3.0): a reverse split that moved the
 * stock to a new contract restates the trades made before it. Made-up rows in the shape of IB's — the two rows of one
 * action, IB's signs —, none of the account's.
 */
class SplitAdjustedFlexTradesTest {

    private static final int OLD_CON_ID = 1111;
    private static final int NEW_CON_ID = 2222;
    private static final LocalDateTime REVERSE_SPLIT_TIME = LocalDateTime.of(2024, 6, 10, 20, 25, 0);

    /**
     * 500 shares at $8 and 1,500 at $2 under the old contract, then a reverse split of 1 for 4 (−2,000, +500): the buys
     * become 125 at $32 and 375 at $8 of the new contract — the same cost, commission and settlement day —, and the
     * sale of the new contract after it takes from them.
     */
    @Test
    void aReverseSplitRestatesTheOldContractsTradesInTheNewContractsShares() {
        FlexReportTrade firstBuy = tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "500", "8", "-5");
        FlexReportTrade secondBuy = tradeOf("102", OLD_CON_ID, LocalDateTime.of(2023, 4, 3, 11, 0), "1500", "2", "-5");
        FlexReportTrade saleAfter = tradeOf("103", NEW_CON_ID, LocalDateTime.of(2025, 2, 4, 12, 0), "-150", "20", "-1");

        List<TradesOfOneStock> stocks = new SplitAdjustedFlexTrades(List.of(saleAfter, firstBuy, secondBuy),
                reverseSplitOfOneForFour(OLD_CON_ID, NEW_CON_ID, "-2000", "500")).tradesOfEveryStock();

        assertThat(stocks).singleElement().satisfies(stock -> {
            assertThat(stock.conId()).isEqualTo(NEW_CON_ID);
            assertThat(stock.notComputedReasonOfEverySale()).isNull();
            assertThat(stock.tradesInOrder()).extracting(TaxCalculationTrade::tradeId).containsExactly("101", "102", "103");
        });
        List<TaxCalculationTrade> trades = stocks.get(0).tradesInOrder();
        assertThat(trades.get(0).quantity()).isEqualByComparingTo("125");
        assertThat(trades.get(0).price()).isEqualByComparingTo("32");
        assertThat(trades.get(0).commission()).isEqualByComparingTo("5");
        assertThat(trades.get(0).settlementDay()).isEqualTo(firstBuy.settleDate());
        assertThat(trades.get(1).quantity()).isEqualByComparingTo("375");
        assertThat(trades.get(1).price()).isEqualByComparingTo("8");
    }

    /** IB's signs are turned into the calculation's: a side, and a positive quantity and commission. */
    @Test
    void aSaleIsASaleOfAPositiveQuantityAndCommission() {
        FlexReportTrade sale = tradeOf("101", OLD_CON_ID, LocalDateTime.of(2025, 2, 4, 12, 0), "-150", "20", "-1.25");

        TaxCalculationTrade trade = new SplitAdjustedFlexTrades(List.of(sale), List.of())
                .tradesOfEveryStock().get(0).tradesInOrder().get(0);

        assertThat(trade.side()).isEqualTo(TradeSide.SELL);
        assertThat(trade.quantity()).isEqualByComparingTo("150");
        assertThat(trade.commission()).isEqualByComparingTo("1.25");
    }

    @Test
    void aTradeAfterTheSplitStaysAsIbWroteIt() {
        FlexReportTrade buyBefore = tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "2000", "2", "-5");
        FlexReportTrade buyAfter = tradeOf("102", NEW_CON_ID, LocalDateTime.of(2025, 1, 6, 10, 0), "40", "9", "-5");

        List<TaxCalculationTrade> trades = new SplitAdjustedFlexTrades(List.of(buyBefore, buyAfter),
                reverseSplitOfOneForFour(OLD_CON_ID, NEW_CON_ID, "-2000", "500")).tradesOfEveryStock()
                .get(0).tradesInOrder();

        assertThat(trades.get(1).quantity()).isEqualByComparingTo("40");
        assertThat(trades.get(1).price()).isEqualByComparingTo("9");
    }

    /** A reverse split of 1 for 2 into a second contract, then a split of 4 for 1 into a third: 100 at $10 is 200 at $5. */
    @Test
    void twoSplitsInARowRestateThroughBoth() {
        int thirdConId = 3333;
        LocalDateTime laterSplitTime = LocalDateTime.of(2025, 9, 1, 20, 25, 0);
        List<FlexReportCorporateAction> twoActions = List.of(
                corporateActionRowOf("901", "A1", "RS", OLD_CON_ID, REVERSE_SPLIT_TIME, "-100", "0"),
                corporateActionRowOf("902", "A1", "RS", NEW_CON_ID, REVERSE_SPLIT_TIME, "50", "0"),
                corporateActionRowOf("903", "A2", "FS", NEW_CON_ID, laterSplitTime, "-50", "0"),
                corporateActionRowOf("904", "A2", "FS", thirdConId, laterSplitTime, "200", "0"));

        TaxCalculationTrade buy = new SplitAdjustedFlexTrades(
                List.of(tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "100", "10", "-5")),
                twoActions).tradesOfEveryStock().get(0).tradesInOrder().get(0);

        assertThat(buy.quantity()).isEqualByComparingTo("200");
        assertThat(buy.price()).isEqualByComparingTo("5");
    }

    /** Cash for a fraction of a share is money the split brought: not the shape followed, never guessed. */
    @Test
    void anActionWithMoneyIsNotFollowedAndNoneOfItsContractsSalesIsComputed() {
        List<FlexReportCorporateAction> reverseSplitWithCash = List.of(
                corporateActionRowOf("901", "A1", "RS", OLD_CON_ID, REVERSE_SPLIT_TIME, "-2000", "0"),
                corporateActionRowOf("902", "A1", "RS", NEW_CON_ID, REVERSE_SPLIT_TIME, "500", "3.75"));

        List<TradesOfOneStock> stocks = new SplitAdjustedFlexTrades(List.of(
                tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "2000", "2", "-5"),
                tradeOf("102", NEW_CON_ID, LocalDateTime.of(2025, 2, 4, 12, 0), "-150", "20", "-1")),
                reverseSplitWithCash).tradesOfEveryStock();

        assertThat(stocks).extracting(TradesOfOneStock::conId).containsExactly(OLD_CON_ID, NEW_CON_ID);
        assertThat(stocks).allSatisfy(stock -> assertThat(stock.notComputedReasonOfEverySale())
                .isEqualTo(SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED));
        assertThat(stocks.get(0).tradesInOrder().get(0).quantity()).isEqualByComparingTo("2000");
    }

    /** The old contract's trades add up to 1,500 shares, the action takes away 2,000: a buy is missing. */
    @Test
    void anActionTakingAwayOtherSharesThanTheTradesAddUpToIsNotFollowed() {
        List<TradesOfOneStock> stocks = new SplitAdjustedFlexTrades(
                List.of(tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "1500", "2", "-5")),
                reverseSplitOfOneForFour(OLD_CON_ID, NEW_CON_ID, "-2000", "500")).tradesOfEveryStock();

        assertThat(stocks.get(0).notComputedReasonOfEverySale())
                .isEqualTo(SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED);
    }

    @Test
    void currencyConversionsAreLeftOut() {
        FlexReportTrade conversion = new FlexReportTrade("101", "201", 4444, "USD.HKD", "CASH", "HKD",
                LocalDate.of(2025, 11, 17), LocalDateTime.of(2025, 11, 17, 10, 15), LocalDate.of(2025, 11, 19), "BUY",
                new BigDecimal("5000"), new BigDecimal("7.8"), new BigDecimal("-2.5"), "USD", BigDecimal.ZERO,
                "ExchTrade", null);

        assertThat(new SplitAdjustedFlexTrades(List.of(conversion), List.of()).tradesOfEveryStock()).isEmpty();
    }

    /** Within an order IB's seconds don't always follow its ids (seen in a report): the time first, then the id as a number. */
    @Test
    void executionsOfTheSameSecondAreInTheOrderOfIbsIdsAsNumbers() {
        LocalDateTime sameSecond = LocalDateTime.of(2025, 2, 4, 12, 0, 1);
        List<TaxCalculationTrade> trades = new SplitAdjustedFlexTrades(List.of(
                        tradeOf("1000", OLD_CON_ID, sameSecond, "10", "20", "0"),
                        tradeOf("999", OLD_CON_ID, sameSecond, "10", "20", "0"),
                        tradeOf("1001", OLD_CON_ID, sameSecond.minusSeconds(1), "10", "20", "0")),
                List.of()).tradesOfEveryStock().get(0).tradesInOrder();

        assertThat(trades).extracting(TaxCalculationTrade::tradeId).containsExactly("1001", "999", "1000");
    }

    /** A commission in another currency can't be added to the price of a share: no sale of the stock is computed. */
    @Test
    void aCommissionInAnotherCurrencyLeavesEverySaleOfTheStockNotComputed() {
        FlexReportTrade buyChargedInDollars = new FlexReportTrade("101", "201", OLD_CON_ID, "1234", "STK", "HKD",
                LocalDate.of(2025, 3, 3), LocalDateTime.of(2025, 3, 3, 10, 0), LocalDate.of(2025, 3, 5), "BUY",
                new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("-3"), "USD", new BigDecimal("-4000"),
                "ExchTrade", BigDecimal.ZERO);

        TradesOfOneStock stock = new SplitAdjustedFlexTrades(List.of(buyChargedInDollars), List.of())
                .tradesOfEveryStock().get(0);

        assertThat(stock.notComputedReasonOfEverySale())
                .isEqualTo(SaleNotComputedReason.COMMISSION_IN_ANOTHER_CURRENCY);
    }

    private List<FlexReportCorporateAction> reverseSplitOfOneForFour(int oldConId, int newConId, String sharesTakenAway,
                                                                     String sharesAdded) {
        return List.of(
                corporateActionRowOf("901", "A1", "RS", newConId, REVERSE_SPLIT_TIME, sharesAdded, "0"),
                corporateActionRowOf("902", "A1", "RS", oldConId, REVERSE_SPLIT_TIME, sharesTakenAway, "0"));
    }

    private FlexReportCorporateAction corporateActionRowOf(String ibTransactionId, String ibActionId, String actionType,
                                                           int conId, LocalDateTime actionTime, String quantity,
                                                           String proceeds) {
        return new FlexReportCorporateAction(ibTransactionId, ibActionId, actionType, conId, "WXYZ", "STK", actionTime,
                new BigDecimal(quantity), new BigDecimal(proceeds), BigDecimal.ZERO, "WXYZ SPLIT");
    }

    /** A stock execution in dollars with IB's signs — a sale's quantity and every commission negative —, settled a day later. */
    private FlexReportTrade tradeOf(String ibTradeId, int conId, LocalDateTime tradeTime, String signedQuantity,
                                    String price, String ibCommission) {
        BigDecimal quantity = new BigDecimal(signedQuantity);
        return new FlexReportTrade(ibTradeId, "2" + ibTradeId, conId, "WXYZ", "STK", "USD", tradeTime.toLocalDate(),
                tradeTime, tradeTime.toLocalDate().plusDays(1), quantity.signum() < 0 ? "SELL" : "BUY", quantity,
                new BigDecimal(price), new BigDecimal(ibCommission), "USD", BigDecimal.ZERO, "ExchTrade", null);
    }
}
