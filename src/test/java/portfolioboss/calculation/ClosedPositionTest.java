package portfolioboss.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The figures derived from a closed position's totals. Pure computation — no Spring, no database. How the totals are
 * worked out from trades (average cost) is in {@code HoldingHistoryTest}.
 */
class ClosedPositionTest {

    private static final LocalDate OPEN_DATE = LocalDate.of(2024, 3, 1);
    private static final LocalDate CLOSE_DATE = LocalDate.of(2025, 6, 1);

    @Test
    void derivesTheAveragesAndTheRealizedPnlFromTheTotals() {
        ClosedPosition closedPosition = soldInFull("10", "1500", "1800");

        assertThat(closedPosition.holdingDays()).isEqualTo(457);
        assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("150");
        assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("180");
        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("300");
        assertThat(closedPosition.realizedPnlPercent()).isEqualByComparingTo("20");
        assertThat(closedPosition.soldMoreThanBought()).isFalse();
        assertThat(closedPosition.warning()).isNull();
    }

    /** 10 bought at 100 and 10 at 200, then 10 sold at 180: they cost the average, 150. */
    @Test
    void aPartialSaleIsMeasuredAgainstWhatTheSharesSoldCost() {
        ClosedPosition closedPosition = new ClosedPosition(OPEN_DATE, CLOSE_DATE, new BigDecimal("20"),
                new BigDecimal("10"), new BigDecimal("1500"), new BigDecimal("1800"), BigDecimal.ZERO,
                new BigDecimal("10"), List.of());

        assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("150");
        assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("180");
        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("300");
        assertThat(closedPosition.realizedPnlPercent()).isEqualByComparingTo("20");
        assertThat(closedPosition.soldMoreThanBought()).isFalse();
    }

    /** The trades of the first browser check: 15 bought (10 at 100, 5 at 110), 25 sold (5 at 110, 20 at 120). */
    @Test
    void sellingMoreThanWasBoughtLeavesTheRealizedPnlUnknownAndSaysWhy() {
        ClosedPosition closedPosition = new ClosedPosition(OPEN_DATE, CLOSE_DATE, new BigDecimal("15"),
                new BigDecimal("25"), new BigDecimal("1550"), new BigDecimal("2950"), BigDecimal.ZERO,
                BigDecimal.ZERO, List.of());

        // the 1,550 paid is the cost of the 15 bought, not of the 25 sold
        assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("103.3333333333333");
        assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("118");   // over the 25 sold
        assertThat(closedPosition.soldMoreThanBought()).isTrue();
        assertThat(closedPosition.realizedPnl()).isNull();
        assertThat(closedPosition.realizedPnlPercent()).isNull();
        assertThat(closedPosition.warning()).isEqualTo("Sold 25 shares but bought 15 in this period: check its trades.");
    }

    /** $5 to buy and $5 to sell: 1,800 − 1,500 − 10. The average prices stay the prices paid and received. */
    @Test
    void theCommissionsComeOffTheRealizedPnlAndItsPercent() {
        ClosedPosition closedPosition = soldInFullWithCommissions("10", "1500", "1800", "10");

        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("290");
        assertThat(closedPosition.realizedPnlPercent()).isEqualByComparingTo("19.33333333333333");
        assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("150");
        assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("180");
    }

    @Test
    void commissionsCanTurnASmallGainIntoALoss() {
        ClosedPosition closedPosition = soldInFullWithCommissions("10", "1500", "1505", "10");

        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("-5");
    }

    @Test
    void aLossIsNegative() {
        ClosedPosition closedPosition = soldInFull("10", "1500", "1200");

        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("-300");
        assertThat(closedPosition.realizedPnlPercent()).isEqualByComparingTo("-20");
    }

    @Test
    void aMissingBuyPriceLeavesTheRealizedPnlUnknownButNotTheSellSide() {
        ClosedPosition closedPosition = soldInFull("10", null, "1800");

        assertThat(closedPosition.averageBuyPrice()).isNull();
        assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("180");
        assertThat(closedPosition.realizedPnl()).isNull();
        assertThat(closedPosition.realizedPnlPercent()).isNull();
    }

    @Test
    void sharesReceivedForFreeHaveAPnlButNoPercent() {
        ClosedPosition closedPosition = soldInFull("10", "0", "1800");

        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("1800");
        assertThat(closedPosition.realizedPnlPercent()).isNull();
    }

    @Test
    void anAverageThatNeverEndsIsCutTo16SignificantDigits() {
        ClosedPosition closedPosition = soldInFull("3", "1000", "1200");

        assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("333.3333333333333");
    }

    /** Every share bought was sold, the usual case, and no commission, so the figures stay round. */
    private ClosedPosition soldInFull(String quantity, String soldCost, String sellProceeds) {
        return soldInFullWithCommissions(quantity, soldCost, sellProceeds, "0");
    }

    private ClosedPosition soldInFullWithCommissions(String quantity, String soldCost, String sellProceeds,
                                                     String commissions) {
        return new ClosedPosition(OPEN_DATE, CLOSE_DATE, new BigDecimal(quantity), new BigDecimal(quantity),
                decimalOrNull(soldCost), decimalOrNull(sellProceeds), new BigDecimal(commissions), BigDecimal.ZERO,
                List.of());
    }

    private BigDecimal decimalOrNull(String amount) {
        return amount == null ? null : new BigDecimal(amount);
    }
}
