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
 * The risk signs and the timing note of RECOMMENDATIONS_TODO.md, decision 5, on figures made up directly. Every stock
 * is HOT — it closes at 100, 5% above its 50-day average, with the long trend — and up 20% in a year unless a test says
 * otherwise.
 */
class RecommendationCandidateTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    /** Growing, profitable, little debt, a P/E of 30: no sign. */
    private static final CompanyFacts HEALTHY_COMPANY = new CompanyFacts(10.0, 20.0, 0.5, 30.0);
    private static final Double UP_IN_A_YEAR = 20.0;
    /** The analysts' target is AnalystTargetCalculatorTest's: these tests are about the signs and the timing note. */
    private static final AnalystTargetCalculator NO_TARGETS = new AnalystTargetCalculator(List.of(), 100.0, TODAY);

    @Test
    void aHealthyCompanyRisingForAYearHasNoSign() {
        RecommendationCandidate candidate = candidateWith(HEALTHY_COMPANY);

        assertThat(candidate.riskSigns()).isEmpty();
        assertThat(candidate.hasCompanyFacts()).isTrue();
    }

    @Test
    void losingMoneyIsANetMarginBelowZero() {
        assertThat(candidateWith(new CompanyFacts(10.0, 0.0, 0.5, 30.0)).riskSigns()).isEmpty();
        assertThat(candidateWith(new CompanyFacts(10.0, -0.01, 0.5, 30.0)).riskSigns())
                .containsExactly(RiskSign.LOSES_MONEY);
    }

    @Test
    void highDebtIsMoreThanTwiceTheEquity() {
        assertThat(candidateWith(new CompanyFacts(10.0, 20.0, 2.0, 30.0)).riskSigns()).isEmpty();
        assertThat(candidateWith(new CompanyFacts(10.0, 20.0, 2.01, 30.0)).riskSigns())
                .containsExactly(RiskSign.HIGH_DEBT);
    }

    @Test
    void expensiveIsAPeRatioAbove50() {
        assertThat(candidateWith(new CompanyFacts(10.0, 20.0, 0.5, 50.0)).riskSigns()).isEmpty();
        assertThat(candidateWith(new CompanyFacts(10.0, 20.0, 0.5, 50.01)).riskSigns())
                .containsExactly(RiskSign.EXPENSIVE);
    }

    @Test
    void revenueFallingIsAGrowthBelowZero() {
        assertThat(candidateWith(new CompanyFacts(0.0, 20.0, 0.5, 30.0)).riskSigns()).isEmpty();
        assertThat(candidateWith(new CompanyFacts(-0.01, 20.0, 0.5, 30.0)).riskSigns())
                .containsExactly(RiskSign.REVENUE_FALLING);
    }

    @Test
    void recoveringIsLowerThanAYearAgo() {
        assertThat(candidateWith(HEALTHY_COMPANY, 0.0).riskSigns()).isEmpty();
        assertThat(candidateWith(HEALTHY_COMPANY, -10.0).riskSigns()).containsExactly(RiskSign.RECOVERING);
        assertThat(candidateWith(HEALTHY_COMPANY, null).riskSigns()).isEmpty();
    }

    /** A loss-making company has no P/E: a figure Finnhub doesn't give raises no sign, so it is only losing money. */
    @Test
    void aMissingFigureRaisesNoSign() {
        RecommendationCandidate lossMakingCompany = candidateWith(new CompanyFacts(40.0, -15.0, null, null));

        assertThat(lossMakingCompany.riskSigns()).containsExactly(RiskSign.LOSES_MONEY);
        assertThat(lossMakingCompany.hasCompanyFacts()).isTrue();
    }

    /** Made-up figures that raise every sign at once, to see the order. */
    @Test
    void theSignsComeInTheirListedOrder() {
        RecommendationCandidate everySign = candidateWith(new CompanyFacts(-5.0, -10.0, 3.0, 80.0), -20.0);

        assertThat(everySign.riskSigns()).containsExactly(RiskSign.LOSES_MONEY, RiskSign.HIGH_DEBT,
                RiskSign.EXPENSIVE, RiskSign.REVENUE_FALLING, RiskSign.RECOVERING);
    }

    /** No key, or Finnhub's call failed: only the closes are checked, and there are no figures. */
    @Test
    void withoutFinnhubsAnswerOnlyTheClosesAreChecked() {
        RecommendationCandidate withoutAnswer =
                new RecommendationCandidate(1, hotStock(-20.0), NO_TARGETS, null, null);

        assertThat(withoutAnswer.riskSigns()).containsExactly(RiskSign.RECOVERING);
        assertThat(withoutAnswer.hasCompanyFacts()).isFalse();
        assertThat(withoutAnswer.reportsWithin14Days(TODAY)).isFalse();
    }

    /** Finnhub answers a symbol it doesn't know with every figure missing (session 2). */
    @Test
    void aSymbolFinnhubDoesNotKnowHasNoFigures() {
        RecommendationCandidate unknownSymbol = candidateWith(new CompanyFacts(null, null, null, null));

        assertThat(unknownSymbol.riskSigns()).isEmpty();
        assertThat(unknownSymbol.hasCompanyFacts()).isFalse();
    }

    @Test
    void theTimingNoteIsAReport0To14DaysAhead() {
        assertThat(candidateReportingOn(TODAY).reportsWithin14Days(TODAY)).isTrue();
        assertThat(candidateReportingOn(TODAY.plusDays(14)).reportsWithin14Days(TODAY)).isTrue();
        assertThat(candidateReportingOn(TODAY.plusDays(15)).reportsWithin14Days(TODAY)).isFalse();
        assertThat(candidateReportingOn(TODAY.minusDays(1)).reportsWithin14Days(TODAY)).isFalse();
        Stock withoutAReport = new Stock("ABCD", null, HEALTHY_COMPANY);
        assertThat(new RecommendationCandidate(1, hotStock(UP_IN_A_YEAR), NO_TARGETS, withoutAReport, null)
                .reportsWithin14Days(TODAY)).isFalse();
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

    private RecommendationCandidate candidateWith(CompanyFacts companyFacts) {
        return candidateWith(companyFacts, UP_IN_A_YEAR);
    }

    private RecommendationCandidate candidateWith(CompanyFacts companyFacts, Double yearReturnPercent) {
        return new RecommendationCandidate(1, hotStock(yearReturnPercent), NO_TARGETS,
                new Stock("ABCD", null, companyFacts), null);
    }

    private RecommendationCandidate candidateReportingOn(LocalDate reportDate) {
        Stock stock = new Stock("ABCD", new EarningsDate(reportDate, "amc"), HEALTHY_COMPANY);
        return new RecommendationCandidate(1, hotStock(UP_IN_A_YEAR), NO_TARGETS, stock, null);
    }

    /** A last close of 100, 5% above its 50-day average, with the long trend and at its year high. */
    private HotStockCalculator hotStock(Double yearReturnPercent) {
        Momentum momentum = new Momentum(TODAY, 100, 99.0, 100 / 1.05, 80.0, 100.0, 5.0, 3.0,
                TODAY.minusYears(1).plusDays(1), 100, yearReturnPercent, 10);
        return new HotStockCalculator(momentum);
    }
}
