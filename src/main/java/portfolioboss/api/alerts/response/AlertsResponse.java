package portfolioboss.api.alerts.response;

import java.util.List;

/**
 * What {@code GET /api/alerts} answers: the bell's list and the settings drawer. The component names are JSON keys.
 *
 * @param alerts   the alerts of the types turned on, open or ended, the newest first
 * @param settings every type, in the drawer's order
 */
public record AlertsResponse(List<AlertResponse> alerts, List<AlertSettingResponse> settings) {
}
