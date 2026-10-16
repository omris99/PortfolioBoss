package portfolioboss.ib;

import com.ib.client.Bar;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.DefaultEWrapper;
import com.ib.client.EClientSocket;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Read-only IB callback handler.
 *
 * <p>Extends {@link DefaultEWrapper} (empty implementations of every callback) and overrides only
 * the handful of methods needed to read the account's portfolio, and then the daily closes of its holdings and of
 * SPY for the momentum. It requests one snapshot of the holdings, prints them, then unsubscribes — it never places,
 * modifies, or cancels an order.
 */
public class PortfolioWrapper extends DefaultEWrapper {

    /** IB status/notice codes that are informational, not real errors. */
    private static final Set<Integer> INFO_CODES = Set.of(
            2104, 2106, 2107, 2108, 2119, 2158, 2100, 2103, 2105, 2110, 2137, 2168, 2169);
    /** {@code yyyyMMdd}: how IB dates a daily bar when asked with {@code formatDate=1}. */
    private static final DateTimeFormatter BAR_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private EClientSocket client;
    private String account = "";
    private double netLiquidation = Double.NaN;
    private double totalCashValue = Double.NaN;

    /** Keyed by IB's contract id: two positions can share a symbol, but never a conId. */
    private final Map<Integer, Holding> holdings = new LinkedHashMap<>();
    private final CountDownLatch portfolioDownloaded = new CountDownLatch(1);
    private volatile boolean connectionFailed = false;
    private volatile PortfolioSnapshot snapshot;

    // The daily closes are requested after the portfolio, one request per contract. They arrive on the reader thread
    // while the startup thread waits — and on a timeout reads what came so far — hence the concurrent collections.
    private final Map<Integer, Integer> conIdByDailyCloseRequest = new ConcurrentHashMap<>();
    private final Map<Integer, List<DailyClose>> dailyClosesByRequest = new ConcurrentHashMap<>();
    private final Set<Integer> finishedDailyCloseRequests = ConcurrentHashMap.newKeySet();
    private volatile CountDownLatch dailyClosesDownloaded = new CountDownLatch(0);

    protected void setClient(EClientSocket client) {
        this.client = client;
    }

    /** The finished download, or {@code null} until {@code accountDownloadEnd} has fired. */
    protected PortfolioSnapshot snapshot() {
        return snapshot;
    }

    /** Blocks until the initial portfolio download finishes or the timeout elapses. */
    protected boolean awaitDownload(long timeout, TimeUnit unit) throws InterruptedException {
        return portfolioDownloaded.await(timeout, unit);
    }

    protected boolean connectionFailed() {
        return connectionFailed;
    }

    /** Called before the daily-close requests go out: the contract each request id asks for. */
    protected void expectDailyCloses(Map<Integer, Integer> conIdByRequestId) {
        conIdByRequestId.forEach((requestId, conId) -> {
            conIdByDailyCloseRequest.put(requestId, conId);
            dailyClosesByRequest.put(requestId, Collections.synchronizedList(new ArrayList<>()));
        });
        dailyClosesDownloaded = new CountDownLatch(conIdByRequestId.size());
    }

    /** Blocks until every daily-close request has ended — with its bars or with an error — or the timeout elapses. */
    protected boolean awaitDailyCloses(long timeout, TimeUnit unit) throws InterruptedException {
        return dailyClosesDownloaded.await(timeout, unit);
    }

