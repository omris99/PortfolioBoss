package portfolioboss.ai;

import jakarta.annotation.Nullable;

import java.time.LocalDate;

/**
 * One recent move by an analyst firm, as a search result reports it — for example a firm lowering its target from $360
 * to $355 and keeping its Overweight rating. The ratings are the firm's own words, not the one scale of
 * {@link AnalystRating}. {@code @Nullable} as in {@link SourceConsensus}.
 *
 * @param url the search result it comes from, so the UI can link to it
 */
public record AnalystAction(
        LocalDate date,
        String firm,
        AnalystActionType action,
        @Nullable String fromRating,
        @Nullable String toRating,
        @Nullable Double previousPriceTarget,
        @Nullable Double priceTarget,
        String url) {
}
