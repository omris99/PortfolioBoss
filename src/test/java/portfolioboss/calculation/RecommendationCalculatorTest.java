package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ai.EventMagnitude;
import portfolioboss.ai.NewsEvent;
import portfolioboss.ai.NewsEventType;
import portfolioboss.ai.StockAnalysisResult;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.Stock;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The pick of RECOMMENDATIONS_TODO.md, decisions 5 and 13, on made-up stocks. Every one is HOT, closes at 100 at its
 * year high with the long trend and scores 5, so the list's order (decision 1) is how far each is above its 50-day
 * average — the less stretched first. The signs' weights: revenue falling 3, high debt 2, the other three 1; a pick
 * weighs 2 at most. Then the news score (NewsScoreCalculatorTest has its weights) less the risks orders the picks.
 */
class RecommendationCalculatorTest {

    private static final LocalDate AS_OF = LocalDate.of(2026, 10, 27);
    private static final CompanyFacts HEALTHY_COMPANY = new CompanyFacts(10.0, 20.0, 0.5, 30.0);
    private static final CompanyFacts REVENUE_FALLING = new CompanyFacts(-5.0, 20.0, 0.5, 30.0);
    private static final CompanyFacts REVENUE_FALLING_AND_HIGH_DEBT = new CompanyFacts(-5.0, 20.0, 3.0, 30.0);
    private static final CompanyFacts HIGH_DEBT = new CompanyFacts(10.0, 20.0, 3.0, 30.0);
    private static final CompanyFacts LOSING_MONEY = new CompanyFacts(10.0, -10.0, 0.5, null);
    private static final CompanyFacts LOSING_MONEY_WITH_HIGH_DEBT = new CompanyFacts(42.7, -38.3, 16.8, null);
    private static final CompanyFacts EXPENSIVE = new CompanyFacts(24.3, 1.1, 0.15, 4586.0);
    private static final NewsEvent PARTNERSHIP_WITH_A_GIANT = new NewsEvent(NewsEventType.PARTNERSHIP,
            EventMagnitude.HIGH, "An expanded partnership with a giant.", "A headline", "https://example.com/news");

    @Test
    void aPickWeighsTwoAtMost() {
        RecommendationCandidate highDebt = candidate(1, 2, HIGH_DEBT, false);                     // 2
        RecommendationCandidate lossAndRecovering = candidate(2, 3, LOSING_MONEY, true);          // 1 + 1
        RecommendationCandidate fallingRevenue = candidate(3, 4, REVENUE_FALLING, false);         // 3
        RecommendationCandidate highDebtAndRecovering = candidate(4, 5, HIGH_DEBT, true);         // 2 + 1

        List<RecommendationCandidate> picks = new RecommendationCalculator(
                List.of(highDebt, lossAndRecovering, fallingRevenue, highDebtAndRecovering), AS_OF).picks();

        assertThat(picks).containsExactly(highDebt, lossAndRecovering);
    }

    /** The lighter risk first, the list's order on a tie, and no more than three. */
    @Test
    void lighterRiskFirstThenTheListsOrderUpToThree() {
        RecommendationCandidate noSignFarAbove = candidate(1, 10, HEALTHY_COMPANY, false);        // 0
        RecommendationCandidate oneLightSignNear = candidate(2, 2, LOSING_MONEY, false);          // 1
        RecommendationCandidate noSignNear = candidate(3, 1, HEALTHY_COMPANY, false);             // 0
        RecommendationCandidate twoLightSignsNearest = candidate(4, 0.5, LOSING_MONEY, true);     // 1 + 1

        List<RecommendationCandidate> picks = new RecommendationCalculator(
                List.of(noSignFarAbove, oneLightSignNear, noSignNear, twoLightSignsNearest), AS_OF).picks();

        assertThat(picks).containsExactly(noSignNear, noSignFarAbove, oneLightSignNear);
    }

    /** No answer from Finnhub, or a symbol it doesn't know: "no signs" only means "not checked". */
    @Test
    void aStockWithoutAnyFigureFromFinnhubIsNotPicked() {
        RecommendationCandidate withoutAnswer = new RecommendationCandidate(1, hotStock(1, false), null, null);
        RecommendationCandidate unknownSymbol = candidate(2, 2, new CompanyFacts(null, null, null, null), false);
        RecommendationCandidate healthy = candidate(3, 5, HEALTHY_COMPANY, false);

        List<RecommendationCandidate> picks =
                new RecommendationCalculator(List.of(withoutAnswer, unknownSymbol, healthy), AS_OF).picks();

        assertThat(picks).containsExactly(healthy);
    }

