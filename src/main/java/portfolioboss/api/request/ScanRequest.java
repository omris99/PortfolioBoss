package portfolioboss.api.request;

/**
 * The body of {@code POST /api/scans}: nothing to say, so {@code {}}. A body even so, like {@code POST /api/analysis}'s:
 * the endpoint accepts JSON only, so a page on another site can't start a scan with a plain form.
 */
public record ScanRequest() {
}
