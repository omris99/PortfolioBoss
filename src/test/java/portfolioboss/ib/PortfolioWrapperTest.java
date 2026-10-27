package portfolioboss.ib;

import com.ib.client.Bar;
import com.ib.client.Contract;
import com.ib.client.ContractDetails;
import com.ib.client.Decimal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feeds {@link PortfolioWrapper} the callbacks IB would send, so the way it collects positions, scans, contract details
 * and daily closes is tested without a TWS. No socket is needed: the wrapper only uses its client to unsubscribe and to
 * cancel a scan when one is set.
 */
class PortfolioWrapperTest {

    private static final String ACCOUNT = "U1234567";
    private static final int APPLE_CON_ID = 265598;
    private static final int MICROSOFT_CON_ID = 272093;
    private static final int SPY_CON_ID = 756733;
    private static final String THREE_MONTH_SCAN = "SCAN_histPerfRatio3m_DESC";
    private static final String SIX_MONTH_SCAN = "SCAN_histPerfRatio6m_DESC";

    // A batch numbers its requests in the order expected, from its first request id.
    private static final int THREE_MONTH_SCAN_REQUEST_ID = PortfolioWrapper.FIRST_SCAN_REQUEST_ID;
    private static final int SIX_MONTH_SCAN_REQUEST_ID = PortfolioWrapper.FIRST_SCAN_REQUEST_ID + 1;
    private static final int APPLE_DETAILS_REQUEST_ID = PortfolioWrapper.FIRST_CONTRACT_DETAILS_REQUEST_ID;
    private static final int APPLE_CLOSES_REQUEST_ID = PortfolioWrapper.FIRST_DAILY_CLOSE_REQUEST_ID;
    private static final int SPY_CLOSES_REQUEST_ID = PortfolioWrapper.FIRST_DAILY_CLOSE_REQUEST_ID + 1;

    private final PortfolioWrapper wrapper = new PortfolioWrapper();

    @Test
    void keepsTwoPositionsThatShareASymbolApartByContractId() {
        reportPosition("BRK", 1001, 10);
        reportPosition("BRK", 1002, 5);
        wrapper.accountDownloadEnd(ACCOUNT);

        assertThat(wrapper.snapshot().holdings())
                .extracting(Holding::conId)
                .containsExactly(1001, 1002);
    }

    @Test
    void dropsAPositionIbReportsAsZero() {
        reportPosition("AAPL", 265598, 10);
        reportPosition("AAPL", 265598, 0);
        wrapper.accountDownloadEnd(ACCOUNT);

        assertThat(wrapper.snapshot().holdings()).isEmpty();
    }

    @Test
    void hasNoSnapshotUntilTheDownloadEnds() {
        reportPosition("AAPL", 265598, 10);

        assertThat(wrapper.snapshot()).isNull();
    }

    // ── the scans ───────────────────────────────────────────────────────────────────────────────

    /** A scan keeps sending updates after its first answer ends; only the first answer is kept. */
    @Test
    void collectsEachScansRowsUntilItsFirstAnswerEnds() throws InterruptedException {
        wrapper.scanRequests().expect(List.of(THREE_MONTH_SCAN, SIX_MONTH_SCAN));

        wrapper.scannerData(THREE_MONTH_SCAN_REQUEST_ID, 0, scanned("AAPL", APPLE_CON_ID), "", "", "", "");
        wrapper.scannerData(THREE_MONTH_SCAN_REQUEST_ID, 1, scanned("MSFT", MICROSOFT_CON_ID), "", "", "", "");
        wrapper.scannerDataEnd(THREE_MONTH_SCAN_REQUEST_ID);
        wrapper.scannerData(THREE_MONTH_SCAN_REQUEST_ID, 0, scanned("MSFT", MICROSOFT_CON_ID), "", "", "", "");
        wrapper.scannerData(SIX_MONTH_SCAN_REQUEST_ID, 0, scanned("MSFT", MICROSOFT_CON_ID), "", "", "", "");
        wrapper.scannerDataEnd(SIX_MONTH_SCAN_REQUEST_ID);

        assertThat(wrapper.scanRequests().awaitAllFinished(0, TimeUnit.SECONDS)).isTrue();
        assertThat(wrapper.scanRequests().answersOfFinishedRequests())
                .containsOnlyKeys(THREE_MONTH_SCAN, SIX_MONTH_SCAN)
                .containsEntry(THREE_MONTH_SCAN, List.of(new ScanRow(THREE_MONTH_SCAN, 0, APPLE_CON_ID, "AAPL"),
                        new ScanRow(THREE_MONTH_SCAN, 1, MICROSOFT_CON_ID, "MSFT")))
                .containsEntry(SIX_MONTH_SCAN, List.of(new ScanRow(SIX_MONTH_SCAN, 0, MICROSOFT_CON_ID, "MSFT")));
    }