    @Test
    void whenNoneQualifiesThereIsNoRecommendation() {
        RecommendationCandidate tooRisky = candidate(1, 2, REVENUE_FALLING_AND_HIGH_DEBT, false);
        RecommendationCandidate withoutAnswer = new RecommendationCandidate(2, hotStock(4, false), null, null);

        assertThat(new RecommendationCalculator(List.of(tooRisky, withoutAnswer), AS_OF).picks()).isEmpty();
        assertThat(new RecommendationCalculator(List.of(), AS_OF).picks()).isEmpty();
    }

    /**
     * Expensive (−1) but with a partnership with a giant (+3), it scores 2 and comes before the stocks with no
     * sign and no news (0), though they are earlier in the list.
     */
    @Test
    void goodNewsComesBeforeTheListsOrder() {
        RecommendationCandidate noSignNear = candidate(1, 1, HEALTHY_COMPANY, false);
        RecommendationCandidate noSignFarther = candidate(2, 2, HEALTHY_COMPANY, false);
        RecommendationCandidate expensiveWithAPartnership =
                candidate(3, 10, EXPENSIVE, false, analysisWith(PARTNERSHIP_WITH_A_GIANT));
        RecommendationCalculator recommendationCalculator = new RecommendationCalculator(
                List.of(noSignNear, noSignFarther, expensiveWithAPartnership), AS_OF);

        assertThat(recommendationCalculator.picks())
                .containsExactly(expensiveWithAPartnership, noSignNear, noSignFarther);
        assertThat(recommendationCalculator.scoreOf(expensiveWithAPartnership)).isEqualTo(2);
    }

    /** A loss and a debt 16.8 times the equity weigh 3, and no piece of news lets the stock past the limit of 2. */
    @Test
    void goodNewsNeverLetsAStockPastTheRiskLimit() {
        RecommendationCandidate tooRiskyWithAPartnership =
                candidate(1, 1, LOSING_MONEY_WITH_HIGH_DEBT, false, analysisWith(PARTNERSHIP_WITH_A_GIANT));
        RecommendationCandidate noSign = candidate(2, 5, HEALTHY_COMPANY, false);

        assertThat(new RecommendationCalculator(List.of(tooRiskyWithAPartnership, noSign), AS_OF).picks())
                .containsExactly(noSign);
    }

    /** A cut outlook (−3) puts a stock with no sign after one with a light sign and no news (−1). */
    @Test
    void badNewsPutsAStockAfterTheOthers() {
        NewsEvent guidanceCut = new NewsEvent(NewsEventType.GUIDANCE_CUT, null, "The company cut its outlook.",
                "A headline", "https://example.com/cut");
        RecommendationCandidate noSignWithACut = candidate(1, 1, HEALTHY_COMPANY, false, analysisWith(guidanceCut));
        RecommendationCandidate expensive = candidate(2, 5, EXPENSIVE, false);

        assertThat(new RecommendationCalculator(List.of(noSignWithACut, expensive), AS_OF).picks())
                .containsExactly(expensive, noSignWithACut);
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private RecommendationCandidate candidate(long candidateId, double percentAboveSma50, CompanyFacts companyFacts,
                                              boolean recovering) {
        return candidate(candidateId, percentAboveSma50, companyFacts, recovering, null);
    }

    /** {@code analysis} {@code null}: no news events, a news score of 0. */
    private RecommendationCandidate candidate(long candidateId, double percentAboveSma50, CompanyFacts companyFacts,
                                              boolean recovering, StockAnalysisResult analysis) {
        return new RecommendationCandidate(candidateId, hotStock(percentAboveSma50, recovering),
                new Stock("STOCK" + candidateId, null, companyFacts), analysis);
    }

    private StockAnalysisResult analysisWith(NewsEvent newsEvent) {
        return new StockAnalysisResult(List.of(), List.of(), List.of(), null, null, null, List.of(newsEvent));
    }

    /** Recovering is down 20% in a year, otherwise up 20%. */
    private HotStockCalculator hotStock(double percentAboveSma50, boolean recovering) {
        double sma50 = 100 / (1 + percentAboveSma50 / 100);
        Double yearReturnPercent = recovering ? -20.0 : 20.0;
        Momentum momentum = new Momentum(AS_OF, 100, 99.0, sma50, 80.0, 100.0, 5.0, 3.0,
                AS_OF.minusYears(1).plusDays(1), 100, yearReturnPercent, 10);
        return new HotStockCalculator(momentum);
    }
}
