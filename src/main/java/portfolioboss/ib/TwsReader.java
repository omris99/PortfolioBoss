package portfolioboss.ib;

import org.springframework.boot.ApplicationArguments;
import org.springframework.stereotype.Service;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * The one place that connects to TWS. It owns the TWS host, port and client id, opens a new {@link IbGateway} for each
 * reading and disconnects at its end, and lets one reading through at a time: TWS refuses a second connection with the
 * same client id. {@code ./run.sh} reads the portfolio through it at startup ({@code TwsPortfolioRunner}), and the Scan
 * again button reads a new scan ({@code api.ScanService}, RECOMMENDATIONS_TODO.md, session 1b). Read-only, like
 * everything that talks to IB: it never places, modifies or cancels an order.
 *
 * <p>{@code ./run.sh 7497 102} arrives as the two non-option arguments (port, client id).
 */
@Service
public class TwsReader {

    private static final String TWS_HOST = "127.0.0.1";
    private static final int DEFAULT_TWS_PORT = 7496;      // live TWS; use 7497 for paper
    private static final int DEFAULT_TWS_CLIENT_ID = 101;  // must differ from the trading bot (id 0)
    private static final int PORTFOLIO_DOWNLOAD_TIMEOUT_SECONDS = 15;
    // Session 0 measured under a second a scan, four seconds for 71 stocks' contract details and three for 72
    // contracts' daily closes: the timeouts only matter when IB is slow.
    private static final int SCAN_TIMEOUT_SECONDS = 10;
    private static final int CONTRACT_DETAILS_TIMEOUT_SECONDS = 15;
    private static final int DAILY_CLOSES_TIMEOUT_SECONDS = 20;
    // Read one at a time on 2026-10-29, a page of headlines took a fifth of a second; a busy stock has some 1,200 Dow
    // Jones headlines in half a year (CRWD) — four pages.
    private static final int NEWS_ROUND_TIMEOUT_SECONDS = 20;
    private static final int MOST_NEWS_ROUNDS = 8;
    /** The Scan again button's answer when TWS can't be read; the console line says why. */
    private static final String TWS_NOT_REACHABLE = "TWS is not reachable: open TWS and log in, then scan again.";

    private final int twsPort;
    private final int twsClientId;
    private final ReentrantLock oneReadingAtATime = new ReentrantLock();

    public TwsReader(ApplicationArguments arguments) {
        List<String> positionalArguments = arguments.getNonOptionArgs();
        this.twsPort = intArgumentOrDefault(positionalArguments, 0, DEFAULT_TWS_PORT);
        this.twsClientId = intArgumentOrDefault(positionalArguments, 1, DEFAULT_TWS_CLIENT_ID);
    }

    /**
     * {@code ./run.sh}: the portfolio, then — when {@code withScan} — the scans for hot stocks with the contract details
     * of the stocks they found, then the holdings' contract details, then the analysts' price targets in the Dow Jones
     * headlines of the stocks the scan kept and of the holdings, in the same rounds, then the daily closes of the
     * holdings, of SPY and of the stocks the scan kept. Waits for a reading already running to end. {@code null} when the
     * portfolio could not be read; the reason has been printed.
     */
    public TwsReading readPortfolio(boolean withScan) throws InterruptedException {
        oneReadingAtATime.lock();
        IbGateway gateway = new IbGateway();
        try {
            PortfolioSnapshot snapshot = connectAndReadPortfolio(gateway);
            Scan scan = withScan ? readHotStockScan(gateway) : null;
            Map<Integer, StockContractDetails> holdingContractDetailsByConId =
                    readContractDetailsOfHoldings(gateway, snapshot, scan);

            Map<Integer, String> companyNameByConId =
                    scan == null ? new LinkedHashMap<>() : companyNamesOfScannedStocks(scan);
            holdingContractDetailsByConId.forEach((conId, contractDetails) ->
                    companyNameByConId.putIfAbsent(conId, contractDetails.companyName()));
            Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsOfAllStocks =
                    readAnalystPriceTargets(gateway, companyNameByConId, Instant.now());
            Map<Integer, List<AnalystPriceTarget>> holdingAnalystPriceTargetsByConId =
                    analystPriceTargetsOfStocks(holdingContractDetailsByConId.keySet(), analystPriceTargetsOfAllStocks);
            System.out.printf("[ib] analysts' price targets of the holdings: %d for %d of %d holdings%n",
                    holdingAnalystPriceTargetsByConId.values().stream().mapToInt(List::size).sum(),
                    holdingAnalystPriceTargetsByConId.size(), snapshot.holdings().size());
            Scan scanWithAnalystPriceTargets = scan == null ? null : scan.withAnalystPriceTargets(
                    analystPriceTargetsOfStocks(companyNamesOfScannedStocks(scan).keySet(),
                            analystPriceTargetsOfAllStocks));

            Set<Integer> conIds = new LinkedHashSet<>();
            snapshot.holdings().forEach(holding -> conIds.add(holding.conId()));
            conIds.add(Benchmark.SPY_CON_ID);
            if (scan != null) {
                scan.candidates().forEach(candidate -> conIds.add(candidate.conId()));
            }
            return new TwsReading(snapshot, readDailyCloses(gateway, conIds), scanWithAnalystPriceTargets,
                    holdingContractDetailsByConId, holdingAnalystPriceTargetsByConId);
        } catch (TwsUnavailableException e) {
            System.err.println(e.reason());
            return null;
        } finally {
            gateway.disconnect();
            oneReadingAtATime.unlock();
        }
    }

