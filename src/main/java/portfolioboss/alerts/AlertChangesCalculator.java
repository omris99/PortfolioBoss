package portfolioboss.alerts;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The second step of a check: the conditions true now against the alerts still open. An open alert stands for "this was
 * true at the previous check, and has been since", so no other state of the previous check is needed — and at the first
 * check, with nothing open, everything true becomes an alert once.
 *
 * @param openAlertKeys     the keys of the alerts not ended yet, of the types turned on
 * @param conditionsTrueNow what the rules found true at this check
 */
record AlertChangesCalculator(Set<AlertKey> openAlertKeys, List<AlertCondition> conditionsTrueNow) {

    /**
     * The conditions true now with no open alert: each becomes a new alert. A key found twice in one check (one firm's
     * two headlines at the same minute) is one alert.
     */
    protected List<AlertCondition> conditionsToAlert() {
        Map<AlertKey, AlertCondition> conditionByKey = new LinkedHashMap<>();
        conditionsTrueNow.forEach(condition -> conditionByKey.putIfAbsent(condition.key(), condition));
        return conditionByKey.values().stream()
                .filter(condition -> !openAlertKeys.contains(condition.key()))
                .toList();
    }

    /** The open alerts whose condition is no longer true: each is ended, and if it comes back it is a new alert. */
    protected Set<AlertKey> keysOfAlertsNoLongerTrue() {
        Set<AlertKey> keysTrueNow = conditionsTrueNow.stream().map(AlertCondition::key).collect(Collectors.toSet());
        return openAlertKeys.stream()
                .filter(openAlertKey -> !keysTrueNow.contains(openAlertKey))
                .collect(Collectors.toSet());
    }
}
