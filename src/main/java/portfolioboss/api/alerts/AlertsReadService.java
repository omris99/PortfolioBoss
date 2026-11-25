package portfolioboss.api.alerts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.api.alerts.response.AlertResponse;
import portfolioboss.api.alerts.response.AlertSettingResponse;
import portfolioboss.api.alerts.response.AlertsResponse;
import portfolioboss.calculation.alerts.AlertGroupCalculator;
import portfolioboss.calculation.alerts.AlertSettings;
import portfolioboss.db.alerts.AlertRepository;
import portfolioboss.db.alerts.AlertSettingRepository;
import portfolioboss.db.portfolio.AccountStateRepository;
import portfolioboss.db.portfolio.HoldingEntity;
import portfolioboss.db.portfolio.HoldingRepository;
import portfolioboss.model.alerts.AlertType;
import portfolioboss.model.portfolio.HoldingStatus;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * What the bell shows ({@code GET /api/alerts}): the alerts the checks stored ({@code alerts.AlertService}) and every
 * type's setting, as the settings drawer reads them. It only reads.
 */
@Service
public class AlertsReadService {

    private final AlertRepository alertRepository;
    private final AlertSettingRepository alertSettingRepository;
    private final AccountStateRepository accountStateRepository;
    private final HoldingRepository holdingRepository;

    public AlertsReadService(AlertRepository alertRepository, AlertSettingRepository alertSettingRepository,
                             AccountStateRepository accountStateRepository, HoldingRepository holdingRepository) {
        this.alertRepository = alertRepository;
        this.alertSettingRepository = alertSettingRepository;
        this.accountStateRepository = accountStateRepository;
        this.holdingRepository = holdingRepository;
    }

    /**
     * The alerts of the types turned on, the newest first, each with the holding whose drawer it opens and its group in
     * the bell's panel, and every type's setting.
     */
    @Transactional(readOnly = true)
    public AlertsResponse currentAlerts() {
        AlertSettings settings = alertSettingRepository.alertSettings();
        List<AlertType> enabledTypes = settings.enabledTypes();
        List<HoldingEntity> holdings = holdingsOfTheLatestSync();
        Map<Integer, Long> holdingIdByConId = holdingIdByConId(holdings);
        Set<Integer> conIdsHeldNow = conIdsHeldNow(holdings);
        List<AlertResponse> alerts = enabledTypes.isEmpty() ? List.of()
                : alertRepository.findByAlertTypeInOrderByCreatedAtDescIdDesc(enabledTypes).stream()
                        .map(alert -> new AlertResponse(alert, holdingIdByConId.get(alert.conId()),
                                new AlertGroupCalculator(alert.alertType(), conIdsHeldNow.contains(alert.conId()))
                                        .group()))
                        .toList();
        return new AlertsResponse(alerts, settings.allSettings().stream().map(AlertSettingResponse::new).toList());
    }

    /** Every holding of the latest sync's account, open and closed; none before the first sync. */
    private List<HoldingEntity> holdingsOfTheLatestSync() {
        return accountStateRepository.findFirstByOrderByAsOfDesc()
                .map(latestSync -> holdingRepository.findByAccountOrderById(latestSync.account()))
                .orElse(List.of());
    }

    /** By contract id: an alert opens its holding's drawer — a closed holding's too. */
    private Map<Integer, Long> holdingIdByConId(List<HoldingEntity> holdings) {
        return holdings.stream().collect(Collectors.toMap(HoldingEntity::conId, HoldingEntity::id));
    }

    /** The contracts of the open holdings: the stocks held now, whose alerts are all in "Your holdings". */
    private Set<Integer> conIdsHeldNow(List<HoldingEntity> holdings) {
        return holdings.stream()
                .filter(holding -> holding.status() == HoldingStatus.OPEN)
                .map(HoldingEntity::conId)
                .collect(Collectors.toSet());
    }
}
