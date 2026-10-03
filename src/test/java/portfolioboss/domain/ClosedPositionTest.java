package portfolioboss.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The figures derived from a closed position's totals. Pure computation — no Spring, no database. */
class ClosedPositionTest {

    private static final LocalDate OPEN_DATE = LocalDate.of(2024, 3, 1);
    private static final LocalDate CLOSE_DATE = LocalDate.of(2025, 6, 1);

    @Test
    void derivesTheAveragesAndTheRealizedPnlFromTheTotals() {
        ClosedPosition closedPosition = closedPosition("10", "1500", "1800");

        assertThat(closedPosition.holdingDays()).isEqualTo(457);
        assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("150");
        assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("180");
        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("300");
        assertThat(closedPosition.realizedPnlPercent()).isEqualByComparingTo("20");
    }

    @Test
    void aLossIsNegative() {
        ClosedPosition closedPosition = closedPosition("10", "1500", "1200");

        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("-300");
        assertThat(closedPosition.realizedPnlPercent()).isEqualByComparingTo("-20");
    }

    @Test
    void aMissingBuyPriceLeavesTheRealizedPnlUnknownButNotTheSellSide() {
        ClosedPosition closedPosition = closedPosition("10", null, "1800");

        assertThat(closedPosition.averageBuyPrice()).isNull();
        assertThat(closedPosition.averageSellPrice()).isEqualByComparingTo("180");
        assertThat(closedPosition.realizedPnl()).isNull();
        assertThat(closedPosition.realizedPnlPercent()).isNull();
    }

    @Test
    void sharesReceivedForFreeHaveAPnlButNoPercent() {
        ClosedPosition closedPosition = closedPosition("10", "0", "1800");

        assertThat(closedPosition.realizedPnl()).isEqualByComparingTo("1800");
        assertThat(closedPosition.realizedPnlPercent()).isNull();
    }

    @Test
    void anAverageThatNeverEndsIsCutTo16SignificantDigits() {
        ClosedPosition closedPosition = closedPosition("3", "1000", "1200");

        assertThat(closedPosition.averageBuyPrice()).isEqualByComparingTo("333.3333333333333");
    }

    private ClosedPosition closedPosition(String quantity, String buyCost, String sellProceeds) {
        return new ClosedPosition(OPEN_DATE, CLOSE_DATE, new BigDecimal(quantity), decimalOrNull(buyCost),
                decimalOrNull(sellProceeds));
    }

    private BigDecimal decimalOrNull(String amount) {
        return amount == null ? null : new BigDecimal(amount);
    }
}
