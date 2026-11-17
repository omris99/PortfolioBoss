package portfolioboss.api.sync.request;

/**
 * The body of {@code POST /api/syncs}: nothing to say, so {@code {}}. A body even so, like {@code POST /api/scans}'s:
 * the endpoint accepts JSON only, so a page on another site can't start a sync with a plain form.
 */
public record SyncRequest() {
}
