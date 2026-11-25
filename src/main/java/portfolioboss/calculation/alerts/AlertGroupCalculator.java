package portfolioboss.calculation.alerts;

import portfolioboss.model.alerts.AlertGroup;
import portfolioboss.model.alerts.AlertType;

/**
 * Which group of the bell's panel one alert is shown in (UI_POLISH_TODO.md, stage ד). The user's rule: a stock he holds
 * is first of all his holding — its alert that it entered the short list is in "Your holdings" too. So only a short-list
 * alert of a stock not held now is in "The short list". Every other kind is a holding's own rule, its group "Your
 * holdings" even once the holding is closed (its warning triangle).
 *
 * @param alertType      the alert's kind
 * @param stockIsHeldNow whether the stock is an open holding of the latest sync — a closed one is held no more
 */
public record AlertGroupCalculator(AlertType alertType, boolean stockIsHeldNow) {

    public AlertGroup group() {
        boolean isAboutAStockNotHeld = alertType == AlertType.ENTERED_SHORT_LIST && !stockIsHeldNow;
        return isAboutAStockNotHeld ? AlertGroup.SHORT_LIST : AlertGroup.YOUR_HOLDINGS;
    }
}
