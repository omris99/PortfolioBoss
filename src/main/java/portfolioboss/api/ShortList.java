package portfolioboss.api;

import java.util.List;

/**
 * The stocks the Recommendations page's full run analyzes (RECOMMENDATIONS_TODO.md, decision 14): the first 10 of the
 * latest scan's HOT list, in the page's order, whose analysts' target is at least 15% above the last close.
 *
 * @param scanId                the latest scan, which the recommendation is stored against; {@code null} before the
 *                              first scan
 * @param hotStockCount         how many stocks the HOT list has
 * @param withEnoughUpsideCount how many of them have enough upside — more than the short list when over 10 do
 * @param candidates            the short list itself: at most 10, empty when none has enough upside
 */
public record ShortList(Long scanId, int hotStockCount, int withEnoughUpsideCount,
                        List<CandidateWithItsList> candidates) {
}