    /**
     * The Scan again button: the scans, the contract details of the stocks they found and the daily closes of the
     * stocks the scan kept and of SPY (the momentum is measured against it). The portfolio comes with every connection
     * — it is how the connection proves it works — but is not kept: the holdings stay as {@code ./run.sh} last read
     * them.
     *
     * @throws TwsBusyException        while another reading runs: the startup's, or another press of the button
     * @throws TwsUnavailableException when TWS can't be read, or no scan answered
     */
    public ScanReading readScan() throws InterruptedException {
        if (!oneReadingAtATime.tryLock()) {
            throw new TwsBusyException("A scan is already running, or the startup is still reading TWS: try again in "
                    + "a moment.");
        }
        IbGateway gateway = new IbGateway();
        try {
            connectAndReadPortfolio(gateway);
            Scan scan = readHotStockScan(gateway);
            if (scan == null) {
                throw new TwsUnavailableException("TWS answered, but no scan did: try again in a minute.",
                        "[ib] scan unavailable: no scan answered");
            }
            Scan scanWithAnalystPriceTargets = scan.withAnalystPriceTargets(
                    readAnalystPriceTargets(gateway, companyNamesOfScannedStocks(scan), scan.scannedAt()));
            Set<Integer> conIds = new LinkedHashSet<>();
            conIds.add(Benchmark.SPY_CON_ID);
            scan.candidates().forEach(candidate -> conIds.add(candidate.conId()));
            return new ScanReading(scanWithAnalystPriceTargets, readDailyCloses(gateway, conIds));
        } catch (TwsUnavailableException e) {
            System.err.println(e.reason());
            throw e;
        } finally {
            gateway.disconnect();
            oneReadingAtATime.unlock();
        }
    }

