package portfolioboss.ai;

/**
 * Why the stock's price has been moving, as the week's news explains it (RECOMMENDATIONS_TODO.md, decision 4): one
 * sentence, and the news result it rests on, so that it can be checked. From the news search only, never from Claude's
 * own knowledge of the company.
 *
 * @param reason one short sentence in English
 * @param title  the title of the result it rests on, word for word
 * @param url    that result's url
 */
public record MoveReason(
        String reason,
        String title,
        String url) {
}
