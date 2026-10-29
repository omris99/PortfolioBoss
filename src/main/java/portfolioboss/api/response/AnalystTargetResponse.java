package portfolioboss.api.response;

import portfolioboss.calculation.AnalystTargetCalculator;
import portfolioboss.ib.AnalystPriceTarget;

import java.util.List;

/**
 * The analysts' target from a stock's Dow Jones headlines (RECOMMENDATIONS_TODO.md, decision 14), worked out on this
 * read. The component names are JSON keys: added to, never renamed or removed.
 *
 * @param medianTarget           the median of the firms' latest targets; {@code null} under three firms
 * @param upsidePercent          how far it is above the last close, in percent — negative below it; {@code null}
 *                               without a target
 * @param hasEnoughUpside        at least 15% above the last close: the stock can be on the short list
 * @param firmCount              how many firms gave a target in the half year before the scan
 * @param latestTargetOfEachFirm each firm's latest target of that half year, the newest first, as it was read
 */
public record AnalystTargetResponse(
        Double medianTarget,
        Double upsidePercent,
        boolean hasEnoughUpside,
        int firmCount,
        List<AnalystPriceTarget> latestTargetOfEachFirm) {

    public AnalystTargetResponse(AnalystTargetCalculator analystTarget) {
        this(analystTarget.medianTarget(), analystTarget.upsidePercent(), analystTarget.hasEnoughUpside(),
                analystTarget.firmCount(), analystTarget.latestTargetOfEachFirm());
    }
}
