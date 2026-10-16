package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ai.AnalystAction;
import portfolioboss.ai.AnalystActionType;
import portfolioboss.model.AnalystTrend;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The analysts' trend of AI_ANALYSIS_TODO.md, decision 18, on the actions Claude extracted in the run of 2026-10-09:
 * only price targets count — raised against lowered, when an action states the target before and after.
 */
class AnalystTrendCalculatorTest {

    /** NVDA: BNP Paribas and J.P. Morgan raised their targets; Morgan Stanley kept its view without one. */
    @Test
    void moreTargetsRaisedIsImproving() {
        AnalystTrendCalculator nvidia = new AnalystTrendCalculator(List.of(
                actionOf(AnalystActionType.TARGET_RAISED, 285.0, 345.0),
                actionOf(AnalystActionType.REITERATE, null, null),
                actionOf(AnalystActionType.TARGET_RAISED, 280.0, 320.0)));

        assertThat(nvidia.trend()).isEqualTo(AnalystTrend.IMPROVING);
        assertThat(nvidia.raisedTargetCount()).isEqualTo(2);
        assertThat(nvidia.loweredTargetCount()).isZero();
    }

    /**
     * INTC: Mizuho lowered its target; Northland's upgrade states a $120 target but no earlier one, so it says nothing
     * about which way the target went, and the firms that kept or started coverage moved nothing.
     */
    @Test
    void moreTargetsLoweredIsDeteriorating() {
        AnalystTrendCalculator intel = new AnalystTrendCalculator(List.of(
                actionOf(AnalystActionType.REITERATE, null, 145.0),
                actionOf(AnalystActionType.REITERATE, null, 110.0),
                actionOf(AnalystActionType.INITIATE, null, 110.0),
                actionOf(AnalystActionType.UPGRADE, null, 120.0),
                actionOf(AnalystActionType.TARGET_LOWERED, 109.0, 92.0)));

        assertThat(intel.trend()).isEqualTo(AnalystTrend.DETERIORATING);
        assertThat(intel.raisedTargetCount()).isZero();
        assertThat(intel.loweredTargetCount()).isEqualTo(1);
    }

    /**
     * TTWO: Raymond James and JPMorgan state targets, and nobody moved one; the downgrade is Zacks Research's — a rating
     * without a target, which counts for nothing.
     */
    @Test
    void targetsThatDidNotMoveAreStable() {
        AnalystTrendCalculator takeTwo = new AnalystTrendCalculator(List.of(
                actionOf(AnalystActionType.REITERATE, null, 300.0),
                actionOf(AnalystActionType.REITERATE, null, null),
                actionOf(AnalystActionType.DOWNGRADE, null, null),
                actionOf(AnalystActionType.INITIATE, null, 310.0)));

        assertThat(takeTwo.trend()).isEqualTo(AnalystTrend.STABLE);
        assertThat(takeTwo.raisedTargetCount()).isZero();
        assertThat(takeTwo.loweredTargetCount()).isZero();
    }

    @Test
    void asManyRaisedAsLoweredIsStable() {
        AnalystTrendCalculator calculator = new AnalystTrendCalculator(List.of(
                actionOf(AnalystActionType.TARGET_RAISED, 100.0, 110.0),
                actionOf(AnalystActionType.TARGET_LOWERED, 120.0, 115.0)));

        assertThat(calculator.trend()).isEqualTo(AnalystTrend.STABLE);
    }

    /** A target restated at the same figure moved nowhere. */
    @Test
    void aTargetKeptAtTheSameFigureCountsAsNeither() {
        AnalystTrendCalculator calculator = new AnalystTrendCalculator(List.of(
                actionOf(AnalystActionType.REITERATE, 300.0, 300.0)));

        assertThat(calculator.trend()).isEqualTo(AnalystTrend.STABLE);
        assertThat(calculator.raisedTargetCount()).isZero();
        assertThat(calculator.loweredTargetCount()).isZero();
    }

    /** Weiss Ratings' "Buy (B) ➝ Buy (B-)" and a Zacks downgrade: grades without a target say nothing. */
    @Test
    void ratingsWithoutAnyTargetHaveNoTrend() {
        AnalystTrendCalculator calculator = new AnalystTrendCalculator(List.of(
                actionOf(AnalystActionType.DOWNGRADE, null, null),
                actionOf(AnalystActionType.DOWNGRADE, null, null)));

        assertThat(calculator.trend()).isNull();
    }

    /** SPY, an ETF, and AAPL, whose results showed no action. */
    @Test
    void noActionHasNoTrend() {
        AnalystTrendCalculator calculator = new AnalystTrendCalculator(List.of());

        assertThat(calculator.trend()).isNull();
        assertThat(calculator.raisedTargetCount()).isZero();
        assertThat(calculator.loweredTargetCount()).isZero();
    }

    private AnalystAction actionOf(AnalystActionType actionType, Double previousPriceTarget, Double priceTarget) {
        return new AnalystAction(LocalDate.of(2026, 10, 5), "Some Firm", actionType, null, "Buy", previousPriceTarget,
                priceTarget, "https://example.com/action", null);
    }
}
