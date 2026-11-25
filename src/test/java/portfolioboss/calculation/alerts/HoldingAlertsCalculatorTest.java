package portfolioboss.calculation.alerts;

import org.junit.jupiter.api.Test;
import portfolioboss.calculation.marketdata.Momentum;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.Holding;
import portfolioboss.model.alerts.AlertType;
import portfolioboss.model.portfolio.HoldingStatus;
import portfolioboss.model.portfolio.HoldingWarning;
import portfolioboss.model.portfolio.HoldingWarningType;
import portfolioboss.stocks.EarningsDate;
import portfolioboss.stocks.Stock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Each of alerts א to ו at its limits, on a made-up holding ABCD — 100 shares at an average of $100 — that raises nothing
 * but what each test gives it. The day is the last sync's, 2026-11-11.
 */
class HoldingAlertsCalculatorTest {

    private static final LocalDate SYNC_DATE = LocalDate.of(2026, 11, 11);
    private static final int ABCD_CON_ID = 8101;
    private static final AlertSettings DEFAULT_SETTINGS = new AlertSettings(Map.of());
    private static final HoldingWarning QUANTITY_MISMATCH = new HoldingWarning(HoldingWarningType.QUANTITY_MISMATCH,
            "The trades entered add up to 90 shares; IB reports 100.");

    // ── א: the trend ────────────────────────────────────────────────────────────────────────────

    /** Below every average, a falling month: a score of 0, so WEAK, and below the 50- and the 200-day averages. */
    @Test
    void weakMomentumBelowBothAveragesRaisesThreeAlerts() {
        Momentum fallingMomentum = momentumOf(80.0, 90.0, 100.0, 110.0, 120.0, -5.0, 2.0);

        List<AlertCondition> conditions = conditionsOf(calculatorWith(fallingMomentum));

        assertThat(conditions).extracting(AlertCondition::message).containsExactly(
                "Momentum turned weak: 0 of 5",
                "Closed below its 50-day average: 80.00 against 100.00",
                "Closed below its 200-day average: 80.00 against 110.00");
        assertThat(conditions).extracting(condition -> condition.key().subject()).containsOnlyNulls();
    }

    @Test
    void aStrongTrendRaisesNothing() {
        Momentum risingMomentum = momentumOf(120.0, 110.0, 100.0, 90.0, 125.0, 8.0, 2.0);

        assertThat(conditionsOf(calculatorWith(risingMomentum))).isEmpty();
    }

    /** Under 200 closes there is no 200-day average and no label: only what is known raises an alert. */
    @Test
    void aYoungStockHasOnlyTheFiftyDayAlert() {
        Momentum youngMomentum = momentumOf(80.0, 90.0, 100.0, null, 120.0, -5.0, 2.0);

        assertThat(conditionsOf(calculatorWith(youngMomentum))).extracting(condition -> condition.key().type())
                .containsExactly(AlertType.CLOSED_BELOW_SMA50);
    }

    // ── ב: the report ───────────────────────────────────────────────────────────────────────────

    @Test
    void aReportInSevenDaysCountsAndHasItsDateAsTheSubject() {
        List<AlertCondition> conditions = conditionsOf(calculatorWithReportOn(SYNC_DATE.plusDays(7), DEFAULT_SETTINGS));

        assertThat(conditions).singleElement().satisfies(condition -> {
            assertThat(condition.key()).isEqualTo(new AlertKey(AlertType.EARNINGS_SOON, ABCD_CON_ID, "2026-11-18"));
            assertThat(condition.message()).isEqualTo("Reports on 18/11/2026, in 7 days");
        });
    }

    @Test
    void aReportInEightDaysDoesNot() {
        assertThat(conditionsOf(calculatorWithReportOn(SYNC_DATE.plusDays(8), DEFAULT_SETTINGS))).isEmpty();
    }

    @Test
    void aReportTodayCountsAndOneAlreadyPastDoesNot() {
        assertThat(conditionsOf(calculatorWithReportOn(SYNC_DATE, DEFAULT_SETTINGS))).extracting(AlertCondition::message)
                .containsExactly("Reports on 11/11/2026, today");
        assertThat(conditionsOf(calculatorWithReportOn(SYNC_DATE.minusDays(1), DEFAULT_SETTINGS))).isEmpty();
    }

    @Test
    void theSettingsNumberOfDaysIsTheLimit() {
        AlertSettings tenDays = settingsWith(AlertType.EARNINGS_SOON, true, 10);

        assertThat(conditionsOf(calculatorWithReportOn(SYNC_DATE.plusDays(9), tenDays))).hasSize(1);
    }

