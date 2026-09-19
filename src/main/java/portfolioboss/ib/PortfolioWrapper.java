package portfolioboss.ib;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.DefaultEWrapper;
import com.ib.client.EClientSocket;
import portfolioboss.model.Holding;
import portfolioboss.model.PortfolioSnapshot;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Read-only IB callback handler for Milestone 0.
 *
 * <p>Extends {@link DefaultEWrapper} (empty implementations of every callback) and overrides only
 * the handful of methods needed to read the account's portfolio. It requests one snapshot of the
 * holdings, prints them, then unsubscribes — it never places, modifies, or cancels an order.
 */
public class PortfolioWrapper extends DefaultEWrapper {

    /** IB status/notice codes that are informational, not real errors. */
    private static final Set<Integer> INFO_CODES = Set.of(
            2104, 2106, 2107, 2108, 2119, 2158, 2100, 2103, 2105, 2110, 2137, 2168, 2169);

    private EClientSocket client;
    private String account = "";
    private double netLiquidation = Double.NaN;
    private double totalCashValue = Double.NaN;

    private final Map<String, Holding> holdings = new LinkedHashMap<>();
    private final CountDownLatch portfolioDownloaded = new CountDownLatch(1);
    private volatile boolean connectionFailed = false;
    private volatile PortfolioSnapshot snapshot;

    void setClient(EClientSocket client) {
        this.client = client;
    }

    /** The finished download, or {@code null} until {@code accountDownloadEnd} has fired. */
    PortfolioSnapshot snapshot() {
        return snapshot;
    }

    /** Blocks until the initial portfolio download finishes or the timeout elapses. */
    boolean awaitDownload(long timeout, TimeUnit unit) throws InterruptedException {
        return portfolioDownloaded.await(timeout, unit);
    }

    boolean connectionFailed() {
        return connectionFailed;
    }

    // ── connection lifecycle ────────────────────────────────────────────────────────────────────

    @Override
    public void nextValidId(int orderId) {
        // Connection handshake is complete once this arrives.
        System.out.println("[ib] connection ready");
    }

    @Override
    public void managedAccounts(String accountsList) {
        // Delivered right after connect; the first token is the account we read.
        String primaryAccount = accountsList == null ? "" : accountsList.split(",")[0].trim();
        this.account = primaryAccount;
        System.out.println("[ib] account: " + (primaryAccount.isEmpty() ? "(default)" : primaryAccount));
        // subscribe=true triggers a full portfolio download, then live updates every ~3 min.
        client.reqAccountUpdates(true, primaryAccount);
    }

    // ── portfolio download ──────────────────────────────────────────────────────────────────────

    @Override
    public void updatePortfolio(Contract contract, Decimal position, double marketPrice,
                                double marketValue, double averageCost, double unrealizedPNL,
                                double realizedPNL, String accountName) {
        double quantity = position.value().doubleValue();
        if (quantity == 0.0) {
            holdings.remove(contract.symbol());   // a closed-out position
            return;
        }
        holdings.put(contract.symbol(), new Holding(
                contract.symbol(),
                String.valueOf(contract.secType()),
                contract.currency(),
                quantity, averageCost, marketPrice, marketValue, unrealizedPNL, realizedPNL, accountName));
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        if ("NetLiquidation".equals(key)) {
            netLiquidation = parseOrNaN(value);
        } else if ("TotalCashValue".equals(key)) {
            totalCashValue = parseOrNaN(value);
        }
    }

    @Override
    public void accountDownloadEnd(String accountName) {
        snapshot = new PortfolioSnapshot(
                account, Instant.now(), netLiquidation, totalCashValue, List.copyOf(holdings.values()));
        printReport();
        if (client != null && client.isConnected()) {
            client.reqAccountUpdates(false, account);   // stop the subscription; we only wanted a snapshot
        }
        portfolioDownloaded.countDown();
    }

    // ── errors ──────────────────────────────────────────────────────────────────────────────────

    @Override
    public void error(int requestId, long errorTime, int errorCode, String errorMsg, String advancedOrderRejectJson) {
        if (INFO_CODES.contains(errorCode)) {
            System.out.println("[ib] " + errorMsg);
            return;
        }
        System.err.println("[ib error] code=" + errorCode + " id=" + requestId + " — " + errorMsg);
        if (errorCode == 502 || errorCode == 504) {   // couldn't connect / not connected
            connectionFailed = true;
            portfolioDownloaded.countDown();
        }
    }

    @Override
    public void error(Exception e) {
        System.err.println("[ib error] " + e.getMessage());
    }

    @Override
    public void error(String message) {
        System.err.println("[ib error] " + message);
    }

    // ── report ──────────────────────────────────────────────────────────────────────────────────

    private void printReport() {
        System.out.println();
        System.out.println("════════════════════════════════════════════════════════════════════════════════════");
        System.out.println(" PORTFOLIO" + (account.isEmpty() ? "" : "  ·  account " + account));
        System.out.println("════════════════════════════════════════════════════════════════════════════════════");

        if (holdings.isEmpty()) {
            System.out.println(" (no open positions reported)");
        } else {
            System.out.printf("%-8s %12s %12s %12s %14s %14s %8s%n",
                    "SYMBOL", "QTY", "AVG COST", "LAST", "MKT VALUE", "UNREAL P&L", "%");
            System.out.println("────────────────────────────────────────────────────────────────────────────────────");

            double totalValue = 0, totalPnl = 0;
            var largestFirst = holdings.values().stream()
                    .sorted((a, b) -> Double.compare(b.marketValue(), a.marketValue()))
                    .toList();
            for (Holding holding : largestFirst) {
                System.out.printf("%-8s %12s %12s %12s %14s %14s %7.1f%%%n",
                        holding.symbol(),
                        formatQuantity(holding.position()),
                        formatMoney(holding.averageCost()),
                        formatMoney(holding.marketPrice()),
                        formatMoney(holding.marketValue()),
                        formatSignedMoney(holding.unrealizedPnl()),
                        holding.unrealizedPnlPercent());
                totalValue += holding.marketValue();
                totalPnl += holding.unrealizedPnl();
            }
            System.out.println("────────────────────────────────────────────────────────────────────────────────────");
            System.out.printf("%-8s %12s %12s %12s %14s %14s%n",
                    "TOTAL", "", "", "", formatMoney(totalValue), formatSignedMoney(totalPnl));
        }

        System.out.println();
        if (!Double.isNaN(netLiquidation)) {
            System.out.printf(" Net liquidation : %s%n", formatMoney(netLiquidation));
        }
        if (!Double.isNaN(totalCashValue)) {
            System.out.printf(" Cash            : %s%n", formatMoney(totalCashValue));
        }
        System.out.println("════════════════════════════════════════════════════════════════════════════════════");
        System.out.println();
    }

    private static double parseOrNaN(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private static String formatMoney(double amount) {
        return String.format("%,.2f", amount);
    }

    private static String formatSignedMoney(double amount) {
        return (amount >= 0 ? "+" : "-") + String.format("%,.2f", Math.abs(amount));
    }

    /** Whole share counts print without decimals; fractional shares keep 4 places. */
    private static String formatQuantity(double quantity) {
        return quantity == Math.floor(quantity)
                ? String.format("%.0f", quantity)
                : String.format("%.4f", quantity);
    }
}
