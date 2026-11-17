package portfolioboss.ib;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

/** How a scan's rows and the contract details become candidates, on made-up stocks. */
class ScanTest {

    private static final Instant SCANNED_AT = Instant.parse("2026-10-26T14:00:00Z");
    private static final String THREE_MONTH_SCAN = "SCAN_histPerfRatio3m_DESC";
    private static final String SIX_MONTH_SCAN = "SCAN_histPerfRatio6m_DESC";

    /** AAA is 4th in one scan and 17th in the other; BBB 2nd in one: BBB is placed better, so it comes first. */
    @Test
    void aStockBothScansFoundIsOneCandidateWithItsPlaceInEach() {
        Scan scan = new Scan(SCANNED_AT,
                List.of(new ScanRow(THREE_MONTH_SCAN, 4, 1, "AAA"),
                        new ScanRow(SIX_MONTH_SCAN, 17, 1, "AAA"),
                        new ScanRow(SIX_MONTH_SCAN, 2, 2, "BBB")),
                Map.of(1, contractDetails(1, "NASDAQ"), 2, contractDetails(2, "NYSE")));

        List<ScanCandidate> candidates = scan.candidates();

        assertThat(candidates).extracting(ScanCandidate::symbol).containsExactly("BBB", "AAA");
        ScanCandidate stockInBothScans = candidates.get(1);
        assertThat(stockInBothScans.rankByScanCode()).containsExactly(entry(THREE_MONTH_SCAN, 4), entry(SIX_MONTH_SCAN, 17));
        assertThat(stockInBothScans.bestRank()).isEqualTo(4);
        assertThat(stockInBothScans.conId()).isEqualTo(1);
        assertThat(stockInBothScans.companyName()).isEqualTo("Company 1");
        assertThat(stockInBothScans.exchange()).isEqualTo("NASDAQ");
        assertThat(stockInBothScans.industry()).isEqualTo("Technology");
        assertThat(stockInBothScans.category()).isEqualTo("Software");
        assertThat(scan.scanCodes()).containsExactly(THREE_MONTH_SCAN, SIX_MONTH_SCAN);
    }

    /** A stock on another exchange, and one whose contract details did not come, are left out — and named. */
    @Test
    void onlyNyseAndNasdaqStocksAreKept() {
        Scan scan = new Scan(SCANNED_AT,
                List.of(new ScanRow(THREE_MONTH_SCAN, 0, 1, "AAA"),
                        new ScanRow(THREE_MONTH_SCAN, 1, 2, "BBB"),
                        new ScanRow(THREE_MONTH_SCAN, 2, 3, "CCC")),
                Map.of(1, contractDetails(1, "NYSE"), 2, contractDetails(2, "ARCA")));

        assertThat(scan.candidates()).extracting(ScanCandidate::symbol).containsExactly("AAA");
        assertThat(scan.leftOutStocks()).containsExactly("BBB (ARCA)", "CCC (no contract details)");
    }

    @Test
    void stocksPlacedEquallyGoBySymbol() {
        Scan scan = new Scan(SCANNED_AT,
                List.of(new ScanRow(THREE_MONTH_SCAN, 0, 1, "ZZZ"), new ScanRow(SIX_MONTH_SCAN, 0, 2, "MMM")),
                Map.of(1, contractDetails(1, "NYSE"), 2, contractDetails(2, "NYSE")));

        assertThat(scan.candidates()).extracting(ScanCandidate::symbol).containsExactly("MMM", "ZZZ");
    }

    private StockContractDetails contractDetails(int conId, String exchange) {
        return new StockContractDetails(conId, exchange, "Company " + conId, "Technology", "Software");
    }
}
