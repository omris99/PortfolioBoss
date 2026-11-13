package portfolioboss.alerts;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** The second step of a check: what is true now against the alerts still open. */
class AlertChangesCalculatorTest {

    private static final AlertKey ABCD_BELOW_SMA50 = new AlertKey(AlertType.CLOSED_BELOW_SMA50, 8101, null);
    private static final AlertKey ABCD_CUT_BY_MORGAN_STANLEY =
            new AlertKey(AlertType.ANALYST_CUT_TARGET, 8101, "Morgan Stanley 2026-11-09T14:05");
    private static final AlertKey ABCD_CUT_BY_MIZUHO =
            new AlertKey(AlertType.ANALYST_CUT_TARGET, 8101, "Mizuho 2026-11-10T09:00");

    @Test
    void aConditionWithNoOpenAlertIsANewAlert() {
        AlertChangesCalculator changes = new AlertChangesCalculator(Set.of(), List.of(conditionOf(ABCD_BELOW_SMA50)));

        assertThat(changes.conditionsToAlert()).extracting(AlertCondition::key).containsExactly(ABCD_BELOW_SMA50);
        assertThat(changes.keysOfAlertsNoLongerTrue()).isEmpty();
    }

    /** True at two checks in a row: the first made the alert, the second makes none. */
    @Test
    void aConditionStillTrueMakesNoSecondAlert() {
        AlertChangesCalculator changes =
                new AlertChangesCalculator(Set.of(ABCD_BELOW_SMA50), List.of(conditionOf(ABCD_BELOW_SMA50)));

        assertThat(changes.conditionsToAlert()).isEmpty();
        assertThat(changes.keysOfAlertsNoLongerTrue()).isEmpty();
    }

    /** No longer true: the open alert ends — and when it is true again, with nothing open, it is a new alert. */
    @Test
    void aConditionNoLongerTrueEndsAndComingBackIsANewAlert() {
        AlertChangesCalculator noLongerTrue = new AlertChangesCalculator(Set.of(ABCD_BELOW_SMA50), List.of());
        AlertChangesCalculator trueAgain = new AlertChangesCalculator(Set.of(), List.of(conditionOf(ABCD_BELOW_SMA50)));

        assertThat(noLongerTrue.keysOfAlertsNoLongerTrue()).containsExactly(ABCD_BELOW_SMA50);
        assertThat(trueAgain.conditionsToAlert()).hasSize(1);
    }

    @Test
    void twoFirmsCutsAreTwoAlertsAndOnlyTheNewOneIsMade() {
        AlertChangesCalculator changes = new AlertChangesCalculator(Set.of(ABCD_CUT_BY_MORGAN_STANLEY),
                List.of(conditionOf(ABCD_CUT_BY_MORGAN_STANLEY), conditionOf(ABCD_CUT_BY_MIZUHO)));

        assertThat(changes.conditionsToAlert()).extracting(AlertCondition::key).containsExactly(ABCD_CUT_BY_MIZUHO);
    }

    /** The table allows one open alert per key: a key twice in one check is one alert. */
    @Test
    void theSameKeyTwiceInOneCheckIsOneAlert() {
        AlertChangesCalculator changes = new AlertChangesCalculator(Set.of(),
                List.of(conditionOf(ABCD_CUT_BY_MIZUHO), conditionOf(ABCD_CUT_BY_MIZUHO)));

        assertThat(changes.conditionsToAlert()).hasSize(1);
    }

    private AlertCondition conditionOf(AlertKey key) {
        return new AlertCondition(key, "ABCD", "a message");
    }
}
