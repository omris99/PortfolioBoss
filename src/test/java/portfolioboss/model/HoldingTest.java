package portfolioboss.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class HoldingTest {

    private static final double TOLERANCE = 1e-9;

    @Test
    void costBasisIsPositionTimesAverageCost() {
        Holding holding = holdingWith(10, 150.0, 0.0);

        assertThat(holding.costBasis()).isCloseTo(1500.0, within(TOLERANCE));
    }

    @Test
    void unrealizedPnlPercentIsProfitOverCostBasis() {
        Holding holding = holdingWith(10, 100.0, 250.0);   // cost basis 1000

        assertThat(holding.unrealizedPnlPercent()).isCloseTo(25.0, within(TOLERANCE));
    }

    @Test
    void unrealizedLossGivesANegativePercent() {
        Holding holding = holdingWith(10, 100.0, -100.0);   // cost basis 1000

        assertThat(holding.unrealizedPnlPercent()).isCloseTo(-10.0, within(TOLERANCE));
    }

    @Test
    void zeroCostBasisGivesZeroPercentInsteadOfDividingByZero() {
        Holding holding = holdingWith(0, 100.0, 50.0);

        assertThat(holding.unrealizedPnlPercent()).isEqualTo(0.0);
    }

    @Test
    void shortPositionIsMeasuredAgainstTheAbsoluteCostBasis() {
        Holding shortHolding = holdingWith(-10, 100.0, 100.0);   // cost basis -1000

        assertThat(shortHolding.costBasis()).isCloseTo(-1000.0, within(TOLERANCE));
        assertThat(shortHolding.unrealizedPnlPercent()).isCloseTo(10.0, within(TOLERANCE));
    }

    private static Holding holdingWith(double position, double averageCost, double unrealizedPnl) {
        return new Holding("AAPL", 265598, "STK", "USD", position, averageCost, 0.0, 0.0, unrealizedPnl, 0.0, "U1234567");
    }
}
