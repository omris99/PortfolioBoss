package portfolioboss.domain;

import org.junit.jupiter.api.Test;
import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/** Pure computation — no Spring, no database. Trades are entered here in whatever order the scenario reads best; {@link HoldingHistory#of} sorts them itself. */
class HoldingHistoryTest {

    private static final LocalDate SNAPSHOT_DATE = LocalDate.of(2026, 9, 22);

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
    void aPositionStillHeldHasNoClosedPosition() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10, "150"),
                sell("2026-02-01", 4, "160")));

        assertThat(history.closedPositions()).isEmpty();
    }

    @Test
    void aFullSellBecomesAClosedPositionWithItsTotals() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-03-01", 10, "150"),
                sell("2025-06-01", 10, "180")));

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition -> {
            assertThat(closedPosition.openDate()).isEqualTo(LocalDate.of(2024, 3, 1));
            assertThat(closedPosition.closeDate()).isEqualTo(LocalDate.of(2025, 6, 1));
            assertThat(closedPosition.quantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.buyCost()).isEqualByComparingTo("1500");
            assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("1800");
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
            assertThat(closedPosition.quantity()).isEqualByComparingTo("20");
            assertThat(closedPosition.soldQuantity()).isEqualByComparingTo("20");    // 5 + 15
            assertThat(closedPosition.buyCost()).isEqualByComparingTo("2200");        // 1,000 + 1,200
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
            assertThat(closedPosition.buyCost()).isNull();
            assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("2400");
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
            assertThat(closedPosition.quantity()).isEqualByComparingTo("10");
            assertThat(closedPosition.soldQuantity()).isEqualByComparingTo("12");
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

    @Test
    void aSellWhileAlreadyFlatBelongsToNoClosedPosition() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2024-01-01", 10, "100"),
                sell("2024-06-01", 10, "120"),
                sell("2024-07-01", 3, "125")));   // entered by mistake: the position is already flat

        assertThat(history.closedPositions()).singleElement().satisfies(closedPosition ->
                assertThat(closedPosition.sellProceeds()).isEqualByComparingTo("1200"));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

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

    private TradeFact trade(String date, TradeSide side, String quantity, String price, String commission) {
        BigDecimal priceOrNull = price == null ? null : new BigDecimal(price);
        return new TradeFact(LocalDate.parse(date), side, new BigDecimal(quantity), priceOrNull,
                new BigDecimal(commission));
    }
}
