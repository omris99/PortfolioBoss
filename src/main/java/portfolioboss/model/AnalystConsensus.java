package portfolioboss.model;

import portfolioboss.ai.AnalystRating;

/**
 * The analysts' consensus the UI shows, chosen by {@code calculation.ConsensusCalculator} from the sources Claude
 * extracted (AI_ANALYSIS_TODO.md, decision 16): one source's figures, and how far the sources' targets spread — "Target
 * $328–$340 · 4 sources" says more than one figure that looks exact. Derived on every read, never stored; goes into the
 * JSON as it is, so the component names are JSON keys.
 *
 * @param rating              the chosen source's consensus on the one scale; {@code null} when it shows neither a
 *                            breakdown nor a label
 * @param analystCount        how many analysts the chosen source covers
 * @param averageTarget       the chosen source's average price target
 * @param targetUpsidePercent how far that target is above IB's market price, in percent — negative below it
 * @param sourceUrl           the chosen source
 * @param targetLow           the lowest average target among the sources ({@code targetHigh} the highest)
 * @param sourceCount         how many sources give an average target
 */
public record AnalystConsensus(
        AnalystRating rating,
        Integer analystCount,
        Double averageTarget,
        Double targetUpsidePercent,
        String sourceUrl,
        Double targetLow,
        Double targetHigh,
        int sourceCount) {
}
