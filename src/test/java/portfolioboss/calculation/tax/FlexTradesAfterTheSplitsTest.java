package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.calculation.splits.StockSplit;
import portfolioboss.calculation.splits.StoredStockSplits;
import portfolioboss.flex.FlexReportCorporateAction;
import portfolioboss.flex.FlexReportTrade;
import portfolioboss.model.portfolio.TradeSide;
import portfolioboss.model.tax.SaleNotComputedReason;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The account's Flex trades after the stored splits (CAPITAL_GAINS_TAX_TODO.md, decision 15; STOCK_SPLITS_TODO.md,
 * decision 7): the open position at each split is translated, a position sold in full before it stays as IB wrote it.
 * Made-up rows in the shape of IB's — IB's signs —, none of the account's.
 */
class FlexTradesAfterTheSplitsTest {

    private static final int OLD_CON_ID = 1111;
    private static final int NEW_CON_ID = 2222;
    private static final LocalDateTime REVERSE_SPLIT_TIME = LocalDateTime.of(2024, 6, 10, 20, 25, 0);
    private static final LocalDateTime SPLIT_TIME = LocalDateTime.of(2026, 3, 10, 20, 25, 0);

    /**
     * 500 shares at $8 and 1,500 at $2 under the old contract, then a reverse split of 1 for 4 into a new contract: the
     * buys become 125 at $32 and 375 at $8 of the new contract — the same cost, commission and settlement day —, and the
     * sale of the new contract after it takes from them.
     */
    @Test
    void aReverseSplitIntoANewContractTranslatesTheOldContractsTradesIntoTheNewOnes() {
        FlexReportTrade firstBuy = tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "500", "8", "-5");
        FlexReportTrade secondBuy = tradeOf("102", OLD_CON_ID, LocalDateTime.of(2023, 4, 3, 11, 0), "1500", "2", "-5");
        FlexReportTrade saleAfter = tradeOf("103", NEW_CON_ID, LocalDateTime.of(2025, 2, 4, 12, 0), "-150", "20", "-1");

        List<TradesOfOneStock> stocks = new FlexTradesAfterTheSplits(List.of(saleAfter, firstBuy, secondBuy),
                splits(reverseSplitOfOneForFour()), reverseSplitRowsOfOneForFour()).tradesOfEveryStock();

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

    /** 10 shares at $400, then a split of 4 for 1 in the same contract: 40 at $100, and the sale of 40 after it takes them. */
    @Test
    void aSplitInTheSameContractTranslatesTheOpenPosition() {
        FlexReportTrade buy = tradeOf("101", NEW_CON_ID, LocalDateTime.of(2026, 1, 5, 10, 0), "10", "400", "-5");
        FlexReportTrade saleAfter = tradeOf("102", NEW_CON_ID, LocalDateTime.of(2026, 4, 1, 10, 0), "-40", "110", "-5");

        List<TradeIdQuantityAndPrice> trades = tradesOf(new FlexTradesAfterTheSplits(List.of(buy, saleAfter),
                splits(splitOfFourForOne()), List.of(splitRowOfFourForOne("30"))));

        assertThat(trades).containsExactly(
                new TradeIdQuantityAndPrice("101", new BigDecimal("40"), new BigDecimal("100")),
                new TradeIdQuantityAndPrice("102", new BigDecimal("40"), new BigDecimal("110")));
    }

