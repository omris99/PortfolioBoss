package portfolioboss.model;

/**
 * Which of the Recommendations page's three lists a scanned stock with a long upward trend is in, by where its last
 * close stands against its 50-day average (RECOMMENDATIONS_TODO.md, decision 1). The order of the constants is the
 * order of the lists on the page. Goes into the JSON as it is.
 */
public enum HotStockLabel {
    /** 0–15% above the 50-day average: worth a look now. */
    HOT,
    /** More than 15% above it: went too far too fast — wait for a pullback. */
    TOO_HOT,
    /** Below it: the pullback came and is not over — wait for the close to climb back above, which makes it HOT. */
    CORRECTING
}
