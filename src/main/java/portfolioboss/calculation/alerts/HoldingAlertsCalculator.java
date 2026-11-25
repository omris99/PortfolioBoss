package portfolioboss.calculation.alerts;

import portfolioboss.calculation.marketdata.Momentum;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.Holding;
import portfolioboss.model.alerts.AlertType;
import portfolioboss.model.marketdata.MomentumLabel;
import portfolioboss.model.portfolio.HoldingStatus;
import portfolioboss.model.portfolio.HoldingWarning;
import portfolioboss.stocks.Stock;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Which of one holding's alert conditions are true now — alerts א to ו of MY_HOLDINGS_TODO.md (decisions 6 and 16), with
 * the numbers of the settings drawer; a type turned off is not checked. Each rule reads what the screen shows: the
 * momentum label, the next report, the Dow Jones targets, the unrealized P&L %, the weight on the Overview, the warning
 * triangle. A closed holding has only its warning checked: the other rules are about a position still held.
 *
 * @param holding             IB's figures as the last sync stored them ({@code NaN} where IB reported none)
 * @param momentum            {@code null} without stored closes
 * @param finnhubData         {@code null} while Finnhub never answered for the holding
 * @param analystPriceTargets the targets in its Dow Jones headlines, the half year the sync read
 * @param warnings            {@code HoldingHistory.warnings}: at most one
 * @param percentOfAccount    its share of the whole account ({@code PortfolioWeightsCalculator}); {@code null} when the
 *                            Overview leaves it out
 * @param today               the last sync's day: the report's days and the cuts' days are counted from it
 */
