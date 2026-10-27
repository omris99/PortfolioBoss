package portfolioboss.ai;

import jakarta.annotation.Nullable;

/**
 * One event the week's news reports about the stock (RECOMMENDATIONS_TODO.md, decision 13): its kind, how big it is,
 * and the result it comes from, so that it can be checked. Several results about the same event make one.
 * {@code @Nullable} as in {@link SourceConsensus}.
 *
 * @param magnitude only for a partnership or a major contract, where the result says who the other company is and how
 *                  much the deal is worth; {@code null} for every other kind, which weighs the same whatever its size
 *                  (an FDA approval came back once {@code HIGH}, once {@code MEDIUM})
 * @param summary   one short sentence in English
 * @param title     the result's title, word for word
 * @param url       the result's url
 */
public record NewsEvent(
        NewsEventType type,
        @Nullable EventMagnitude magnitude,
        String summary,
        String title,
        String url) {
}
