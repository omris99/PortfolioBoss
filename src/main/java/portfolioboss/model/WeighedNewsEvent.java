package portfolioboss.model;

import portfolioboss.ai.NewsEvent;

/**
 * One event Claude found in the news and what it weighs in the stock's news score (RECOMMENDATIONS_TODO.md, decision 13)
 * — 0 for one no kind fits. Goes into the JSON as it is, so the page shows why the score is what it is without knowing
 * the weights, which stay in {@code calculation.NewsScoreCalculator}.
 */
public record WeighedNewsEvent(NewsEvent event, int points) {
}