    /**
     * Bought 10 and sold all 10 in 2025, bought 5 at $500 in 2026, then the split: only the 5 held at it are translated
     * — 20 at $125 —; the position sold in full before it stays as on the broker's statement of its day.
     */
    @Test
    void aPositionSoldInFullBeforeTheSplitStaysAsIbWroteIt() {
        List<FlexReportTrade> flexTrades = List.of(
                tradeOf("101", NEW_CON_ID, LocalDateTime.of(2025, 2, 3, 10, 0), "10", "400", "-5"),
                tradeOf("102", NEW_CON_ID, LocalDateTime.of(2025, 6, 2, 10, 0), "-10", "450", "-5"),
                tradeOf("103", NEW_CON_ID, LocalDateTime.of(2026, 1, 5, 10, 0), "5", "500", "-5"));

        List<TradeIdQuantityAndPrice> trades = tradesOf(new FlexTradesAfterTheSplits(flexTrades,
                splits(splitOfFourForOne()), List.of(splitRowOfFourForOne("15"))));

        assertThat(trades).containsExactly(
                new TradeIdQuantityAndPrice("101", new BigDecimal("10"), new BigDecimal("400")),
                new TradeIdQuantityAndPrice("102", new BigDecimal("10"), new BigDecimal("450")),
                new TradeIdQuantityAndPrice("103", new BigDecimal("20"), new BigDecimal("125")));
    }

    /** A sale of part of the shares before the split is of the open position: translated with it, so it adds up. */
    @Test
    void aPartOfTheOpenPositionSoldBeforeTheSplitIsTranslatedWithIt() {
        List<FlexReportTrade> flexTrades = List.of(
                tradeOf("101", NEW_CON_ID, LocalDateTime.of(2026, 1, 5, 10, 0), "10", "400", "-5"),
                tradeOf("102", NEW_CON_ID, LocalDateTime.of(2026, 2, 2, 10, 0), "-4", "420", "-5"));

        List<TradeIdQuantityAndPrice> trades = tradesOf(new FlexTradesAfterTheSplits(flexTrades,
                splits(splitOfFourForOne()), List.of(splitRowOfFourForOne("18"))));

        assertThat(trades).containsExactly(
                new TradeIdQuantityAndPrice("101", new BigDecimal("40"), new BigDecimal("100")),
                new TradeIdQuantityAndPrice("102", new BigDecimal("16"), new BigDecimal("105")));
    }

    /** IB's signs are turned into the calculation's: a side, and a positive quantity and commission. */
    @Test
    void aSaleIsASaleOfAPositiveQuantityAndCommission() {
        FlexReportTrade sale = tradeOf("101", OLD_CON_ID, LocalDateTime.of(2025, 2, 4, 12, 0), "-150", "20", "-1.25");

        TaxCalculationTrade trade = new FlexTradesAfterTheSplits(List.of(sale), new StoredStockSplits(), List.of())
                .tradesOfEveryStock().get(0).tradesInOrder().get(0);

        assertThat(trade.side()).isEqualTo(TradeSide.SELL);
        assertThat(trade.quantity()).isEqualByComparingTo("150");
        assertThat(trade.commission()).isEqualByComparingTo("1.25");
    }

    @Test
    void aTradeAfterTheSplitStaysAsIbWroteIt() {
        FlexReportTrade buyBefore = tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "2000", "2", "-5");
        FlexReportTrade buyAfter = tradeOf("102", NEW_CON_ID, LocalDateTime.of(2025, 1, 6, 10, 0), "40", "9", "-5");

        List<TaxCalculationTrade> trades = new FlexTradesAfterTheSplits(List.of(buyBefore, buyAfter),
                splits(reverseSplitOfOneForFour()), reverseSplitRowsOfOneForFour()).tradesOfEveryStock()
                .get(0).tradesInOrder();

