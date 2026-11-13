package portfolioboss.alerts.request;

/**
 * The body of {@code POST /api/alerts/seen}: nothing to say, so {@code {}}. A body even so, like {@code POST /api/scans}'s:
 * the endpoint accepts JSON only, so a page on another site can't send it with a plain form.
 */
public record AlertsSeenRequest() {
}
