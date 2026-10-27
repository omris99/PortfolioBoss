package portfolioboss.ib;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import com.ib.client.ScannerSubscription;
import com.ib.client.TagValue;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Owns the Interactive Brokers socket connection and message-reader loop.
 *
 * <p>This is the standard IB socket-client bootstrap (mirrors the pattern used elsewhere in the
 * IBBot codebase): a signal, an {@link EClientSocket}, and a background thread that pumps
 * {@link EReader#processMsgs()} whenever the signal fires. The gateway is strictly read-only — it
 * exposes no order-placement method.
 */
public class IbGateway {

    private final EJavaSignal signal = new EJavaSignal();
    private final PortfolioWrapper wrapper = new PortfolioWrapper();
    private final EClientSocket client = new EClientSocket(wrapper, signal);

    public IbGateway() {
        wrapper.setClient(client);
    }

    /**
     * Connects to TWS/Gateway and starts the reader loop.
     *
     * @param clientId must differ from every other API client on the same TWS (the trading bot
     *                 uses id 0, so this defaults to something else) — otherwise TWS rejects it.
     */
    public void connect(String host, int port, int clientId) {
        System.out.printf("[ib] connecting to %s:%d (clientId=%d, read-only)%n", host, port, clientId);
        client.eConnect(host, port, clientId);
        if (!client.isConnected()) {
            throw new IllegalStateException(
                    "Could not open a socket to TWS at " + host + ":" + port +
                    ". Is TWS/Gateway running with 'Enable ActiveX and Socket Clients' turned on?");
        }
        startReaderLoop();
    }

    private void startReaderLoop() {
        final EReader reader = new EReader(client, signal);
        reader.start();
        Thread readerThread = new Thread(() -> {
            while (client.isConnected()) {
                signal.waitForSignal();
                try {
                    reader.processMsgs();
                } catch (Exception e) {
                    System.err.println("[ib] reader error: " + e.getMessage());
                }
            }
        }, "ib-reader");
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /** Blocks until the portfolio snapshot has been printed, or the timeout elapses. */
    public boolean awaitPortfolio(long timeout, TimeUnit unit) throws InterruptedException {
        return wrapper.awaitDownload(timeout, unit);
    }

    public boolean connectionFailed() {
        return wrapper.connectionFailed();
    }

    /** The downloaded portfolio, or {@code null} if {@link #awaitPortfolio} did not succeed. */
    public PortfolioSnapshot snapshot() {
        return wrapper.snapshot();
    }

    // ── the scans for hot stocks ────────────────────────────────────────────────────────────────

    /**
     * Asks IB for the stocks that rose most over the last 3 and 6 months, with the filters of {@link HotStockScan}. A
     * scan answers with up to 50 rows and would then keep updating them; the wrapper cancels each one once its first
     * answer has ended — one picture, not a subscription, a read like the portfolio.
     */
    public void requestHotStockScans() {
        Map<Integer, String> scanCodeByRequestId = wrapper.scanRequests().expect(HotStockScan.SCAN_CODES);
        System.out.println("[ib] scanning for hot stocks: " + String.join(", ", HotStockScan.SCAN_CODES));
        scanCodeByRequestId.forEach((requestId, scanCode) -> client.reqScannerSubscription(
                requestId, scannerSubscriptionOf(scanCode), null, hotStockScanFilters()));
    }

    /** Blocks until every scan has ended, or the timeout elapses. */
    public boolean awaitScans(long timeout, TimeUnit unit) throws InterruptedException {
        return wrapper.scanRequests().awaitAllFinished(timeout, unit);
    }

    /** Every row of every scan that has ended with at least one. */
    public List<ScanRow> scanRows() {
        return wrapper.scanRequests().answersOfFinishedRequests().values().stream()
                .flatMap(List::stream)
                .toList();
    }

    private ScannerSubscription scannerSubscriptionOf(String scanCode) {
        ScannerSubscription subscription = new ScannerSubscription();
        subscription.instrument(HotStockScan.INSTRUMENT);
        subscription.locationCode(HotStockScan.LOCATION);
        subscription.stockTypeFilter(HotStockScan.STOCK_TYPE);
        subscription.scanCode(scanCode);
        subscription.numberOfRows(HotStockScan.MOST_ROWS_PER_SCAN);
        return subscription;
    }

    private List<TagValue> hotStockScanFilters() {
        return List.of(
                new TagValue("priceAbove", String.valueOf(HotStockScan.LOWEST_PRICE_DOLLARS)),
                new TagValue("marketCapAbove1e6", String.valueOf(HotStockScan.LOWEST_MARKET_CAP_MILLIONS)),
                new TagValue("avgVolumeAbove", String.valueOf(HotStockScan.LOWEST_AVERAGE_DAILY_VOLUME)));
    }

    // ── contract details ────────────────────────────────────────────────────────────────────────

    /** Asks IB for the contract details of each stock — its exchange, name and sector —, one request per contract. */
    public void requestContractDetails(Collection<Integer> conIds) {
        Map<Integer, Integer> conIdByRequestId = wrapper.contractDetailsRequests().expect(conIds);
        System.out.printf("[ib] requesting the contract details of %d stocks%n", conIds.size());
        conIdByRequestId.forEach((requestId, conId) -> client.reqContractDetails(requestId, contractOf(conId)));
    }

    /** Blocks until every contract-details request has ended, or the timeout elapses. */
    public boolean awaitContractDetails(long timeout, TimeUnit unit) throws InterruptedException {
        return wrapper.contractDetailsRequests().awaitAllFinished(timeout, unit);
    }

    /** By contract id, the contract details that came; asked by contract id, IB answers with that one contract. */
    public Map<Integer, StockContractDetails> contractDetails() {
        Map<Integer, StockContractDetails> contractDetailsByConId = new LinkedHashMap<>();
        wrapper.contractDetailsRequests().answersOfFinishedRequests()
                .forEach((conId, answers) -> contractDetailsByConId.put(conId, answers.getFirst()));
        return contractDetailsByConId;
    }

    // ── daily closes ────────────────────────────────────────────────────────────────────────────

    /**
     * Asks IB for a year of daily closing prices of each contract, by contract id — a read, like the portfolio. One
     * request per contract; the bars arrive on the reader thread, and {@link #awaitDailyCloses} waits for them.
     */
    public void requestDailyCloses(Collection<Integer> conIds) {
        Map<Integer, Integer> conIdByRequestId = wrapper.dailyCloseRequests().expect(conIds);
        System.out.printf("[ib] requesting a year of daily closes for %d contracts%n", conIds.size());
        conIdByRequestId.forEach((requestId, conId) -> client.reqHistoricalData(
                requestId, contractOf(conId), "", "1 Y", "1 day", "TRADES",
                1,       // regular trading hours only
                1,       // dates as yyyyMMdd
                false,   // one answer, not a live subscription
                null));
    }

    /** Blocks until every daily-close request has ended, or the timeout elapses. */
    public boolean awaitDailyCloses(long timeout, TimeUnit unit) throws InterruptedException {
        return wrapper.dailyCloseRequests().awaitAllFinished(timeout, unit);
    }

    /** By contract id, the closes of the requests that have ended with at least one. */
    public Map<Integer, List<DailyClose>> dailyCloses() {
        return wrapper.dailyCloseRequests().answersOfFinishedRequests();
    }

    /** A contract id and an exchange are all IB needs to know which contract is meant. */
    private Contract contractOf(int conId) {
        Contract contract = new Contract();
        contract.conid(conId);
        contract.exchange("SMART");
        return contract;
    }

    public void disconnect() {
        if (client.isConnected()) {
            client.eDisconnect();
            System.out.println("[ib] disconnected");
        }
    }
}