        assertThat(trades.get(1).quantity()).isEqualByComparingTo("40");
        assertThat(trades.get(1).price()).isEqualByComparingTo("9");
    }

    /** A reverse split of 1 for 2 into a second contract, then a split of 4 for 1 into a third: 100 at $10 is 200 at $5. */
    @Test
    void twoSplitsInARowTranslateThroughBoth() {
        int thirdConId = 3333;
        LocalDateTime laterSplitTime = LocalDateTime.of(2025, 9, 1, 20, 25, 0);
        StoredStockSplits twoSplits = splits(
                new StockSplit("A1", "WXYZ", OLD_CON_ID, NEW_CON_ID, REVERSE_SPLIT_TIME, 2, 1),
                new StockSplit("A2", "WXYZ", NEW_CON_ID, thirdConId, laterSplitTime, 1, 4));

        TaxCalculationTrade buy = new FlexTradesAfterTheSplits(
                List.of(tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "100", "10", "-5")),
                twoSplits, List.of()).tradesOfEveryStock().get(0).tradesInOrder().get(0);

        assertThat(buy.quantity()).isEqualByComparingTo("200");
        assertThat(buy.price()).isEqualByComparingTo("5");
    }

    /** An action not stored as a split — cash for a fraction of a share, say —: never guessed. */
    @Test
    void anActionNotStoredAsASplitLeavesTheSalesOfItsContractsNotComputed() {
        List<TradesOfOneStock> stocks = new FlexTradesAfterTheSplits(List.of(
                tradeOf("101", OLD_CON_ID, LocalDateTime.of(2022, 3, 1, 10, 0), "2000", "2", "-5"),
                tradeOf("102", NEW_CON_ID, LocalDateTime.of(2025, 2, 4, 12, 0), "-150", "20", "-1")),
                new StoredStockSplits(), reverseSplitRowsOfOneForFour()).tradesOfEveryStock();

        assertThat(stocks).extracting(TradesOfOneStock::conId).containsExactly(OLD_CON_ID, NEW_CON_ID);
        assertThat(stocks).allSatisfy(stock -> assertThat(stock.notComputedReasonOfEverySale())
                .isEqualTo(SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED));
        assertThat(stocks.get(0).tradesInOrder().get(0).quantity()).isEqualByComparingTo("2000");
    }

    /** Bought 10, sold 4: 6 held just before the moment; a trade at the moment itself isn't before it. */
    @Test
    void theSharesHeldJustBeforeAMomentAreWhatTheTradesBeforeItAddUpTo() {
        FlexTradesAfterTheSplits trades = new FlexTradesAfterTheSplits(List.of(
                tradeOf("101", NEW_CON_ID, LocalDateTime.of(2026, 1, 5, 10, 0), "10", "400", "-5"),
                tradeOf("102", NEW_CON_ID, LocalDateTime.of(2026, 2, 2, 10, 0), "-4", "420", "-5"),
                tradeOf("103", NEW_CON_ID, SPLIT_TIME, "100", "100", "-5"),
                tradeOf("104", OLD_CON_ID, LocalDateTime.of(2026, 1, 5, 10, 0), "7", "50", "-5")),
                new StoredStockSplits(), List.of());

        assertThat(trades.sharesHeldJustBefore(NEW_CON_ID, SPLIT_TIME)).isEqualByComparingTo("6");
    }

    @Test
    void currencyConversionsAreLeftOut() {
        FlexReportTrade conversion = new FlexReportTrade("101", "201", 4444, "USD.HKD", "CASH", "HKD",
                LocalDate.of(2025, 11, 17), LocalDateTime.of(2025, 11, 17, 10, 15), LocalDate.of(2025, 11, 19), "BUY",
                new BigDecimal("5000"), new BigDecimal("7.8"), new BigDecimal("-2.5"), "USD", BigDecimal.ZERO,
                "ExchTrade", null);

        assertThat(new FlexTradesAfterTheSplits(List.of(conversion), new StoredStockSplits(), List.of())
                .tradesOfEveryStock()).isEmpty();
    }

    /** Within an order IB's seconds don't always follow its ids (seen in a report): the time first, then the id as a number. */
    @Test
    void executionsOfTheSameSecondAreInTheOrderOfIbsIdsAsNumbers() {
        LocalDateTime sameSecond = LocalDateTime.of(2025, 2, 4, 12, 0, 1);
        List<TaxCalculationTrade> trades = new FlexTradesAfterTheSplits(List.of(
                        tradeOf("1000", OLD_CON_ID, sameSecond, "10", "20", "0"),
                        tradeOf("999", OLD_CON_ID, sameSecond, "10", "20", "0"),
                        tradeOf("1001", OLD_CON_ID, sameSecond.minusSeconds(1), "10", "20", "0")),
                new StoredStockSplits(), List.of()).tradesOfEveryStock().get(0).tradesInOrder();

        assertThat(trades).extracting(TaxCalculationTrade::tradeId).containsExactly("1001", "999", "1000");
    }

    /** A commission in another currency can't be added to the price of a share: no sale of the stock is computed. */
    @Test
    void aCommissionInAnotherCurrencyLeavesEverySaleOfTheStockNotComputed() {
        FlexReportTrade buyChargedInDollars = new FlexReportTrade("101", "201", OLD_CON_ID, "1234", "STK", "HKD",
                LocalDate.of(2025, 3, 3), LocalDateTime.of(2025, 3, 3, 10, 0), LocalDate.of(2025, 3, 5), "BUY",
                new BigDecimal("100"), new BigDecimal("40"), new BigDecimal("-3"), "USD", new BigDecimal("-4000"),
                "ExchTrade", BigDecimal.ZERO);

        TradesOfOneStock stock = new FlexTradesAfterTheSplits(List.of(buyChargedInDollars), new StoredStockSplits(),
                List.of()).tradesOfEveryStock().get(0);

        assertThat(stock.notComputedReasonOfEverySale())
                .isEqualTo(SaleNotComputedReason.COMMISSION_IN_ANOTHER_CURRENCY);
    }

    /** A trade as the test compares it: its IB id, its shares and its price. */
    private record TradeIdQuantityAndPrice(String tradeId, BigDecimal quantity, BigDecimal price) {

        /** {@code equals} of a {@code BigDecimal} counts its decimal places: 40 and 40.00 would differ. */
        private TradeIdQuantityAndPrice {
            quantity = quantity.stripTrailingZeros();
            price = price.stripTrailingZeros();
        }
    }

    private List<TradeIdQuantityAndPrice> tradesOf(FlexTradesAfterTheSplits flexTradesAfterTheSplits) {
        return flexTradesAfterTheSplits.tradesOfEveryStock().stream()
                .flatMap(stock -> stock.tradesInOrder().stream())
                .map(trade -> new TradeIdQuantityAndPrice(trade.tradeId(), trade.quantity(), trade.price()))
                .toList();
    }

    private StoredStockSplits splits(StockSplit... splits) {
        return new StoredStockSplits(List.of(splits));
    }

    private StockSplit reverseSplitOfOneForFour() {
        return new StockSplit("A1", "WXYZ", OLD_CON_ID, NEW_CON_ID, REVERSE_SPLIT_TIME, 4, 1);
    }

    private List<FlexReportCorporateAction> reverseSplitRowsOfOneForFour() {
        return List.of(
                corporateActionRowOf("901", "A1", "RS", NEW_CON_ID, REVERSE_SPLIT_TIME, "500"),
                corporateActionRowOf("902", "A1", "RS", OLD_CON_ID, REVERSE_SPLIT_TIME, "-2000"));
    }

    private StockSplit splitOfFourForOne() {
        return new StockSplit("A3", "WXYZ", NEW_CON_ID, NEW_CON_ID, SPLIT_TIME, 1, 4);
    }

    private FlexReportCorporateAction splitRowOfFourForOne(String sharesAdded) {
        return corporateActionRowOf("903", "A3", "FS", NEW_CON_ID, SPLIT_TIME, sharesAdded);
    }

    private FlexReportCorporateAction corporateActionRowOf(String ibTransactionId, String ibActionId, String actionType,
                                                           int conId, LocalDateTime actionTime, String quantity) {
        return new FlexReportCorporateAction(ibTransactionId, ibActionId, actionType, conId, "WXYZ", "STK", actionTime,
                new BigDecimal(quantity), BigDecimal.ZERO, BigDecimal.ZERO, "WXYZ SPLIT");
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
