package portfolioboss.db;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.DailyClose;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.Scan;
import portfolioboss.model.HoldingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Brings the database up to date with one snapshot from TWS, once per connection. A holding TWS reported is
 * created or refreshed; an open holding TWS no longer reports is marked closed, never deleted, so the
 * sector and trades entered by hand survive. The daily closes read on the same connection (for the momentum) and the
 * scan for hot stocks, when one ran, with the analysts' price targets of its stocks, are stored with it.
 *
 * <p>Everything happens in one transaction: either the whole snapshot is stored or nothing is.
 */
@Service
public class PortfolioSyncService {

    private final HoldingRepository holdingRepository;
    private final AccountStateRepository accountStateRepository;
    private final DailyCloseRepository dailyCloseRepository;
    private final ScanRepository scanRepository;
    private final AnalystPriceTargetRepository analystPriceTargetRepository;

    public PortfolioSyncService(HoldingRepository holdingRepository, AccountStateRepository accountStateRepository,
                                DailyCloseRepository dailyCloseRepository, ScanRepository scanRepository,
                                AnalystPriceTargetRepository analystPriceTargetRepository) {
        this.holdingRepository = holdingRepository;
        this.accountStateRepository = accountStateRepository;
        this.dailyCloseRepository = dailyCloseRepository;
        this.scanRepository = scanRepository;
        this.analystPriceTargetRepository = analystPriceTargetRepository;
    }

    /** When the latest stored scan ran; empty before the first one. */
    @Transactional(readOnly = true)
    public Optional<Instant> lastScannedAt() {
        return scanRepository.findLatestScannedAt();
    }

    /** A sync without daily closes: the ones stored before stay as they are. */
    @Transactional
    public SyncResult sync(PortfolioSnapshot snapshot) {
        return sync(snapshot, Map.of());
    }

    /** A sync without a scan: the latest stored scan stays the latest. */
    @Transactional
    public SyncResult sync(PortfolioSnapshot snapshot, Map<Integer, List<DailyClose>> dailyCloses) {
        return sync(snapshot, dailyCloses, null);
    }

    /**
     * @param dailyCloses by IB contract id — the holdings', SPY's and the scanned stocks', as many as IB sent this
     *                    connection
     * @param scan        {@code null} when no scan ran or answered this connection
     */
    @Transactional
    public SyncResult sync(PortfolioSnapshot snapshot, Map<Integer, List<DailyClose>> dailyCloses, Scan scan) {
        String account = snapshot.account();
        Instant syncedAt = snapshot.asOf();
        Set<Integer> reportedConIds = new HashSet<>();
        int addedCount = 0;
        int updatedCount = 0;

        for (Holding holdingFromIb : snapshot.holdings()) {
            reportedConIds.add(holdingFromIb.conId());
            Optional<HoldingEntity> storedHolding =
                    holdingRepository.findByAccountAndConId(account, holdingFromIb.conId());
            if (storedHolding.isPresent()) {
                storedHolding.get().refreshFromIb(holdingFromIb, syncedAt);   // Hibernate writes the change at commit
                updatedCount++;
            } else {
                holdingRepository.save(new HoldingEntity(account, holdingFromIb, syncedAt));
                addedCount++;
            }
        }

        int closedCount = markMissingAsClosed(account, reportedConIds, syncedAt);
        accountStateRepository.save(new AccountStateEntity(snapshot));
        System.out.printf("[db] synced %d holdings (%d new, %d updated, %d closed)%n",
                reportedConIds.size(), addedCount, updatedCount, closedCount);
        storeDailyCloses(dailyCloses);
        storeScan(scan, dailyCloses);
        return new SyncResult(addedCount, updatedCount, closedCount);
    }

    /**
     * The Scan again button: a new scan and the daily closes read with it — its stocks' and SPY's —, without a
     * portfolio: the holdings stay as the last sync left them. In one transaction, like a sync.
     */
    @Transactional
    public void syncScan(Scan scan, Map<Integer, List<DailyClose>> dailyCloses) {
        storeDailyCloses(dailyCloses);
        storeScan(scan, dailyCloses);
    }

