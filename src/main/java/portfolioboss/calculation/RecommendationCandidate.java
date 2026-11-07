package portfolioboss.calculation;

import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.Stock;

import java.time.LocalDate;
import java.util.List;

/**
 * A scanned stock, as the recommendation sees it (RECOMMENDATIONS_TODO.md, decisions 5, 13 and 14): its analysts'
 * target, its risk signs, whether Finnhub gave any of its figures at all, its news score and the timing note. The risk
 * signs and the timing note are {@link RiskSignsCalculator}'s, which a holding uses too.
 *
 * @param candidateId   the stock's row in its scan ({@code scan_candidate}), so that a pick can be told apart
 * @param hotStock      what the rule of decision 1 says about its closes: its place in its list, whether it is
 *                      recovering
 * @param analystTarget the analysts' target from its Dow Jones headlines (decision 14), against its last close
 * @param stock         what Finnhub answered; {@code null} without an answer — no key, or the call failed
 * @param analysis      what Claude found in the news and about the analysts; {@code null} while never analyzed
 */
public record RecommendationCandidate(long candidateId, HotStockCalculator hotStock,
                                      AnalystTargetCalculator analystTarget, Stock stock,
                                      StockAnalysisResult analysis) {

    /** The signs the stock has, in the order of {@link RiskSign}; empty when it has none. */
    public List<RiskSign> riskSigns() {
        return riskSignsCalculator().riskSigns();
    }

    /** At least one of Finnhub's four figures: without any, the stock is not picked ({@link RiskSignsCalculator}). */
    public boolean hasCompanyFacts() {
        return riskSignsCalculator().hasCompanyFacts();
    }

    /**
     * The news events and the analysts' moves weighed ({@link NewsScoreCalculator}); 0 while never analyzed.
     *
     * @param today the day of the analysis: the analysts' moves of the 30 days before it count
     */
    public int newsScore(LocalDate today) {
        if (analysis == null) {
            return 0;
        }
        return new NewsScoreCalculator(analysis.newsEvents(), analysis.recentActions(), today).score();
    }

    /** The timing note: the next report is 0 to 14 days after {@code today} ({@link RiskSignsCalculator}). */
    public boolean reportsWithin14Days(LocalDate today) {
        return riskSignsCalculator().reportsWithin14Days(today);
    }

    /** The stock's risk signs and timing note, from Finnhub's answer and the closes: the API shows them from it too. */
    public RiskSignsCalculator riskSignsCalculator() {
        return new RiskSignsCalculator(stock, hotStock.momentum());
    }
}
