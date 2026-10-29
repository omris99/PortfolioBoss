package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ib.AnalystPriceTarget;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

/**
 * The analysts' target of RECOMMENDATIONS_TODO.md, decision 14: each firm's latest target of the half year before the
 * scan, their median, three firms at least, and 15% above the last close for a pick. A stock closing at 100 unless a
 * test says otherwise.
 */
class AnalystTargetCalculatorTest {

    private static final LocalDate SCAN_DATE = LocalDate.of(2026, 10, 27);
    private static final double LAST_CLOSE = 100;

    @Test
    void eachFirmCountsWithItsLatestTarget() {
        AnalystTargetCalculator analystTarget = new AnalystTargetCalculator(List.of(
                target("Wells Fargo", SCAN_DATE.minusMonths(3), 110),
                target("Wells Fargo", SCAN_DATE.minusDays(10), 130),
                target("Barclays", SCAN_DATE.minusDays(20), 120),
                target("Needham", SCAN_DATE.minusDays(30), 125)), LAST_CLOSE, SCAN_DATE);

        assertThat(analystTarget.firmCount()).isEqualTo(3);
        assertThat(analystTarget.latestTargetOfEachFirm())
                .extracting(AnalystPriceTarget::firm, AnalystPriceTarget::priceTarget)
                .containsExactly(tuple("Wells Fargo", 130.0), tuple("Barclays", 120.0), tuple("Needham", 125.0));
        assertThat(analystTarget.medianTarget()).isEqualTo(125.0);
    }

    /** Six months back from the scan's day, that day included; Bernstein's $250 of May 28 counts for SNOW's scan. */
    @Test
    void aTargetCountsForHalfAYearBeforeTheScan() {
        AnalystTargetCalculator analystTarget = new AnalystTargetCalculator(List.of(
                target("Bernstein", LocalDate.of(2026, 5, 28), 250),
                target("Firm on the first day", SCAN_DATE.minusMonths(6), 400),
                target("Firm a day too early", SCAN_DATE.minusMonths(6).minusDays(1), 500)), 338.24, SCAN_DATE);

        assertThat(analystTarget.latestTargetOfEachFirm())
                .extracting(AnalystPriceTarget::firm)
                .containsExactly("Bernstein", "Firm on the first day");
    }

    @Test
    void theMedianIsTheMiddleTargetOrTheAverageOfTheTwoInTheMiddle() {
        assertThat(targetOfFirms(90, 130, 110).medianTarget()).isEqualTo(110.0);
        assertThat(targetOfFirms(90, 130, 110, 120).medianTarget()).isEqualTo(115.0);
    }

    /**
     * CRWD split 1:4 in July 2026: a firm that hasn't updated since still has a target four times too high. It pulls an
     * average up — (230 + 235 + 240 + 250 + 780) / 5 is 347 —, not the median.
     */
    @Test
    void aTargetFromBeforeASplitDoesNotMoveTheMedian() {
        AnalystTargetCalculator analystTarget = targetOfFirms(230, 235, 240, 250, 780);

        assertThat(analystTarget.medianTarget()).isEqualTo(240.0);
    }

    /** Fewer than three firms is an opinion, not a target. */
    @Test
    void underThreeFirmsThereIsNoTarget() {
        AnalystTargetCalculator twoFirms = targetOfFirms(150, 160);

        assertThat(twoFirms.firmCount()).isEqualTo(2);
        assertThat(twoFirms.medianTarget()).isNull();
        assertThat(twoFirms.upsidePercent()).isNull();
        assertThat(twoFirms.hasEnoughUpside()).isFalse();
        assertThat(targetOfFirms().medianTarget()).isNull();
    }

    @Test
    void theUpsideIsTheMedianAgainstTheLastClose() {
        assertThat(targetOfFirms(120, 125, 130).upsidePercent()).isCloseTo(25, within(1e-9));
        assertThat(targetOfFirms(80, 90, 95).upsidePercent()).isCloseTo(-10, within(1e-9));
        assertThat(new AnalystTargetCalculator(List.of(target("A", SCAN_DATE, 120), target("B", SCAN_DATE, 125),
                target("C", SCAN_DATE, 130)), null, SCAN_DATE).upsidePercent()).isNull();
    }

    /** $115 against $100 is 14.999999999999991% in floating point: still exactly 15%, enough. */
    @Test
    void enoughUpsideIsFifteenPercentOrMore() {
        assertThat(targetOfFirms(115, 115, 115).hasEnoughUpside()).isTrue();
        assertThat(targetOfFirms(114.9, 114.9, 114.9).hasEnoughUpside()).isFalse();
        assertThat(targetOfFirms(90, 95, 100).hasEnoughUpside()).isFalse();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** One target per firm, each a week before the scan. */
    private AnalystTargetCalculator targetOfFirms(double... targetOfEachFirm) {
        List<AnalystPriceTarget> targets = new ArrayList<>();
        for (int firmNumber = 1; firmNumber <= targetOfEachFirm.length; firmNumber++) {
            targets.add(target("Firm " + firmNumber, SCAN_DATE.minusWeeks(1), targetOfEachFirm[firmNumber - 1]));
        }
        return new AnalystTargetCalculator(targets, LAST_CLOSE, SCAN_DATE);
    }

    private AnalystPriceTarget target(String firm, LocalDate publishedOn, double priceTarget) {
        LocalDateTime publishedAt = publishedOn.atTime(12, 0);
        return new AnalystPriceTarget(publishedAt, firm, priceTarget, null, firm + " set its target");
    }
}
