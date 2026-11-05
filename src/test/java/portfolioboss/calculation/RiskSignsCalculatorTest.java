package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.model.RiskSign;
import portfolioboss.stocks.CompanyFacts;
import portfolioboss.stocks.EarningsDate;
import portfolioboss.stocks.Stock;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The risk signs and the timing note of RECOMMENDATIONS_TODO.md, decision 5, on figures made up directly — for a scanned
 * stock and a holding alike. Every stock closes at 100 and is up 20% in a year unless a test says otherwise.
 */
class RiskSignsCalculatorTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 27);
    /** Growing, profitable, little debt, a P/E of 30: no sign. */
    private static final CompanyFacts HEALTHY_COMPANY = new CompanyFacts(10.0, 20.0, 0.5, 30.0);
    private static final Double UP_IN_A_YEAR = 20.0;

    @Test
    void aHealthyCompanyRisingForAYearHasNoSign() {
        RiskSignsCalculator healthyCompany = calculatorWithFacts(HEALTHY_COMPANY);

        assertThat(healthyCompany.riskSigns()).isEmpty();
        assertThat(healthyCompany.hasCompanyFacts()).isTrue();
    }

    @Test
    void losingMoneyIsANetMarginBelowZero() {
        assertThat(calculatorWithFacts(new CompanyFacts(10.0, 0.0, 0.5, 30.0)).riskSigns()).isEmpty();
        assertThat(calculatorWithFacts(new CompanyFacts(10.0, -0.01, 0.5, 30.0)).riskSigns())
                .containsExactly(RiskSign.LOSES_MONEY);
    }

    @Test
    void highDebtIsMoreThanTwiceTheEquity() {
        assertThat(calculatorWithFacts(new CompanyFacts(10.0, 20.0, 2.0, 30.0)).riskSigns()).isEmpty();
        assertThat(calculatorWithFacts(new CompanyFacts(10.0, 20.0, 2.01, 30.0)).riskSigns())
                .containsExactly(RiskSign.HIGH_DEBT);
    }

    @Test
    void expensiveIsAPeRatioAbove50() {
        assertThat(calculatorWithFacts(new CompanyFacts(10.0, 20.0, 0.5, 50.0)).riskSigns()).isEmpty();
        assertThat(calculatorWithFacts(new CompanyFacts(10.0, 20.0, 0.5, 50.01)).riskSigns())
                .containsExactly(RiskSign.EXPENSIVE);
    }

    @Test
    void revenueFallingIsAGrowthBelowZero() {
        assertThat(calculatorWithFacts(new CompanyFacts(0.0, 20.0, 0.5, 30.0)).riskSigns()).isEmpty();
        assertThat(calculatorWithFacts(new CompanyFacts(-0.01, 20.0, 0.5, 30.0)).riskSigns())
                .containsExactly(RiskSign.REVENUE_FALLING);
    }

    @Test
    void recoveringIsLowerThanAYearAgo() {
        assertThat(calculatorWithFacts(HEALTHY_COMPANY, 0.0).riskSigns()).isEmpty();
        assertThat(calculatorWithFacts(HEALTHY_COMPANY, -10.0).riskSigns()).containsExactly(RiskSign.RECOVERING);
        assertThat(calculatorWithFacts(HEALTHY_COMPANY, null).riskSigns()).isEmpty();
    }

    /** A holding whose closes IB never sent: only Finnhub's figures are checked. */
    @Test
    void withoutClosesOnlyFinnhubsFiguresAreChecked() {
        RiskSignsCalculator withoutCloses =
                new RiskSignsCalculator(new Stock("ABCD", null, new CompanyFacts(10.0, -5.0, 0.5, 30.0)), null);

        assertThat(withoutCloses.riskSigns()).containsExactly(RiskSign.LOSES_MONEY);
        assertThat(withoutCloses.hasCompanyFacts()).isTrue();
    }

    /** A loss-making company has no P/E: a figure Finnhub doesn't give raises no sign, so it is only losing money. */
    @Test
    void aMissingFigureRaisesNoSign() {
        RiskSignsCalculator lossMakingCompany = calculatorWithFacts(new CompanyFacts(40.0, -15.0, null, null));

        assertThat(lossMakingCompany.riskSigns()).containsExactly(RiskSign.LOSES_MONEY);
        assertThat(lossMakingCompany.hasCompanyFacts()).isTrue();
    }

    /** Made-up figures that raise every sign at once, to see the order. */
    @Test
    void theSignsComeInTheirListedOrder() {
        RiskSignsCalculator everySign = calculatorWithFacts(new CompanyFacts(-5.0, -10.0, 3.0, 80.0), -20.0);

        assertThat(everySign.riskSigns()).containsExactly(RiskSign.LOSES_MONEY, RiskSign.HIGH_DEBT,
                RiskSign.EXPENSIVE, RiskSign.REVENUE_FALLING, RiskSign.RECOVERING);
    }

    /** No key, or Finnhub's call failed: only the closes are checked, and there are no figures. */
    @Test
    void withoutFinnhubsAnswerOnlyTheClosesAreChecked() {
        RiskSignsCalculator withoutAnswer = new RiskSignsCalculator(null, momentumWithYearReturn(-20.0));

        assertThat(withoutAnswer.riskSigns()).containsExactly(RiskSign.RECOVERING);
        assertThat(withoutAnswer.hasCompanyFacts()).isFalse();
        assertThat(withoutAnswer.reportsWithin14Days(TODAY)).isFalse();
    }

    /** Finnhub answers a symbol it doesn't know with every figure missing (RECOMMENDATIONS_TODO.md, session 2). */
    @Test
    void aSymbolFinnhubDoesNotKnowHasNoFigures() {
        RiskSignsCalculator unknownSymbol = calculatorWithFacts(new CompanyFacts(null, null, null, null));

        assertThat(unknownSymbol.riskSigns()).isEmpty();
        assertThat(unknownSymbol.hasCompanyFacts()).isFalse();
    }

    @Test
    void theTimingNoteIsAReport0To14DaysAhead() {
        assertThat(calculatorWithReportOn(TODAY).reportsWithin14Days(TODAY)).isTrue();
        assertThat(calculatorWithReportOn(TODAY.plusDays(14)).reportsWithin14Days(TODAY)).isTrue();
        assertThat(calculatorWithReportOn(TODAY.plusDays(15)).reportsWithin14Days(TODAY)).isFalse();
        assertThat(calculatorWithReportOn(TODAY.minusDays(1)).reportsWithin14Days(TODAY)).isFalse();
        Stock withoutAReport = new Stock("ABCD", null, HEALTHY_COMPANY);
        assertThat(new RiskSignsCalculator(withoutAReport, momentumWithYearReturn(UP_IN_A_YEAR))
                .reportsWithin14Days(TODAY)).isFalse();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private RiskSignsCalculator calculatorWithFacts(CompanyFacts companyFacts) {
        return calculatorWithFacts(companyFacts, UP_IN_A_YEAR);
    }

    private RiskSignsCalculator calculatorWithFacts(CompanyFacts companyFacts, Double yearReturnPercent) {
        return new RiskSignsCalculator(new Stock("ABCD", null, companyFacts),
                momentumWithYearReturn(yearReturnPercent));
    }

    private RiskSignsCalculator calculatorWithReportOn(LocalDate reportDate) {
        Stock stock = new Stock("ABCD", new EarningsDate(reportDate, "amc"), HEALTHY_COMPANY);
        return new RiskSignsCalculator(stock, momentumWithYearReturn(UP_IN_A_YEAR));
    }

    /** A last close of 100, 5% above its 50-day average, with the long trend and at its year high. */
    private Momentum momentumWithYearReturn(Double yearReturnPercent) {
        return new Momentum(TODAY, 100, 99.0, 100 / 1.05, 80.0, 100.0, 5.0, 3.0,
                TODAY.minusYears(1).plusDays(1), 100, yearReturnPercent, 10);
    }
}