    /**
     * A new row for every scan, never replacing an older one: the measurement compares them over months. Each
     * candidate is stored with its last close of this run — the price the measurement starts from.
     */
    private void storeScan(Scan scan, Map<Integer, List<DailyClose>> dailyCloses) {
        if (scan == null) {
            return;
        }
        Map<Integer, DailyClose> lastCloseByConId = new HashMap<>();
        dailyCloses.forEach((conId, closes) -> lastCloseByConId.put(conId, lastCloseOf(closes)));
        ScanEntity storedScan = scanRepository.save(new ScanEntity(scan, lastCloseByConId));
        System.out.printf("[db] stored a scan of %d stocks%n", storedScan.candidates().size());
        storeAnalystPriceTargets(scan.analystPriceTargetsByConId());
    }

    /**
     * A stock whose Dow Jones headlines were read gets their targets in place of the ones stored before — an empty list
     * too: its headlines hold none now. One whose headlines did not come keeps its old ones, like the closes.
     */
    private void storeAnalystPriceTargets(Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsByConId) {
        if (analystPriceTargetsByConId.isEmpty()) {
            return;
        }
        analystPriceTargetRepository.deleteForContracts(analystPriceTargetsByConId.keySet());
        List<AnalystPriceTargetEntity> rows = analystPriceTargetsByConId.entrySet().stream()
                .flatMap(stockTargets -> stockTargets.getValue().stream()
                        .map(analystPriceTarget ->
                                new AnalystPriceTargetEntity(stockTargets.getKey(), analystPriceTarget)))
                .toList();
        analystPriceTargetRepository.saveAll(rows);
        System.out.printf("[db] stored %d analysts' price targets for %d stocks%n",
                rows.size(), analystPriceTargetsByConId.size());
    }

    private DailyClose lastCloseOf(List<DailyClose> closes) {
        return closes.stream().max(Comparator.comparing(DailyClose::date)).orElse(null);
    }

    /**
     * A contract IB sent closes for gets them in place of the ones stored before — IB adjusts past prices for splits,
     * so nothing old is kept. A contract it sent none for (an error, a timeout) keeps its old ones. A date IB sent twice
     * is stored once, the later value: a duplicate would break the table's {@code UNIQUE} constraint and, with it, the
     * whole sync.
     */
    private void storeDailyCloses(Map<Integer, List<DailyClose>> dailyCloses) {
        if (dailyCloses.isEmpty()) {
            return;
        }
        dailyCloseRepository.deleteForContracts(dailyCloses.keySet());
        List<DailyCloseEntity> rows = dailyCloses.entrySet().stream()
                .flatMap(contractCloses -> oneClosePerDate(contractCloses.getValue()).stream()
                        .map(dailyClose -> new DailyCloseEntity(contractCloses.getKey(), dailyClose)))
                .toList();
        dailyCloseRepository.saveAll(rows);
        System.out.printf("[db] stored %d daily closes for %d contracts%n", rows.size(), dailyCloses.size());
    }

    private Collection<DailyClose> oneClosePerDate(List<DailyClose> closes) {
        Map<LocalDate, DailyClose> closeByDate = new TreeMap<>();
        closes.forEach(dailyClose -> closeByDate.put(dailyClose.date(), dailyClose));
        return closeByDate.values();
    }

    /**
     * Closing is reversible: if the holding shows up again it is reopened by {@code refreshFromIb}, so a partial
     * read from IB costs nothing permanent.
     */
    private int markMissingAsClosed(String account, Set<Integer> reportedConIds, Instant syncedAt) {
        int closedCount = 0;
        for (HoldingEntity openHolding : holdingRepository.findByAccountAndStatus(account, HoldingStatus.OPEN)) {
            if (!reportedConIds.contains(openHolding.conId())) {
                openHolding.markClosed(syncedAt);
                closedCount++;
            }
        }
        return closedCount;
    }
}