    /** Connects and waits for the portfolio, which IB sends on every connection. */
    private PortfolioSnapshot connectAndReadPortfolio(IbGateway gateway) throws InterruptedException {
        try {
            gateway.connect(TWS_HOST, twsPort, twsClientId);
        } catch (IllegalStateException e) {
            throw new TwsUnavailableException(TWS_NOT_REACHABLE, e.getMessage());
        }
        boolean portfolioReceived = gateway.awaitPortfolio(PORTFOLIO_DOWNLOAD_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (gateway.connectionFailed()) {
            throw new TwsUnavailableException(TWS_NOT_REACHABLE,
                    "Connection was refused by TWS. Check the port and that API access is enabled.");
        }
        if (!portfolioReceived) {
            throw new TwsUnavailableException(TWS_NOT_REACHABLE, "Timed out after " + PORTFOLIO_DOWNLOAD_TIMEOUT_SECONDS
                    + "s waiting for portfolio data. Is TWS running and logged in on port " + twsPort + "?");
        }
        return gateway.snapshot();
    }

    /**
     * Still connected, after the portfolio: the scans for hot stocks, then the contract details of the stocks they
     * found — their exchange and sector. Without the analysts' price targets: the caller reads them next, with the
     * holdings' when there are any. {@code null} when no scan answered.
     */
    private Scan readHotStockScan(IbGateway gateway) throws InterruptedException {
        gateway.requestHotStockScans();
        if (!gateway.awaitScans(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            System.out.println("[ib] scans still running after " + SCAN_TIMEOUT_SECONDS + "s; keeping what arrived");
        }
        Instant scannedAt = Instant.now();
        List<ScanRow> scanRows = gateway.scanRows();
        if (scanRows.isEmpty()) {
            System.out.println("[ib] scan unavailable: no scan answered");
            return null;
        }

        Set<Integer> foundConIds = new LinkedHashSet<>();
        scanRows.forEach(scanRow -> foundConIds.add(scanRow.conId()));
        gateway.requestContractDetails(foundConIds);
        if (!gateway.awaitContractDetails(CONTRACT_DETAILS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            System.out.println("[ib] contract details still missing after " + CONTRACT_DETAILS_TIMEOUT_SECONDS
                    + "s; keeping what arrived");
        }
        Scan scan = new Scan(scannedAt, scanRows, gateway.contractDetails());
        System.out.printf("[ib] scans found %d stocks, %d of them on NYSE or NASDAQ%n",
                foundConIds.size(), scan.candidates().size());
        List<String> leftOutStocks = scan.leftOutStocks();
        if (!leftOutStocks.isEmpty()) {
            System.out.println("[ib] left out, not on NYSE or NASDAQ: " + String.join(", ", leftOutStocks));
        }
        return scan;
    }

    /**
     * Still connected: the contract details of every holding — its sector (MY_HOLDINGS_TODO.md, decision 11) and the
     * name its headlines are read with. A holding the scan found is not asked again: the scan's details are its own. One
     * whose details fail or come late is left out, and keeps the sector stored before.
     */
    private Map<Integer, StockContractDetails> readContractDetailsOfHoldings(IbGateway gateway,
                                                                            PortfolioSnapshot snapshot, Scan scan)
            throws InterruptedException {
        Map<Integer, StockContractDetails> contractDetailsAlreadyRead =
                scan == null ? Map.of() : scan.contractDetailsByConId();
        List<Integer> holdingConIds = snapshot.holdings().stream().map(Holding::conId).distinct().toList();
        List<Integer> holdingConIdsToRequest = holdingConIds.stream()
                .filter(conId -> !contractDetailsAlreadyRead.containsKey(conId))
                .toList();
        if (!holdingConIdsToRequest.isEmpty()) {
            gateway.requestContractDetails(holdingConIdsToRequest);
            if (!gateway.awaitContractDetails(CONTRACT_DETAILS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.out.println("[ib] contract details still missing after " + CONTRACT_DETAILS_TIMEOUT_SECONDS
                        + "s; keeping what arrived");
            }
        }
        // every contract details this connection received: the scan's and the holdings'
        Map<Integer, StockContractDetails> contractDetailsOfThisConnection = gateway.contractDetails();
        Map<Integer, StockContractDetails> holdingContractDetailsByConId = new LinkedHashMap<>();
        for (int conId : holdingConIds) {
            StockContractDetails contractDetails = contractDetailsOfThisConnection.get(conId);
            if (contractDetails != null) {
                holdingContractDetailsByConId.put(conId, contractDetails);
            }
        }
        System.out.printf("[ib] contract details received for %d of %d holdings%n",
                holdingContractDetailsByConId.size(), holdingConIds.size());
        return holdingContractDetailsByConId;
    }

    /** By contract id, the company name of each stock the scan kept: its headlines are read with it. */
    private Map<Integer, String> companyNamesOfScannedStocks(Scan scan) {
        Map<Integer, String> companyNameByConId = new LinkedHashMap<>();
        scan.candidates().forEach(candidate -> companyNameByConId.put(candidate.conId(), candidate.companyName()));
        return companyNameByConId;
    }

    /** Of the targets read for every stock, those of these stocks only: the holdings', or the scanned stocks'. */
    private Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsOfStocks(
            Collection<Integer> stockConIds, Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsOfAllStocks) {
        Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsByConId = new LinkedHashMap<>();
        for (int conId : stockConIds) {
            List<AnalystPriceTarget> analystPriceTargets = analystPriceTargetsOfAllStocks.get(conId);
            if (analystPriceTargets != null) {
                analystPriceTargetsByConId.put(conId, analystPriceTargets);
            }
        }
        return analystPriceTargetsByConId;
    }

    /**
     * Still connected: half a year of each stock's Dow Jones headlines, page by page, and the analysts' price targets in
     * them (decision 14) — the scanned stocks' and the holdings' in the same rounds, each stock once. A stock without a
     * company name is not read: its headlines couldn't be told from other companies'. One whose headlines fail or come
     * late keeps the targets stored before.
     *
     * @param companyNameByConId IB's name of each stock's company, from its contract details
     * @param readAt             the headlines of the half year before this day are read
     */
    private Map<Integer, List<AnalystPriceTarget>> readAnalystPriceTargets(IbGateway gateway,
                                                                          Map<Integer, String> companyNameByConId,
                                                                          Instant readAt)
            throws InterruptedException {
        List<Integer> conIdsToRead = companyNameByConId.keySet().stream()
                .filter(conId -> hasCompanyName(companyNameByConId.get(conId)))
                .toList();
        if (conIdsToRead.isEmpty()) {
            return Map.of();
        }
        LocalDateTime oldestWanted = readAt.atZone(ZoneId.systemDefault()).toLocalDate()
                .minusMonths(AnalystPriceTarget.MONTHS_COUNTED)
                .atStartOfDay();
        NewsHeadlinePaging paging = new NewsHeadlinePaging(conIdsToRead, oldestWanted);
        System.out.printf("[ib] requesting half a year of Dow Jones headlines for %d stocks%n", conIdsToRead.size());
        for (int round = 1; round <= MOST_NEWS_ROUNDS && paging.hasStocksToRead(); round++) {
            Map<Integer, LocalDateTime> nextPages = paging.nextPages();
            gateway.requestNewsHeadlines(nextPages);
            if (!gateway.awaitNewsHeadlines(NEWS_ROUND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                System.out.println("[ib] Dow Jones headlines still missing after " + NEWS_ROUND_TIMEOUT_SECONDS
                        + "s; keeping what arrived");
            }
            Map<Integer, List<NewsHeadline>> pages = gateway.newsHeadlines();
            nextPages.keySet().forEach(conId -> paging.addPage(conId, pages.getOrDefault(conId, List.of())));
        }

        Map<Integer, List<AnalystPriceTarget>> targetsByConId =
                priceTargetsIn(paging.headlinesByConId(), companyNameByConId);
        int targetCount = targetsByConId.values().stream().mapToInt(List::size).sum();
        System.out.printf("[ib] analysts' price targets: %d in the Dow Jones headlines of %d of %d stocks%n",
                targetCount, targetsByConId.size(), conIdsToRead.size());
        return targetsByConId;
    }

    /** Each stock's own targets, read with its IB name: other companies' headlines in its feed are left out. */
    private Map<Integer, List<AnalystPriceTarget>> priceTargetsIn(Map<Integer, List<NewsHeadline>> headlinesByConId,
                                                                  Map<Integer, String> companyNameByConId) {
        Map<Integer, List<AnalystPriceTarget>> targetsByConId = new LinkedHashMap<>();
        headlinesByConId.forEach((conId, headlines) -> {
            PriceTargetHeadlineReader headlineReader = new PriceTargetHeadlineReader(companyNameByConId.get(conId));
            targetsByConId.put(conId, headlines.stream()
                    .map(headlineReader::priceTargetIn)
                    .flatMap(Optional::stream)
                    .toList());
        });
        return targetsByConId;
    }

    /** A name its headlines can be matched with; IB may send an empty one. */
    private boolean hasCompanyName(String companyName) {
        return companyName != null && !companyName.isBlank();
    }

    /**
     * Still connected: a year of daily closes of each contract, for the momentum. A contract whose closes fail or come
     * late keeps the ones an earlier sync stored.
     */
    private Map<Integer, List<DailyClose>> readDailyCloses(IbGateway gateway, Collection<Integer> conIds)
            throws InterruptedException {
        gateway.requestDailyCloses(conIds);
        if (!gateway.awaitDailyCloses(DAILY_CLOSES_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            System.out.println("[ib] daily closes still missing after " + DAILY_CLOSES_TIMEOUT_SECONDS
                    + "s; keeping what arrived");
        }
        Map<Integer, List<DailyClose>> dailyCloses = gateway.dailyCloses();
        System.out.printf("[ib] daily closes received for %d of %d contracts%n", dailyCloses.size(), conIds.size());
        return dailyCloses;
    }

    private int intArgumentOrDefault(List<String> positionalArguments, int index, int defaultValue) {
        return positionalArguments.size() > index ? Integer.parseInt(positionalArguments.get(index)) : defaultValue;
    }
}
