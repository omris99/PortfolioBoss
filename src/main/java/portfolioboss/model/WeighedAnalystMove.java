package portfolioboss.model;

import portfolioboss.ai.AnalystAction;

/**
 * An analyst's upgrade or downgrade that counts in the stock's news score (RECOMMENDATIONS_TODO.md, decision 13) — with
 * a price target, from the last 30 days — and what it weighs: +2 or −2. Goes into the JSON as it is, like
 * {@link WeighedNewsEvent}.
 */
public record WeighedAnalystMove(AnalystAction action, int points) {
}