public record HoldingAlertsCalculator(Holding holding, HoldingStatus status, Momentum momentum, Stock finnhubData,
                               List<AnalystPriceTarget> analystPriceTargets, List<HoldingWarning> warnings,
                               Double percentOfAccount, AlertSettings settings, LocalDate today) {

    /** So that exactly 15% counts as it should despite floating-point rounding, as in {@code HotStockCalculator}. */
    private static final double ROUNDING_TOLERANCE = 1e-9;

    /** A date in a message as every screen writes one (round 6): "30/11/2026". */
    private static final DateTimeFormatter DATE_AS_THE_SCREENS_WRITE_IT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public List<AlertCondition> conditionsTrueNow() {
        List<AlertCondition> conditions = new ArrayList<>();
        tradesDontMatchIbCondition().ifPresent(conditions::add);
        if (status == HoldingStatus.CLOSED) {
            return conditions;
        }
        conditions.addAll(trendConditions());
        earningsSoonCondition().ifPresent(conditions::add);
        conditions.addAll(analystCutConditions());
        farBelowAverageCostCondition().ifPresent(conditions::add);
        aboveWeightLimitCondition().ifPresent(conditions::add);
        return conditions;
    }

    /**
     * א: the momentum turned weak, and the last close under the 50-day and the 200-day averages, each on its own. The
     * label in the message is a word, as every screen writes it (round 6), not the enum's capitals.
     */
    private List<AlertCondition> trendConditions() {
        if (momentum == null) {
            return List.of();
        }
        List<AlertCondition> conditions = new ArrayList<>();
        if (settings.isEnabled(AlertType.MOMENTUM_TURNED_WEAK) && momentum.label() == MomentumLabel.WEAK) {
            conditions.add(conditionOf(AlertType.MOMENTUM_TURNED_WEAK, null,
                    "Momentum turned weak: " + momentum.score() + " of 5"));
        }
        if (settings.isEnabled(AlertType.CLOSED_BELOW_SMA50) && Boolean.FALSE.equals(momentum.aboveSma50())) {
            conditions.add(conditionOf(AlertType.CLOSED_BELOW_SMA50, null, "Closed below its 50-day average: "
                    + priceText(momentum.lastClose()) + " against " + priceText(momentum.sma50())));
        }
        if (settings.isEnabled(AlertType.CLOSED_BELOW_SMA200) && Boolean.FALSE.equals(momentum.aboveSma200())) {
            conditions.add(conditionOf(AlertType.CLOSED_BELOW_SMA200, null, "Closed below its 200-day average: "
                    + priceText(momentum.lastClose()) + " against " + priceText(momentum.sma200())));
        }
        return conditions;
    }

    /**
     * ב: the next report today, or within the drawer's number of days. Its date is the subject — each report once —, as
     * {@code LocalDate} writes it: the key of an open alert, so it never changes with the message's wording.
     */
    private Optional<AlertCondition> earningsSoonCondition() {
        if (!settings.isEnabled(AlertType.EARNINGS_SOON) || finnhubData == null
                || finnhubData.nextEarningsDate() == null) {
            return Optional.empty();
        }
        LocalDate reportDate = finnhubData.nextEarningsDate().date();
        long daysUntilReport = ChronoUnit.DAYS.between(today, reportDate);
        if (daysUntilReport < 0 || daysUntilReport > daysOf(AlertType.EARNINGS_SOON)) {
            return Optional.empty();
        }
        return Optional.of(conditionOf(AlertType.EARNINGS_SOON, reportDate.toString(),
                "Reports on " + reportDate.format(DATE_AS_THE_SCREENS_WRITE_IT) + ", " + whenText(daysUntilReport)));
    }

    /**
     * ג: every target a firm cut — the new one below the one it replaced — published in the drawer's number of days back,
     * that day included. The firm and the headline's time are the subject: each cut once.
     */
    private List<AlertCondition> analystCutConditions() {
        if (!settings.isEnabled(AlertType.ANALYST_CUT_TARGET)) {
            return List.of();
        }
        LocalDate firstDayCounted = today.minusDays(daysOf(AlertType.ANALYST_CUT_TARGET));
        return analystPriceTargets.stream()
                .filter(this::isCut)
                .filter(target -> !target.publishedAt().toLocalDate().isBefore(firstDayCounted))
                .map(target -> conditionOf(AlertType.ANALYST_CUT_TARGET, target.firm() + " " + target.publishedAt(),
                        target.firm() + " cut its target to " + priceText(target.priceTarget()) + " from "
                                + priceText(target.previousPriceTarget())))
                .toList();
    }

    private boolean isCut(AnalystPriceTarget target) {
        return target.previousPriceTarget() != null && target.priceTarget() < target.previousPriceTarget();
    }

    /** ד: the unrealized P&L the drawer's percent below 0 or more — exactly that much counts. */
    private Optional<AlertCondition> farBelowAverageCostCondition() {
        double unrealizedPnlPercent = holding.unrealizedPnlPercent();
        if (!settings.isEnabled(AlertType.FAR_BELOW_AVERAGE_COST) || !Double.isFinite(unrealizedPnlPercent)) {
            return Optional.empty();
        }
        double leastPercentBelow = percentOf(AlertType.FAR_BELOW_AVERAGE_COST);
        if (unrealizedPnlPercent > -leastPercentBelow + ROUNDING_TOLERANCE) {
            return Optional.empty();
        }
        return Optional.of(conditionOf(AlertType.FAR_BELOW_AVERAGE_COST, null,
                "Down " + percentText(-unrealizedPnlPercent) + " from the average cost of "
                        + priceText(holding.averageCost())));
    }

    /** ה: more than the drawer's percent of the whole account — exactly the limit is still within it. */
    private Optional<AlertCondition> aboveWeightLimitCondition() {
        if (!settings.isEnabled(AlertType.ABOVE_WEIGHT_LIMIT) || percentOfAccount == null) {
            return Optional.empty();
        }
        double weightLimitPercent = percentOf(AlertType.ABOVE_WEIGHT_LIMIT);
        if (percentOfAccount <= weightLimitPercent + ROUNDING_TOLERANCE) {
            return Optional.empty();
        }
        return Optional.of(conditionOf(AlertType.ABOVE_WEIGHT_LIMIT, null, percentText(percentOfAccount)
                + " of the whole account, above the limit of " + percentText(weightLimitPercent)));
    }

    /**
     * ו: the holding's warning triangle — a buy not entered, a sell not entered, or quantities that don't add up. Which
     * warning is the subject: a different one later is a new alert.
     */
    private Optional<AlertCondition> tradesDontMatchIbCondition() {
        if (!settings.isEnabled(AlertType.TRADES_DONT_MATCH_IB) || warnings.isEmpty()) {
            return Optional.empty();
        }
        HoldingWarning warning = warnings.getFirst();
        return Optional.of(conditionOf(AlertType.TRADES_DONT_MATCH_IB, warning.type().name(), warning.message()));
    }

    private AlertCondition conditionOf(AlertType type, String subject, String message) {
        return new AlertCondition(new AlertKey(type, holding.conId(), subject), holding.symbol(), message);
    }

    private long daysOf(AlertType type) {
        return settings.thresholdOf(type).longValue();
    }

    private double percentOf(AlertType type) {
        return settings.thresholdOf(type).doubleValue();
    }

    /** "in 6 days", "in 1 day", "today". */
    private String whenText(long daysUntilReport) {
        if (daysUntilReport == 0) {
            return "today";
        }
        return daysUntilReport == 1 ? "in 1 day" : "in " + daysUntilReport + " days";
    }

    /** "182.40" — {@code Locale.ROOT}, so that the decimal point is a point on any computer. */
    private String priceText(double price) {
        return String.format(Locale.ROOT, "%.2f", price);
    }

    /** "17.3%". */
    private String percentText(double percent) {
        return String.format(Locale.ROOT, "%.1f%%", percent);
    }
}