    @Test
    void anErrorEndsThatScanWithoutRows() throws InterruptedException {
        wrapper.scanRequests().expect(List.of(THREE_MONTH_SCAN, SIX_MONTH_SCAN));

        wrapper.scannerData(THREE_MONTH_SCAN_REQUEST_ID, 0, scanned("AAPL", APPLE_CON_ID), "", "", "", "");
        wrapper.scannerDataEnd(THREE_MONTH_SCAN_REQUEST_ID);
        wrapper.error(SIX_MONTH_SCAN_REQUEST_ID, 0L, 162, "Historical Market Data Service error message: no scanner", "");

        assertThat(wrapper.scanRequests().awaitAllFinished(0, TimeUnit.SECONDS)).isTrue();
        assertThat(wrapper.scanRequests().answersOfFinishedRequests()).containsOnlyKeys(THREE_MONTH_SCAN);
    }

    /** Code 165 comes at the start of a scan; in session 0, taken for an error, it ended the scan before its rows. */
    @Test
    void codeOneSixtyFiveDoesNotEndTheScan() throws InterruptedException {
        wrapper.scanRequests().expect(List.of(THREE_MONTH_SCAN));

        wrapper.error(THREE_MONTH_SCAN_REQUEST_ID, 0L, 165,
                "Historical Market Data Service query message:50 out of 990 items retrieved", "");

        assertThat(wrapper.scanRequests().awaitAllFinished(0, TimeUnit.SECONDS)).isFalse();
        wrapper.scannerData(THREE_MONTH_SCAN_REQUEST_ID, 0, scanned("AAPL", APPLE_CON_ID), "", "", "", "");
        wrapper.scannerDataEnd(THREE_MONTH_SCAN_REQUEST_ID);
        assertThat(wrapper.scanRequests().answersOfFinishedRequests().get(THREE_MONTH_SCAN)).hasSize(1);
    }

    /** IB confirms a cancelled scan with an error; by then the scan has ended, and its rows stay. */
    @Test
    void anErrorAfterTheScanEndedKeepsItsRows() {
        wrapper.scanRequests().expect(List.of(THREE_MONTH_SCAN));

        wrapper.scannerData(THREE_MONTH_SCAN_REQUEST_ID, 0, scanned("AAPL", APPLE_CON_ID), "", "", "", "");
        wrapper.scannerDataEnd(THREE_MONTH_SCAN_REQUEST_ID);
        wrapper.error(THREE_MONTH_SCAN_REQUEST_ID, 0L, 162,
                "Historical Market Data Service error message:API scanner subscription cancelled: 100", "");

        assertThat(wrapper.scanRequests().answersOfFinishedRequests().get(THREE_MONTH_SCAN))
                .containsExactly(new ScanRow(THREE_MONTH_SCAN, 0, APPLE_CON_ID, "AAPL"));
    }

    // ── contract details ────────────────────────────────────────────────────────────────────────

    @Test
    void collectsTheContractDetailsOfEachStock() throws InterruptedException {
        wrapper.contractDetailsRequests().expect(List.of(APPLE_CON_ID));

        wrapper.contractDetails(APPLE_DETAILS_REQUEST_ID, contractDetails(APPLE_CON_ID, "NASDAQ", "APPLE INC",
                "Technology", "Computers"));
        wrapper.contractDetailsEnd(APPLE_DETAILS_REQUEST_ID);

        assertThat(wrapper.contractDetailsRequests().awaitAllFinished(0, TimeUnit.SECONDS)).isTrue();
        assertThat(wrapper.contractDetailsRequests().answersOfFinishedRequests()).isEqualTo(Map.of(APPLE_CON_ID,
                List.of(new StockContractDetails(APPLE_CON_ID, "NASDAQ", "APPLE INC", "Technology", "Computers"))));
    }

    // ── daily closes ────────────────────────────────────────────────────────────────────────────

