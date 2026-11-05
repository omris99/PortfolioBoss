package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ai.EventMagnitude;
import portfolioboss.ai.NewsEvent;
import portfolioboss.ai.NewsEventType;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;
import portfolioboss.stocks.Stock;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A scanned stock as the recommendation sees it: the news score from its analysis, and the risk signs and the timing
 * note from {@link RiskSignsCalculator} (RiskSignsCalculatorTest has every rule). Every stock is HOT — it closes at 100,
 * 5% above its 50-day average, with the long trend.
 */
class RecommendationCandidateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    /** Growing, profitable, little debt, a P/E of 30: no sign. */
    private static final CompanyFacts HEALTHY_COMPANY = new CompanyFacts(10.0, 20.0, 0.5, 30.0);
    private static final Double UP_IN_A_YEAR = 20.0;
    /** The analysts' target is AnalystTargetCalculatorTest's: these tests are about the rest. */
    private static final AnalystTargetCalculator NO_TARGETS = new AnalystTargetCalculator(List.of(), 100.0, TODAY);

    /** Finnhub's figures and the closes both reach the signs: a loss, and lower than a year ago. */
    @Test
    void theRiskSignsAndTheTimingNoteComeFromFinnhubAndTheCloses() {
        Stock lossMakingCompany = new Stock("ABCD", new EarningsDate(TODAY.plusDays(3), "bmo"),
                new CompanyFacts(10.0, -5.0, 0.5, 30.0));
        RecommendationCandidate candidate =
                new RecommendationCandidate(1, hotStock(-10.0), NO_TARGETS, lossMakingCompany, null);

        assertThat(candidate.riskSigns()).containsExactly(RiskSign.LOSES_MONEY, RiskSign.RECOVERING);
        assertThat(candidate.hasCompanyFacts()).isTrue();
        assertThat(candidate.reportsWithin14Days(TODAY)).isTrue();
    }

    /** The analysis's events and analysts' moves weighed (NewsScoreCalculatorTest has the weights); 0 never analyzed. */
    @Test
    void theNewsScoreComesFromTheAnalysis() {
        NewsEvent partnership = new NewsEvent(NewsEventType.PARTNERSHIP, EventMagnitude.HIGH,
                "An expanded partnership with a giant.", "A headline", "https://example.com/news");
        StockAnalysisResult analysis = new StockAnalysisResult(List.of(), List.of(), List.of(), null, null, null,
                List.of(partnership));
        Stock stock = new Stock("ABCD", null, HEALTHY_COMPANY);

        assertThat(new RecommendationCandidate(1, hotStock(UP_IN_A_YEAR), NO_TARGETS, stock, analysis)
                .newsScore(TODAY))
                .isEqualTo(3);
        assertThat(new RecommendationCandidate(1, hotStock(UP_IN_A_YEAR), NO_TARGETS, stock, null).newsScore(TODAY))
                .isZero();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** A last close of 100, 5% above its 50-day average, with the long trend and at its year high. */
    private HotStockCalculator hotStock(Double yearReturnPercent) {
        Momentum momentum = new Momentum(TODAY, 100, 99.0, 100 / 1.05, 80.0, 100.0, 5.0, 3.0,
                TODAY.minusYears(1).plusDays(1), 100, yearReturnPercent, 10);
        return new HotStockCalculator(momentum);
    }
}
