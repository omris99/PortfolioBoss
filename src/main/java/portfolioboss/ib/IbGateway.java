package portfolioboss.ib;

import com.ib.client.Contract;
import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;
import portfolioboss.calculation.DailyClose;
import portfolioboss.calculation.PortfolioSnapshot;

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

    /** The daily-close requests are numbered from here; nothing else in PortfolioBoss uses request ids. */
    private static final int FIRST_DAILY_CLOSE_REQUEST_ID = 1000;

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

    /**
     * Asks IB for a year of daily closing prices of each contract, by contract id — a read, like the portfolio. One
     * request per contract; the bars arrive on the reader thread, and {@link #awaitDailyCloses} waits for them.
     */
    public void requestDailyCloses(Collection<Integer> conIds) {
        Map<Integer, Integer> conIdByRequestId = new LinkedHashMap<>();
        int requestId = FIRST_DAILY_CLOSE_REQUEST_ID;
        for (int conId : conIds) {
            conIdByRequestId.put(requestId++, conId);
        }
        wrapper.expectDailyCloses(conIdByRequestId);
        System.out.printf("[ib] requesting a year of daily closes for %d contracts%n", conIds.size());
        conIdByRequestId.forEach((dailyCloseRequestId, conId) -> client.reqHistoricalData(
                dailyCloseRequestId, contractOf(conId), "", "1 Y", "1 day", "TRADES",
                1,       // regular trading hours only
                1,       // dates as yyyyMMdd
                false,   // one answer, not a live subscription
                null));
    }

    /** Blocks until every daily-close request has ended, or the timeout elapses. */
    public boolean awaitDailyCloses(long timeout, TimeUnit unit) throws InterruptedException {
        return wrapper.awaitDailyCloses(timeout, unit);
    }

    /** By contract id, the closes of the requests that have ended with at least one. */
    public Map<Integer, List<DailyClose>> dailyCloses() {
        return wrapper.dailyCloses();
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