    // ── ג: an analyst's cut ─────────────────────────────────────────────────────────────────────

    @Test
    void aCutSevenDaysAgoCountsWithTheFirmAndTimeAsTheSubject() {
        AnalystPriceTarget cut = targetOf("Morgan Stanley", LocalDateTime.of(2026, 11, 4, 14, 5), 410.0, 450.0);

        assertThat(conditionsOf(calculatorWithTargets(List.of(cut)))).singleElement().satisfies(condition -> {
            assertThat(condition.key().subject()).isEqualTo("Morgan Stanley 2026-11-04T14:05");
            assertThat(condition.message()).isEqualTo("Morgan Stanley cut its target to 410.00 from 450.00");
        });
    }

    @Test
    void aCutEightDaysAgoARaiseAndATargetWithoutAnEarlierOneDoNot() {
        List<AnalystPriceTarget> targets = List.of(
                targetOf("Morgan Stanley", LocalDateTime.of(2026, 11, 3, 23, 59), 410.0, 450.0),
                targetOf("Mizuho", LocalDateTime.of(2026, 11, 10, 9, 0), 480.0, 450.0),
                targetOf("Daiwa", LocalDateTime.of(2026, 11, 10, 9, 0), 300.0, null));

        assertThat(conditionsOf(calculatorWithTargets(targets))).isEmpty();
    }

    @Test
    void twoFirmsCutsAreTwoConditions() {
        List<AnalystPriceTarget> targets = List.of(
                targetOf("Morgan Stanley", LocalDateTime.of(2026, 11, 9, 14, 5), 410.0, 450.0),
                targetOf("Mizuho", LocalDateTime.of(2026, 11, 10, 9, 0), 400.0, 440.0));

        assertThat(conditionsOf(calculatorWithTargets(targets))).hasSize(2);
    }

    // ── ד: far below the average cost ───────────────────────────────────────────────────────────

    @Test
    void exactlyFifteenPercentBelowTheAverageCounts() {
        assertThat(conditionsOf(calculatorWithUnrealizedPnl(-1_500))).extracting(AlertCondition::message)
                .containsExactly("Down 15.0% from the average cost of 100.00");
    }

    @Test
    void fourteenPointNinePercentBelowDoesNot() {
        assertThat(conditionsOf(calculatorWithUnrealizedPnl(-1_490))).isEmpty();
    }

    // ── ה: the weight limit ─────────────────────────────────────────────────────────────────────

    @Test
    void exactlyTheLimitIsStillWithinIt() {
        assertThat(conditionsOf(calculatorWithWeight(15.0, DEFAULT_SETTINGS))).isEmpty();
    }

    @Test
    void aboveTheLimitCounts() {
        assertThat(conditionsOf(calculatorWithWeight(15.1, DEFAULT_SETTINGS))).extracting(AlertCondition::message)
                .containsExactly("15.1% of the whole account, above the limit of 15.0%");
    }

    @Test
    void theSettingsLimitIsTheOneChecked() {
        AlertSettings twentyPercent = settingsWith(AlertType.ABOVE_WEIGHT_LIMIT, true, 20);

        assertThat(conditionsOf(calculatorWithWeight(18.0, twentyPercent))).isEmpty();
        assertThat(conditionsOf(calculatorWithWeight(21.0, twentyPercent))).hasSize(1);
    }

    // ── ו: the trades ───────────────────────────────────────────────────────────────────────────

    @Test
    void aWarningIsAnAlertWithItsTypeAsTheSubject() {
        HoldingAlertsCalculator calculator = new HoldingAlertsCalculator(holdingWithUnrealizedPnl(0), HoldingStatus.OPEN,
                null, null, List.of(), List.of(QUANTITY_MISMATCH), null, DEFAULT_SETTINGS, SYNC_DATE);

        assertThat(conditionsOf(calculator)).singleElement().satisfies(condition -> {
            assertThat(condition.key().subject()).isEqualTo("QUANTITY_MISMATCH");
            assertThat(condition.message()).isEqualTo("The trades entered add up to 90 shares; IB reports 100.");
        });
    }

