package portfolioboss.calculation.alerts;

import org.junit.jupiter.api.Test;
import portfolioboss.model.alerts.AlertGroup;
import portfolioboss.model.alerts.AlertType;

import static org.assertj.core.api.Assertions.assertThat;

/** The bell's two groups: a stock held now is first of all a holding (the user's rule). */
class AlertGroupCalculatorTest {

    @Test
    void aStockNotHeldThatEnteredTheShortListIsInTheShortList() {
        assertThat(new AlertGroupCalculator(AlertType.ENTERED_SHORT_LIST, false).group()).isEqualTo(AlertGroup.SHORT_LIST);
    }

    @Test
    void aStockHeldThatEnteredTheShortListIsInYourHoldings() {
        assertThat(new AlertGroupCalculator(AlertType.ENTERED_SHORT_LIST, true).group())
                .isEqualTo(AlertGroup.YOUR_HOLDINGS);
    }

    /** A closed holding's warning triangle: a holding's own rule, though the stock is no longer held. */
    @Test
    void everyOtherKindIsInYourHoldingsHeldOrNot() {
        for (AlertType alertType : AlertType.values()) {
            if (alertType == AlertType.ENTERED_SHORT_LIST) {
                continue;
            }
            assertThat(new AlertGroupCalculator(alertType, false).group()).as(alertType.name())
                    .isEqualTo(AlertGroup.YOUR_HOLDINGS);
            assertThat(new AlertGroupCalculator(alertType, true).group()).as(alertType.name())
                    .isEqualTo(AlertGroup.YOUR_HOLDINGS);
        }
    }
}
