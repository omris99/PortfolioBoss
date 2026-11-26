package portfolioboss.model.recommendations;

/**
 * The Recommendations page's three lists of long-term investments (RECOMMENDATIONS_FINVIZ_TODO.md, decision 1), each the
 * stocks one of Finviz's screens passes. Momentum is shown beside each stock and counts in the pick, but takes no stock
 * off a list (decision 2). Stored by its name in {@code listed_stock.recommendation_list}.
 */
public enum RecommendationList {

    /** Sales growing fast at a high gross margin: the "dream" list. */
    FAST_GROWTH,

    /** Profitable, still growing, not expensive. */
    SLOW_GROWTH,

    /** Cheap against its earnings and cash, still growing. */
    VALUE
}
