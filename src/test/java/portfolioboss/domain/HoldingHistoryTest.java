package portfolioboss.domain;

import org.junit.jupiter.api.Test;
import portfolioboss.db.HoldingStatus;
import portfolioboss.db.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    void aFullSellClosesTheEpisodeAndHoldingDaysRunsToTheSellDate() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10),
                sell("2026-03-01", 10)));

        assertThat(history.netQuantity()).isEqualByComparingTo("0");
        assertThat(history.holdingDays(HoldingStatus.CLOSED, SNAPSHOT_DATE)).isEqualTo(59);
    }

    @Test
    void sellingInFullAndBuyingAgainResetsTheEpisode() {
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
    void partialQuantitiesThatSumToZeroCloseTheEpisode() {
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", "10.5"),
                sell("2026-02-01", "10.5")));

        assertThat(history.netQuantity()).isEqualByComparingTo("0");
        assertThat(history.holdingDays(HoldingStatus.CLOSED, SNAPSHOT_DATE)).isEqualTo(31);
    }

    @Test
    void aSellWithNoPriorBuyIsIgnored() {
        HoldingHistory history = HoldingHistory.of(List.of(sell("2026-01-01", 10)));

        assertThat(history.firstBuyDate()).isNull();
        assertThat(history.lastSellDate()).isNull();
        assertThat(history.netQuantity()).isEqualByComparingTo("0");
    }

    @Test
    void aSecondSellAfterTheEpisodeIsAlreadyFlatIsIgnored() {
        // a duplicate/mistaken sell entry after the position was already fully sold
        HoldingHistory history = HoldingHistory.of(List.of(
                buy("2026-01-01", 10),
                sell("2026-02-01", 10),
                sell("2026-02-15", 3)));

        assertThat(history.lastSellDate()).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(history.netQuantity()).isEqualByComparingTo("0");
    }

    private TradeFact buy(String date, long quantity) {
        return buy(date, String.valueOf(quantity));
    }

    private TradeFact buy(String date, String quantity) {
        return new TradeFact(LocalDate.parse(date), TradeSide.BUY, new BigDecimal(quantity));
    }

    private TradeFact sell(String date, long quantity) {
        return sell(date, String.valueOf(quantity));
    }

    private TradeFact sell(String date, String quantity) {
        return new TradeFact(LocalDate.parse(date), TradeSide.SELL, new BigDecimal(quantity));
    }
}
