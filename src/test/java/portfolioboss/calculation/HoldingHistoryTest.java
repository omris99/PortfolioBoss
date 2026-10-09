package portfolioboss.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Pure computation — no Spring, no database. Trades are entered here in whatever order the scenario reads best; {@link HoldingHistory#of} sorts them itself. */
class HoldingHistoryTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 9, 22);
    private static final long INVESTOR_ID = 1;

    @Test
    void everythingIsNullWithNoTrades() {
        HoldingHistory history = HoldingHistory.of(List.of());

        assertThat(history.firstBuyDate()).isNull();
        assertThat(history.lastSellDate()).isNull();
        assertThat(history.holdingDays(HoldingStatus.OPEN, SNAPSHOT_DATE)).isNull();
    }

    @Test
    void oneBuyGivesAFirstBuyDateAndCountsToTheSnapshotDate() {
        HoldingHistory history = HoldingHistory.of(List.of(buy("2026-01-01", 10)));

        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(history.lastSellDate()).isNull();
        assertThat(history.holdingDays(HoldingStatus.OPEN, SNAPSHOT_DATE)).isEqualTo(264);
    }

    @Test
    void aPartialSellKeepsTheFirstBuyDateAndKeepsCountingWhileOpen() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10),
                sell("2026-02-01", 4)));

        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(history.lastSellDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(history.netQuantity()).isEqualByComparingTo("6");
        assertThat(history.holdingDays(HoldingStatus.OPEN, SNAPSHOT_DATE)).isEqualTo(264);
    }

    @Test
    void aFullSellClosesThePositionPeriodAndHoldingDaysRunsToTheSellDate() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10),
                sell("2026-03-01", 10)));

        assertThat(history.netQuantity()).isEqualByComparingTo("0");
        assertThat(history.holdingDays(HoldingStatus.CLOSED, SNAPSHOT_DATE)).isEqualTo(59);
    }

    @Test
    void sellingInFullAndBuyingAgainStartsANewPositionPeriod() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2023-03-01", 10),
                sell("2024-01-01", 10),
                buy("2024-05-01", 5)));

        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(history.lastSellDate()).isNull();
        assertThat(history.netQuantity()).isEqualByComparingTo("5");
    }

    @Test
    void twoBuysKeepTheEarlierDateAsTheFirstBuyDate() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-02-01", 5),
                buy("2026-01-01", 5)));   // entered out of order on purpose

        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(history.netQuantity()).isEqualByComparingTo("10");
    }

    @Test
    void aBuyAndASellOnTheSameDateProcessTheBuyFirst() {
        HoldingHistory history = HoldingHistory.of(List.of(
                sell("2026-01-01", 10),
                buy("2026-01-01", 10)));

        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(history.lastSellDate()).isEqualTo(LocalDate.of(2026, 1, 1));
        assertThat(history.netQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void aBuyDatedAfterTheLastSyncCountsAsZeroDaysNotNegative() {
        HoldingHistory history = HoldingHistory.of(List.of(buy("2026-09-25", 10)));

        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2026, 9, 25));
        assertThat(history.holdingDays(HoldingStatus.OPEN, SNAPSHOT_DATE)).isZero();
    }

    @Test
    void aClosedHoldingWithNoSellEnteredHasNoHoldingDays() {
        HoldingHistory history = HoldingHistory.of(List.of(buy("2026-01-01", 10)));

        assertThat(history.holdingDays(HoldingStatus.CLOSED, SNAPSHOT_DATE)).isNull();
    }

    @Test
    void partialQuantitiesThatSumToZeroCloseThePositionPeriod() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", "10.5"),
                sell("2026-02-01", "10.5")));

        assertThat(history.netQuantity()).isEqualByComparingTo("0");
        assertThat(history.holdingDays(HoldingStatus.CLOSED, SNAPSHOT_DATE)).isEqualTo(31);
    }

    @Test
    void aSellWithNoPriorBuyIsIgnoredByTheDates() {
        HoldingHistory history = HoldingHistory.of(List.of(sell("2026-01-01", 10)));

        assertThat(history.firstBuyDate()).isNull();
        assertThat(history.lastSellDate()).isNull();
        assertThat(history.netQuantity()).isEqualByComparingTo("-10");   // still counted, for the check against IB
    }

    @Test
    void aSecondSellAfterThePositionIsAlreadyFlatIsIgnoredByTheDates() {
        // a duplicate/mistaken sell entry after the position was already fully sold
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10),
                sell("2026-02-01", 10),
                sell("2026-02-15", 3)));

        assertThat(history.lastSellDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(history.netQuantity()).isEqualByComparingTo("-3");   // still counted, for the check against IB
    }

    // ── warnings ────────────────────────────────────────────────────────────────────────────────

    @Test
    void noBuyEnteredWarnsNoTradesLoggedAndNothingElse() {
        // IB reports 100 and the log adds up to 0, but the missing buys are one gap, reported once
        HoldingHistory history = HoldingHistory.of(List.of());

        assertThat(history.warnings(HoldingStatus.OPEN, 100))
                .extracting(HoldingWarning::type)
                .containsExactly(HoldingWarningType.NO_TRADES_LOGGED);
    }

    @Test
    void tradesAddingUpToIbsQuantityGiveNoWarning() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2025-01-01", 60),
                buy("2025-06-01", 50),
                sell("2026-01-01", 10)));

        assertThat(history.warnings(HoldingStatus.OPEN, 100)).isEmpty();
    }

    @Test
    void tradesAddingUpToLessThanIbReportsWarnQuantityMismatch() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2025-01-01", 60),
                buy("2025-06-01", 30)));

        assertThat(history.warnings(HoldingStatus.OPEN, 100)).containsExactly(new HoldingWarning(
                HoldingWarningType.QUANTITY_MISMATCH, "The trades entered add up to 90 shares; IB reports 100."));
    }

    @Test
    void aDifferenceWithinTheToleranceIsNotAMismatch() {
        HoldingHistory history = HoldingHistory.of(List.of(buy("2026-01-01", "0.0523")));

        assertThat(history.warnings(HoldingStatus.OPEN, 0.05234)).isEmpty();
        assertThat(history.warnings(HoldingStatus.OPEN, 0.0525))
                .extracting(HoldingWarning::type)
                .containsExactly(HoldingWarningType.QUANTITY_MISMATCH);
    }

    @Test
    void aClosedHoldingWithNoSellEnteredWarnsClosedWithoutSellAndNothingElse() {
        // the log adds up to 10 against IB's 0 as well, but the missing sell is one gap, reported once
        HoldingHistory history = HoldingHistory.of(List.of(buy("2026-01-01", 10)));

        assertThat(history.warnings(HoldingStatus.CLOSED, 0))
                .extracting(HoldingWarning::type)
                .containsExactly(HoldingWarningType.CLOSED_WITHOUT_SELL);
    }

    @Test
    void aClosedHoldingSoldInFullGivesNoWarning() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10),
                sell("2026-03-01", 10)));

        assertThat(history.warnings(HoldingStatus.CLOSED, 0)).isEmpty();
    }

    @Test
    void aClosedHoldingSoldOnlyInPartWarnsQuantityMismatch() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10),
                sell("2026-03-01", 4)));

        assertThat(history.warnings(HoldingStatus.CLOSED, 0)).containsExactly(new HoldingWarning(
                HoldingWarningType.QUANTITY_MISMATCH, "The trades entered add up to 6 shares; IB reports 0."));
    }

    @Test
    void aSellTheDatesIgnoreIsStillCaughtByTheQuantityCheck() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2023-03-01", 10),
                sell("2024-01-01", 10),
                sell("2024-02-01", 3),    // entered by mistake: the position is already flat
                buy("2024-05-01", 5)));

        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2024, 5, 1));
        assertThat(history.warnings(HoldingStatus.OPEN, 5)).containsExactly(new HoldingWarning(
                HoldingWarningType.QUANTITY_MISMATCH, "The trades entered add up to 2 shares; IB reports 5."));
    }

    // ── closed positions ────────────────────────────────────────────────────────────────────────

    @Test
    void aPositionNeverSoldHasNoClosedPosition() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10, "150"),
                buy("2026-02-01", 5, "160")));

        assertThat(history.closedPositions()).isEmpty();
    }

    @Test
    void aPartialSellIsAClosedPositionOfTheSharesSoldWithTheRestStillHeld() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10, "150"),
                sell("2026-02-01", 4, "160")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.openDate()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2026, 2, 1));
            assertThat(closedPosition.boughtQuantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.soldQuantity()).isEqualByComparingTo("4");
            assertThat(closedPosition.soldCost()).isEqualByComparingTo("600");       // 4 × 150
            assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("640");   // 4 × 160
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("6");
        });
    }

    /** The plan's example (decision 9): the 10 sold cost the average of all 20 held, 150 — not the first 10's 100. */
    @Test
    void sharesSoldCostTheAverageOfTheSharesHeldAtTheTime() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10, "100"),
                buy("2026-03-01", 10, "200"),
                sell("2026-05-01", 10, "180")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.soldCost()).isEqualByComparingTo("1500");
            assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("300");
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("10");
        });
    }

    /**
     * The first sell takes half the cost (500); the 5 left and the 10 bought after it then average 166.67, and the last
     * sell takes all that is left (2,500) — the same total as all the proceeds less all the cost, with no rounding.
     */
    @Test
    void aBuyBetweenTwoSellsChangesTheAverageOfTheSecond() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                sell("2024-02-01", 5, "150"),
                buy("2024-03-01", 10, "200"),
                sell("2024-04-01", 15, "180")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.soldQuantity()).isEqualByComparingTo("20");
            assertThat(closedPosition.soldCost()).isEqualByComparingTo("3000");   // exactly, not 2999.99…
            assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("3450");
            assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("450");
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("0");
        });
    }

    @Test
    void aFullSellBecomesAClosedPositionWithItsTotals() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-03-01", 10, "150"),
                sell("2025-06-01", 10, "180")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.openDate()).isEqualTo(LocalDate.of(2024, 3, 1));
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2025, 6, 1));
            assertThat(closedPosition.boughtQuantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.soldCost()).isEqualByComparingTo("1500");
            assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("1800");
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("0");
        });
    }

    @Test
    void buysAndPartialSellsUntilTheQuantityIsZeroAreOneClosedPosition() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                buy("2024-02-01", 10, "120"),
                sell("2024-03-01", 5, "130"),
                sell("2024-04-01", 15, "140")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.openDate()).isEqualTo(LocalDate.of(2024, 1, 1));
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2024, 4, 1));
            assertThat(closedPosition.boughtQuantity()).isEqualByComparingTo("20");
            assertThat(closedPosition.soldQuantity()).isEqualByComparingTo("20");    // 5 + 15
            assertThat(closedPosition.soldCost()).isEqualByComparingTo("2200");       // 1,000 + 1,200
            assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("2750");   // 650 + 2,100
        });
    }

    @Test
    void sellingInFullAndBuyingAgainLeavesOneClosedPositionAndTheDatesOfTheNewPeriod() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-03-01", 10, "150"),
                sell("2025-06-01", 10, "180"),
                buy("2026-02-01", 5, "200")));

        assertThat(history.closedPositions())
                .extracting(ClosedPosition::openDate, ClosedPosition::closeDate)
                .containsExactly(tuple(LocalDate.of(2024, 3, 1), LocalDate.of(2025, 6, 1)));
        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(history.lastSellDate()).isNull();
    }

    @Test
    void eachFullSellClosesItsOwnPositionOldestFirst() {
        HoldingHistory history = HoldingHistory.of(List.of(
                sell("2025-03-01", 5, "90"),    // entered out of order on purpose
                buy("2023-01-01", 10, "50"),
                sell("2023-06-01", 10, "70"),
                buy("2024-01-01", 5, "80")));

        assertThat(history.closedPositions())
                .extracting(ClosedPosition::openDate, ClosedPosition::closeDate)
                .containsExactly(
                        tuple(LocalDate.of(2023, 1, 1), LocalDate.of(2023, 6, 1)),
                        tuple(LocalDate.of(2024, 1, 1), LocalDate.of(2025, 3, 1)));
    }

    @Test
    void aTradeWithNoPriceLeavesOnlyItsOwnSidesTotalUnknown() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                buy("2024-02-01", 10),           // no price entered
                sell("2024-03-01", 20, "120")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.soldCost()).isNull();
            assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("2400");
        });
    }

    /** The shares sold left before the buy with no price came in, so what they cost is still known. */
    @Test
    void aBuyWithNoPriceAfterAPartialSellLeavesWhatWasSoldKnown() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                sell("2024-02-01", 5, "150"),
                buy("2024-03-01", 5)));          // no price entered

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.soldCost()).isEqualByComparingTo("500");
            assertThat(closedPosition.remainingQuantity()).isEqualByComparingTo("10");
        });
    }

    @Test
    void sellingMoreThanWasBoughtStillClosesThePositionPeriod() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                sell("2024-06-01", 12, "120"),   // a data-entry mistake: 2 more than were bought
                buy("2025-01-01", 5, "130")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2024, 6, 1));
            assertThat(closedPosition.boughtQuantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.soldQuantity()).isEqualByComparingTo("12");
            assertThat(closedPosition.soldCost()).isEqualByComparingTo("1000");   // all that was bought
            assertThat(closedPosition.soldMoreThanBought()).isTrue();
        });
        assertThat(history.firstBuyDate()).isEqualTo(LocalDate.of(2025, 1, 1));
        assertThat(history.netQuantity()).isEqualByComparingTo("3");   // so the check against IB still shows it
    }

    @Test
    void theCommissionsOfEveryBuyAndSellOfThePeriodAddUp() {
        HoldingHistory history = HoldingHistory.of(List.of(
                trade("2024-01-01", TradeSide.BUY, "10", "100", "5"),
                trade("2024-02-01", TradeSide.BUY, "600", "100", "6"),
                trade("2024-03-01", TradeSide.SELL, "610", "120", "6.10"),
                trade("2025-01-01", TradeSide.BUY, "5", "130", "5")));   // the next period: not part of it

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition ->
                assertThat(closedPosition.commissions()).isEqualByComparingTo("17.10"));
    }

    /** Selling 4 of 10 takes 4/10 of the $5 paid to buy them, with all of its own $5. */
    @Test
    void aPartialSellTakesItsShareOfTheBuyCommissions() {
        HoldingHistory history = HoldingHistory.of(List.of(
                trade("2024-01-01", TradeSide.BUY, "10", "100", "5"),
                trade("2024-02-01", TradeSide.SELL, "4", "150", "5")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition ->
                assertThat(closedPosition.commissions()).isEqualByComparingTo("7"));
    }

    @Test
    void aSellWhileAlreadyFlatBelongsToNoClosedPosition() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                sell("2024-06-01", 10, "120"),
                sell("2024-07-01", 3, "125")));   // entered by mistake: the position is already flat

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition ->
                assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("1200"));
    }

    /** Ids in the order the trades are created here: 1, 2, 3 (the sell while flat), 4, 5. */
    @Test
    void eachClosedPositionListsTheTradesOfItsPeriod() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                sell("2024-06-01", 10, "120"),
                sell("2024-07-01", 3, "125"),     // belongs to no period
                buy("2025-01-01", 5, "130"),
                sell("2025-02-01", 3, "140")));   // a partial sell: the period is still open

        assertThat(history.closedPositions())
                .extracting(ClosedPosition::tradeIds)
                .containsExactly(List.of(1L, 2L), List.of(4L, 5L));
    }

    // ── what the shares still held cost ─────────────────────────────────────────────────────────

    @Test
    void nothingIsHeldWithNoTrades() {
        assertThat(HoldingHistory.of(List.of()).heldCost()).isEqualByComparingTo("0");
    }

    @Test
    void sharesBoughtAreHeldAtWhatTheyCostWithTheirCommissions() {
        HoldingHistory history = HoldingHistory.of(List.of(
                trade("2026-01-01", TradeSide.BUY, "10", "100", "5"),
                trade("2026-02-01", TradeSide.BUY, "10", "200", "5")));

        assertThat(history.heldCost()).isEqualByComparingTo("3010");
    }

    /** The example in INVESTORS_TODO.md, decision 5. */
    @Test
    void aPartialSellLeavesTheRestAtTheSameAverageCost() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 24, "120"),
                sell("2026-02-01", 4, "180")));

        assertThat(history.heldCost()).isEqualByComparingTo("2400");
        assertThat(history.closedPositions().getFirst().realizedPnl()).isEqualByComparingTo("240");
    }

    @Test
    void theSharesLeftKeepTheirPartOfTheBuyCommission() {
        HoldingHistory history = HoldingHistory.of(List.of(
                trade("2026-01-01", TradeSide.BUY, "10", "100", "6"),
                trade("2026-02-01", TradeSide.SELL, "5", "150", "5")));

        assertThat(history.heldCost()).isEqualByComparingTo("503");
    }

    @Test
    void onlyTheCurrentPositionPeriodIsHeld() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2025-01-01", 10, "100"),
                sell("2025-06-01", 10, "150"),
                buy("2026-01-01", 5, "200")));

        assertThat(history.heldCost()).isEqualByComparingTo("1000");
    }

    @Test
    void aPositionSoldInFullHoldsNothing() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2025-01-01", 10, "100"),
                sell("2025-06-01", 10, "150")));

        assertThat(history.heldCost()).isEqualByComparingTo("0");
    }

    @Test
    void aBuyStillHeldWithNoPriceLeavesTheCostUnknown() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10, "100"),
                buy("2026-02-01", 10)));

        assertThat(history.heldCost()).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Each trade gets the next id, in the order a test creates them; JUnit makes a new instance for every test. */
    private long nextTradeId = 1;

    private TradeFact buy(String date, long quantity) {
        return buy(date, String.valueOf(quantity));
    }

    private TradeFact buy(String date, String quantity) {
        return trade(date, TradeSide.BUY, quantity, null);
    }

    private TradeFact buy(String date, long quantity, String price) {
        return trade(date, TradeSide.BUY, String.valueOf(quantity), price);
    }

    private TradeFact sell(String date, long quantity) {
        return sell(date, String.valueOf(quantity));
    }

    private TradeFact sell(String date, String quantity) {
        return trade(date, TradeSide.SELL, quantity, null);
    }

    private TradeFact sell(String date, long quantity, String price) {
        return trade(date, TradeSide.SELL, String.valueOf(quantity), price);
    }

    /** No commission, so the totals in the tests above stay round. */
    private TradeFact trade(String date, TradeSide side, String quantity, String price) {
        return trade(date, side, quantity, price, "0");
    }

    /** Every trade here is one investor's: which investor makes no difference to the history itself. */
    private TradeFact trade(String date, TradeSide side, String quantity, String price, String commission) {
        BigDecimal priceOrNull = price == null ? null : new BigDecimal(price);
        return new TradeFact(nextTradeId++, INVESTOR_ID, LocalDate.parse(date), side, new BigDecimal(quantity),
                priceOrNull, new BigDecimal(commission));
    }
}