    /**
     * By contract id, the closes of every request that has ended with at least one; one still running when this is
     * called (a timeout) is left out rather than served half-received.
     */
    protected Map<Integer, List<DailyClose>> dailyCloses() {
        Map<Integer, List<DailyClose>> closesByConId = new LinkedHashMap<>();
        for (int requestId : finishedDailyCloseRequests) {
            List<DailyClose> closes = List.copyOf(dailyClosesByRequest.get(requestId));
            if (!closes.isEmpty()) {
                closesByConId.put(conIdByDailyCloseRequest.get(requestId), closes);
            }
        }
        return closesByConId;
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
            holdings.remove(contract.conid());   // a closed-out position
            return;
        }
        holdings.put(contract.conid(), new Holding(
                contract.symbol(),
                contract.conid(),
                String.valueOf(contract.secType()),
                contract.currency(),
                quantity, averageCost, marketPrice, marketValue, unrealizedPNL, realizedPNL, accountName));
    }

    @Override
    public void updateAccountValue(String key, String value, String currency, String accountName) {
        if ("NetLiquidation".equals(key)) {
            netLiquidation = parseDoubleOrNaN(value);
        } else if ("TotalCashValue".equals(key)) {
            totalCashValue = parseDoubleOrNaN(value);
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

    // ── daily closes (for the momentum) ─────────────────────────────────────────────────────────

    @Override
    public void historicalData(int requestId, Bar bar) {
        List<DailyClose> closes = dailyClosesByRequest.get(requestId);
        LocalDate barDate = dateOf(bar);
        if (closes != null && barDate != null) {
            closes.add(new DailyClose(barDate, bar.close()));
        }
    }

    @Override
    public void historicalDataEnd(int requestId, String startDate, String endDate) {
        finishDailyCloses(requestId);
    }

    /** Counts a request down once, whether it ended with its bars or with an error. */
    private void finishDailyCloses(int requestId) {
        if (conIdByDailyCloseRequest.containsKey(requestId) && finishedDailyCloseRequests.add(requestId)) {
            dailyClosesDownloaded.countDown();
        }
    }

    /**
     * A daily bar's time is its date as {@code yyyyMMdd}, sometimes followed by a time; {@code null} (the bar is
     * skipped) if it is anything else — an exception here would stop the reader loop's whole batch of messages.
     */
    private LocalDate dateOf(Bar bar) {
        String time = bar.time();
        try {
            return time != null && time.length() >= 8 ? LocalDate.parse(time.substring(0, 8), BAR_DATE_FORMAT) : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    // ── errors ──────────────────────────────────────────────────────────────────────────────────

    @Override
    public void error(int requestId, long errorTime, int errorCode, String errorMessage, String advancedOrderRejectJson) {
        if (INFO_CODES.contains(errorCode)) {
            System.out.println("[ib] " + errorMessage);
            return;
        }
        Integer dailyCloseConId = conIdByDailyCloseRequest.get(requestId);
        if (dailyCloseConId != null) {
            // e.g. 162, no market data permission: this contract keeps the closes of an earlier sync
            System.err.println("[ib error] daily closes of contract " + dailyCloseConId + ": code=" + errorCode
                    + " — " + errorMessage);
            finishDailyCloses(requestId);
            return;
        }
        System.err.println("[ib error] code=" + errorCode + " id=" + requestId + " — " + errorMessage);
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

            double totalMarketValue = 0;
            double totalUnrealizedPnl = 0;
            var holdingsLargestFirst = holdings.values().stream()
                    .sorted((firstHolding, secondHolding) ->
                            Double.compare(secondHolding.marketValue(), firstHolding.marketValue()))
                    .toList();
            for (Holding holding : holdingsLargestFirst) {
                System.out.printf("%-8s %12s %12s %12s %14s %14s %7.1f%%%n",
                        holding.symbol(),
                        formatQuantity(holding.position()),
                        formatMoney(holding.averageCost()),
                        formatMoney(holding.marketPrice()),
                        formatMoney(holding.marketValue()),
                        formatSignedMoney(holding.unrealizedPnl()),
                        holding.unrealizedPnlPercent());
                totalMarketValue += holding.marketValue();
                totalUnrealizedPnl += holding.unrealizedPnl();
            }
            System.out.println("────────────────────────────────────────────────────────────────────────────────────");
            System.out.printf("%-8s %12s %12s %12s %14s %14s%n",
                    "TOTAL", "", "", "", formatMoney(totalMarketValue), formatSignedMoney(totalUnrealizedPnl));
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

    private double parseDoubleOrNaN(String value) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private String formatMoney(double amount) {
        return String.format("%,.2f", amount);
    }

    private String formatSignedMoney(double amount) {
        return (amount >= 0 ? "+" : "-") + String.format("%,.2f", Math.abs(amount));
    }

    /** Whole share counts print without decimals; fractional shares keep 4 places. */
    private String formatQuantity(double quantity) {
        return quantity == Math.floor(quantity)
                ? String.format("%.0f", quantity)
                : String.format("%.4f", quantity);
    }
}
