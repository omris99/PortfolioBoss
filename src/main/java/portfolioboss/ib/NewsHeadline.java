package portfolioboss.ib;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * One headline of a news feed, as IB's {@code historicalNews} sends it — here only Dow Jones's ({@code DJ-N}), whose
 * headlines carry every analyst's price target (RECOMMENDATIONS_TODO.md, decision 14).
 *
 * @param publishedAt when it came out, as IB writes it — in IB's own time zone
 * @param articleId   IB's id of the article: a headline that comes on two pages is kept once
 * @param headline    the text, with IB's metadata in braces before it: "{A:800015:L:en}Snowflake Price Target …"
 */
public record NewsHeadline(LocalDateTime publishedAt, String articleId, String headline) {

    /** How IB writes a headline's time, and how it wants one back: "2026-10-12 10:25:00.0". */
    protected static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.S");
}
