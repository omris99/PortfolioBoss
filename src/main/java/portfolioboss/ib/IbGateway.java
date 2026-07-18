package portfolioboss.ib;

import com.ib.client.EClientSocket;
import com.ib.client.EJavaSignal;
import com.ib.client.EReader;

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

    public void disconnect() {
        if (client.isConnected()) {
            client.eDisconnect();
            System.out.println("[ib] disconnected");
        }
    }
}
