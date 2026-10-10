package portfolioboss.ai;

import jakarta.annotation.Nullable;

import java.time.LocalDate;

/**
 * One recent move by an analyst firm, as a search result reports it — for example a firm lowering its target from $360
 * to $355 and keeping its Overweight rating. The ratings are the firm's own words, not the one scale of
 * {@link AnalystRating}. {@code @Nullable} as in {@link SourceConsensus}.
 *
 * @param url                 the search result it comes from, so the UI can link to it
 * @param sourcePublishedDate the date the search engine gives that result — how old its copy of the page may be, since
 *                            the page itself may have changed since (AI_ANALYSIS_TODO.md, decision 20); {@code null}
 *                            when it gives none, and in every analysis stored before 2026-10-10
 */
public record AnalystAction(
        LocalDate date,
        String firm,
        AnalystActionType action,
        @Nullable String fromRating,
        @Nullable String toRating,
        @Nullable Double previousPriceTarget,
        @Nullable Double priceTarget,
        String url,
        @Nullable LocalDate sourcePublishedDate) {
}
