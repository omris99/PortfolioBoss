package portfolioboss.ib;

import java.util.Collections;
import java.util.Map;

/**
 * A stock the scans found, once whichever scans found it: its row and its contract details together.
 *
 * @param rankByScanCode where each scan that found it placed it (0 is the top), e.g.
 *                       {@code {SCAN_histPerfRatio3m_DESC: 4, SCAN_histPerfRatio6m_DESC: 17}}
 */
public record ScanCandidate(int conId, String symbol, String companyName, String exchange, String industry,
                            String category, Map<String, Integer> rankByScanCode) {

    /** The best place any scan gave it. */
    public int bestRank() {
        return Collections.min(rankByScanCode.values());
    }
}