    @Test
    void collectsTheDailyClosesOfEachRequestUnderItsContractId() throws InterruptedException {
        wrapper.dailyCloseRequests().expect(List.of(APPLE_CON_ID, SPY_CON_ID));

        wrapper.historicalData(APPLE_CLOSES_REQUEST_ID, bar("20261005", 333.5));
        wrapper.historicalData(APPLE_CLOSES_REQUEST_ID, bar("20261006", 335.0));
        wrapper.historicalDataEnd(APPLE_CLOSES_REQUEST_ID, "20251006", "20261006");
        wrapper.historicalData(SPY_CLOSES_REQUEST_ID, bar("20261006", 671.2));
        wrapper.historicalDataEnd(SPY_CLOSES_REQUEST_ID, "20251006", "20261006");

        assertThat(wrapper.dailyCloseRequests().awaitAllFinished(0, TimeUnit.SECONDS)).isTrue();
        assertThat(wrapper.dailyCloseRequests().answersOfFinishedRequests())
                .containsOnlyKeys(APPLE_CON_ID, SPY_CON_ID)
                .containsEntry(APPLE_CON_ID, List.of(new DailyClose(LocalDate.of(2026, 10, 5), 333.5),
                        new DailyClose(LocalDate.of(2026, 10, 6), 335.0)))
                .containsEntry(SPY_CON_ID, List.of(new DailyClose(LocalDate.of(2026, 10, 6), 671.2)));
    }

    /** E.g. no market data permission for one contract: that request ends without closes, the others carry on. */
    @Test
    void anErrorEndsThatContractsRequestWithoutCloses() throws InterruptedException {
        wrapper.dailyCloseRequests().expect(List.of(APPLE_CON_ID, SPY_CON_ID));

        wrapper.historicalData(APPLE_CLOSES_REQUEST_ID, bar("20261006", 335.0));
        wrapper.historicalDataEnd(APPLE_CLOSES_REQUEST_ID, "20251006", "20261006");
        wrapper.error(SPY_CLOSES_REQUEST_ID, 0L, 162,
                "Historical Market Data Service error message: no market data permissions", "");

        assertThat(wrapper.dailyCloseRequests().awaitAllFinished(0, TimeUnit.SECONDS)).isTrue();
        assertThat(wrapper.dailyCloseRequests().answersOfFinishedRequests()).containsOnlyKeys(APPLE_CON_ID);
    }

    /** What a timeout sees: a request that has not ended is left out, not served half-received. */
    @Test
    void leavesOutARequestThatHasNotEnded() throws InterruptedException {
        wrapper.dailyCloseRequests().expect(List.of(APPLE_CON_ID, SPY_CON_ID));

        wrapper.historicalData(APPLE_CLOSES_REQUEST_ID, bar("20261006", 335.0));
        wrapper.historicalDataEnd(APPLE_CLOSES_REQUEST_ID, "20251006", "20261006");
        wrapper.historicalData(SPY_CLOSES_REQUEST_ID, bar("20261005", 670.0));   // SPY's closes are still coming in

        assertThat(wrapper.dailyCloseRequests().awaitAllFinished(0, TimeUnit.SECONDS)).isFalse();
        assertThat(wrapper.dailyCloseRequests().answersOfFinishedRequests()).containsOnlyKeys(APPLE_CON_ID);
    }

    @Test
    void skipsABarWhoseDateCannotBeRead() {
        wrapper.dailyCloseRequests().expect(List.of(APPLE_CON_ID));

        wrapper.historicalData(APPLE_CLOSES_REQUEST_ID, bar("not a date", 1.0));
        wrapper.historicalData(APPLE_CLOSES_REQUEST_ID, bar("20261006 16:00:00 US/Eastern", 335.0));
        wrapper.historicalDataEnd(APPLE_CLOSES_REQUEST_ID, "20251006", "20261006");

        assertThat(wrapper.dailyCloseRequests().answersOfFinishedRequests().get(APPLE_CON_ID))
                .containsExactly(new DailyClose(LocalDate.of(2026, 10, 6), 335.0));
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private Bar bar(String time, double close) {
        return new Bar(time, close, close, close, close, Decimal.get(0), 0, Decimal.get(0));
    }

    /** What a scan's row carries: little more than the contract id and the symbol. */
    private ContractDetails scanned(String symbol, int conId) {
        ContractDetails scannedStock = new ContractDetails();
        scannedStock.contract().symbol(symbol);
        scannedStock.contract().conid(conId);
        return scannedStock;
    }

    private ContractDetails contractDetails(int conId, String primaryExchange, String companyName, String industry,
                                            String category) {
        ContractDetails contractDetails = new ContractDetails();
        contractDetails.contract().conid(conId);
        contractDetails.contract().primaryExch(primaryExchange);
        contractDetails.longName(companyName);
        contractDetails.industry(industry);
        contractDetails.category(category);
        return contractDetails;
    }

    private void reportPosition(String symbol, int conId, double quantity) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.conid(conId);
        contract.secType("STK");
        contract.currency("USD");
        wrapper.updatePortfolio(contract, Decimal.get(quantity), 200.0, quantity * 200.0, 150.0, 0.0, 0.0, ACCOUNT);
    }
}
