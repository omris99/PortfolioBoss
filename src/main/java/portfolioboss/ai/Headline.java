package portfolioboss.ai;

import jakarta.annotation.Nullable;

import java.time.LocalDate;

/**
 * One news headline about the stock, word for word as published (in English). {@code @Nullable} as in
 * {@link SourceConsensus}.
 *
 * @param source who published it ("Reuters")
 * @param url    the search result it comes from, so the UI can link to it
 */
public record Headline(
        @Nullable LocalDate date,
        String title,
        String source,
        String url) {
}