    /** A closed holding has only its warning checked, whatever else its last figures would raise. */
    @Test
    void aClosedHoldingHasOnlyItsWarningChecked() {
        HoldingWarning closedWithoutSell = new HoldingWarning(HoldingWarningType.CLOSED_WITHOUT_SELL,
                "IB no longer reports this holding, but no sell closing it was entered.");
        HoldingAlertsCalculator calculator = new HoldingAlertsCalculator(holdingWithUnrealizedPnl(-5_000),
                HoldingStatus.CLOSED, momentumOf(80.0, 90.0, 100.0, 110.0, 120.0, -5.0, 2.0), null, List.of(),
                List.of(closedWithoutSell), 40.0, DEFAULT_SETTINGS, SYNC_DATE);

        assertThat(conditionsOf(calculator)).extracting(condition -> condition.key().type())
                .containsExactly(AlertType.TRADES_DONT_MATCH_IB);
    }

    // ── the settings ────────────────────────────────────────────────────────────────────────────

    @Test
    void aTypeTurnedOffIsNotChecked() {
        AlertSettings farBelowOff = settingsWith(AlertType.FAR_BELOW_AVERAGE_COST, false, 15);
        HoldingAlertsCalculator calculator = new HoldingAlertsCalculator(holdingWithUnrealizedPnl(-5_000),
                HoldingStatus.OPEN, null, null, List.of(), List.of(), null, farBelowOff, SYNC_DATE);

        assertThat(conditionsOf(calculator)).isEmpty();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private List<AlertCondition> conditionsOf(HoldingAlertsCalculator calculator) {
        return calculator.conditionsTrueNow();
    }

    private HoldingAlertsCalculator calculatorWith(Momentum momentum) {
        return new HoldingAlertsCalculator(holdingWithUnrealizedPnl(0), HoldingStatus.OPEN, momentum, null, List.of(),
                List.of(), null, DEFAULT_SETTINGS, SYNC_DATE);
    }

    private HoldingAlertsCalculator calculatorWithReportOn(LocalDate reportDate, AlertSettings settings) {
        Stock finnhubData = new Stock("ABCD", new EarningsDate(reportDate, "amc"), null);
        return new HoldingAlertsCalculator(holdingWithUnrealizedPnl(0), HoldingStatus.OPEN, null, finnhubData, List.of(),
                List.of(), null, settings, SYNC_DATE);
    }

    private HoldingAlertsCalculator calculatorWithTargets(List<AnalystPriceTarget> targets) {
        return new HoldingAlertsCalculator(holdingWithUnrealizedPnl(0), HoldingStatus.OPEN, null, null, targets,
                List.of(), null, DEFAULT_SETTINGS, SYNC_DATE);
    }

    private HoldingAlertsCalculator calculatorWithUnrealizedPnl(double unrealizedPnl) {
        return new HoldingAlertsCalculator(holdingWithUnrealizedPnl(unrealizedPnl), HoldingStatus.OPEN, null, null,
                List.of(), List.of(), null, DEFAULT_SETTINGS, SYNC_DATE);
    }

    private HoldingAlertsCalculator calculatorWithWeight(double percentOfAccount, AlertSettings settings) {
        return new HoldingAlertsCalculator(holdingWithUnrealizedPnl(0), HoldingStatus.OPEN, null, null, List.of(),
                List.of(), percentOfAccount, settings, SYNC_DATE);
    }

    /** 100 shares at an average of $100, so a P&L of −1,500 is −15%. */
    private Holding holdingWithUnrealizedPnl(double unrealizedPnl) {
        double marketValue = 10_000 + unrealizedPnl;
        return new Holding("ABCD", ABCD_CON_ID, "STK", "USD", 100, 100.0, marketValue / 100, marketValue,
                unrealizedPnl, 0.0, "U7654321");
    }

    /** The figures the five checks and the two averages read; the year's figures don't matter here. */
    private Momentum momentumOf(double lastClose, Double sma20, Double sma50, Double sma200, Double high20,
                                Double oneMonthReturnPercent, Double spyOneMonthReturnPercent) {
        return new Momentum(SYNC_DATE, lastClose, sma20, sma50, sma200, high20, oneMonthReturnPercent,
                spyOneMonthReturnPercent, SYNC_DATE.minusYears(1), 130.0, 5.0, 20.0);
    }

    private AnalystPriceTarget targetOf(String firm, LocalDateTime publishedAt, double priceTarget,
                                        Double previousPriceTarget) {
        return new AnalystPriceTarget(publishedAt, firm, priceTarget, previousPriceTarget,
                "ABCD Price Target Cut to $" + priceTarget + "/Share From $" + previousPriceTarget + " by " + firm);
    }

    private AlertSettings settingsWith(AlertType type, boolean enabled, int threshold) {
        return new AlertSettings(Map.of(type, new AlertSetting(type, enabled, BigDecimal.valueOf(threshold))));
    }
}
