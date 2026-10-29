package portfolioboss.calculation;

import portfolioboss.ib.AnalystPriceTarget;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The analysts' price target of a stock, from the targets in its Dow Jones headlines (RECOMMENDATIONS_TODO.md, decision
 * 14): each firm's latest target of the half year before the scan, and the median of those — the middle one, or the
 * average of the two in the middle. Derived on every read from the stored targets, never stored.
 *
 * <p>A median and not an average: a target is in the price terms of its own day, and after a stock split a firm that
 * hasn't updated yet still has a target several times too high. CRWD split 1:4 in July 2026; on 2026-10-29 its average
 * came out 4.8% above the price, its median 5.8% below. One stale target pulls an average, but can't move the middle
 * while most firms have updated. Fewer than three firms is an opinion, not a target, and counts as none (the user's
 * rule).
 *
 * <p>Example, SNOW on 2026-10-27: 36 firms in the half year — Bernstein's $250 of May 28 among them, the stock at
 * $338.24 —, a median of $430, 27.1% above the price.
 *
 * @param targets   every target stored for the stock, any firm, any date
 * @param lastClose the stock's last close, which the target is measured against; {@code null} without one
 * @param scanDate  the day of the scan: the half year is counted back from it, not from the clock
 */
public record AnalystTargetCalculator(List<AnalystPriceTarget> targets, Double lastClose, LocalDate scanDate) {

    /** A target needs at least this many firms (decision 14). */
    private static final int LEAST_FIRMS = 3;
    /** The least upside a pick needs: "a rise of at least 15% by the analysts" (decision 14). An opening number. */
    private static final double LEAST_UPSIDE_PERCENT = 15;
    /**
     * So that exactly 15% counts despite floating-point rounding — $115 against $100 is 14.999999999999991% —, as in
     * {@link HotStockCalculator}.
     */
    private static final double ROUNDING_TOLERANCE = 1e-9;

    /** Each firm's latest target of the half year before the scan, the newest first. */
    public List<AnalystPriceTarget> latestTargetOfEachFirm() {
        LocalDate oldestCounted = scanDate.minusMonths(AnalystPriceTarget.MONTHS_COUNTED);
        Map<String, AnalystPriceTarget> latestTargetByFirm = new LinkedHashMap<>();
        targets.stream()
                .filter(target -> !target.publishedAt().toLocalDate().isBefore(oldestCounted))
                .sorted(Comparator.comparing(AnalystPriceTarget::publishedAt).reversed())
                .forEach(target -> latestTargetByFirm.putIfAbsent(target.firm(), target));
        return List.copyOf(latestTargetByFirm.values());
    }

    public int firmCount() {
        return latestTargetOfEachFirm().size();
    }

    /** The median of the firms' latest targets; {@code null} under three firms. */
    public Double medianTarget() {
        List<Double> targetsLowestFirst = latestTargetOfEachFirm().stream()
                .map(AnalystPriceTarget::priceTarget)
                .sorted()
                .toList();
        if (targetsLowestFirst.size() < LEAST_FIRMS) {
            return null;
        }
        int middle = targetsLowestFirst.size() / 2;
        boolean oddCount = targetsLowestFirst.size() % 2 == 1;
        return oddCount
                ? targetsLowestFirst.get(middle)
                : (targetsLowestFirst.get(middle - 1) + targetsLowestFirst.get(middle)) / 2;
    }

    /**
     * How far the median target is above the last close, in percent — negative below it; {@code null} without a target
     * or a close.
     */
    public Double upsidePercent() {
        Double medianTarget = medianTarget();
        if (medianTarget == null || lastClose == null || lastClose <= 0) {
            return null;
        }
        return (medianTarget / lastClose - 1) * 100;
    }

    /** The median target is at least 15% above the last close (15% exactly is enough). */
    public boolean hasEnoughUpside() {
        Double upsidePercent = upsidePercent();
        return upsidePercent != null && upsidePercent >= LEAST_UPSIDE_PERCENT - ROUNDING_TOLERANCE;
    }
}
