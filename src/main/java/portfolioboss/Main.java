package portfolioboss;

import portfolioboss.ib.IbGateway;

import java.util.concurrent.TimeUnit;

/**
 * PortfolioBoss — Milestone 0 walking skeleton.
 *
 * <p>Connects read-only to a running IB TWS/Gateway, reads the account's real holdings (quantity
 * and average cost come straight from the broker), prints them, and exits. This proves the one IB
 * call the long-term portfolio platform is built on: {@code reqAccountUpdates}. It never trades.
 *
 * <pre>
 *   ./run.sh                 # 127.0.0.1:7496, clientId 101
 *   ./run.sh 7497            # paper-trading port
 *   ./run.sh 7496 102        # custom clientId
 * </pre>
 */
public final class Main {

    private static final String HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 7496;      // live TWS; use 7497 for paper
    private static final int DEFAULT_CLIENT_ID = 101;  // must differ from the trading bot (id 0)
    private static final int TIMEOUT_SECONDS = 15;

    public static void main(String[] args) throws InterruptedException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : DEFAULT_PORT;
        int clientId = args.length > 1 ? Integer.parseInt(args[1]) : DEFAULT_CLIENT_ID;

        System.out.println("PortfolioBoss · read-only portfolio reader (Milestone 0)");

        IbGateway gateway = new IbGateway();
        try {
            gateway.connect(HOST, port, clientId);
            boolean portfolioReceived = gateway.awaitPortfolio(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (gateway.connectionFailed()) {
                System.err.println("Connection was refused by TWS. Check the port and that API access is enabled.");
            } else if (!portfolioReceived) {
                System.err.println("Timed out after " + TIMEOUT_SECONDS + "s waiting for portfolio data. " +
                        "Is TWS running and logged in on port " + port + "?");
            }
        } catch (IllegalStateException e) {
            System.err.println(e.getMessage());
        } finally {
            gateway.disconnect();
        }

        // The reader loop runs on a daemon thread; nothing keeps the JVM alive, so it exits here.
        System.exit(0);
    }

    private Main() {
    }
}
