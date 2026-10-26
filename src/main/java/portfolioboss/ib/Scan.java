package portfolioboss.ib;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * One run of the hot-stock scans (RECOMMENDATIONS_TODO.md, decisions 2 and 3): what IB sent — the rows of every scan
 * that answered, and the contract details of the stocks in them. The candidates are derived from those: one per stock,
 * whichever scans found it, and only on NYSE or NASDAQ — a stock whose contract details did not come is left out, its
 * exchange unknown.
 *
 * @param scannedAt           when the scans answered
 * @param scanRows            every row of every scan that answered
 * @param stockDetailsByConId the contract details that came, by contract id
 */
public record Scan(Instant scannedAt, List<ScanRow> scanRows, Map<Integer, StockDetails> stockDetailsByConId) {

    /** The scans that answered, in the order of their rows. */
    public List<String> scanCodes() {
        return List.copyOf(scanRows.stream()
                .map(ScanRow::scanCode)
                .collect(Collectors.toCollection(LinkedHashSet::new)));
    }

    /** One per stock on an accepted exchange, the best placed first and then by symbol. */
    public List<ScanCandidate> candidates() {
        return scanRowsByStock().values().stream()
                .filter(scanRowsOfOneStock -> isOnAnAcceptedExchange(scanRowsOfOneStock.getFirst().conId()))
                .map(this::candidateFromScanRowsAndStockDetails)
                .sorted(Comparator.comparingInt(ScanCandidate::bestRank).thenComparing(ScanCandidate::symbol))
                .toList();
    }

    /**
     * The stocks left out, for the console: "XYZ (ARCA)" with the exchange IB sent, or "XYZ (no contract details)". The
     * exchange check had not been run against TWS before, so a stock left out by mistake should show at once.
     */
    public List<String> leftOutStocks() {
        return scanRowsByStock().values().stream()
                .map(List::getFirst)
                .filter(scanRow -> !isOnAnAcceptedExchange(scanRow.conId()))
                .map(this::symbolWithExchange)
                .toList();
    }

    /** By contract id, a stock's rows: two when both scans found it. In the order the stocks first appear. */
    private Map<Integer, List<ScanRow>> scanRowsByStock() {
        return scanRows.stream()
                .collect(Collectors.groupingBy(ScanRow::conId, LinkedHashMap::new, Collectors.toList()));
    }

    private boolean isOnAnAcceptedExchange(int conId) {
        StockDetails stockDetails = stockDetailsByConId.get(conId);
        return stockDetails != null && HotStockScan.ACCEPTED_EXCHANGES.contains(stockDetails.exchange());
    }

    /**
     * Makes one candidate of one stock's rows: its symbol from the rows, its name, exchange and sector from its contract
     * details, and where each scan placed it.
     */
    private ScanCandidate candidateFromScanRowsAndStockDetails(List<ScanRow> scanRowsOfOneStock) {
        ScanRow firstScanRow = scanRowsOfOneStock.getFirst();
        StockDetails stockDetails = stockDetailsByConId.get(firstScanRow.conId());
        Map<String, Integer> rankByScanCode = new LinkedHashMap<>();
        scanRowsOfOneStock.forEach(scanRow -> rankByScanCode.put(scanRow.scanCode(), scanRow.rank()));
        return new ScanCandidate(firstScanRow.conId(), firstScanRow.symbol(), stockDetails.companyName(),
                stockDetails.exchange(), stockDetails.industry(), stockDetails.category(), rankByScanCode);
    }

    private String symbolWithExchange(ScanRow scanRow) {
        StockDetails stockDetails = stockDetailsByConId.get(scanRow.conId());
        String exchange = stockDetails == null ? "no contract details" : stockDetails.exchange();
        return scanRow.symbol() + " (" + exchange + ")";
    }
}
