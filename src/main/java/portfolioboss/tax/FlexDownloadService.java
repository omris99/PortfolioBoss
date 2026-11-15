package portfolioboss.tax;

import org.springframework.stereotype.Service;
import portfolioboss.db.TradeRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Brings the stored Flex reports up to date on every sync (CAPITAL_GAINS_TAX_TODO.md, session 1.4): asks
 * {@link FlexDownloadSchedule} which periods are missing, downloads them one after another ({@link FlexClient}) and
 * stores each ({@link FlexReportStorageService}). The first sync ever downloads the history, year after year; later
 * ones only the last days, and the syncs after a day's first nothing at all.
 *
 * <p>Not {@code @Transactional}: IB takes seconds to prepare a report, and no database connection should wait on it.
 * Each report is stored in a transaction of its own.
 */
@Service
public class FlexDownloadService {

    /** A date in the console: "13.11". */
    private static final DateTimeFormatter CONSOLE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

    private final FlexClient flexClient;
    private final FlexReportStorageService flexReportStorageService;
    private final TradeRepository tradeRepository;
    /**
     * The startup's sync and a Sync now pressed during its download could both reach here; one download at a time, so
     * that the two don't store the same new rows at once — the second would break the tables' UNIQUE.
     */
    private final ReentrantLock oneDownloadAtATime = new ReentrantLock();

    public FlexDownloadService(FlexClient flexClient, FlexReportStorageService flexReportStorageService,
                               TradeRepository tradeRepository) {
        this.flexClient = flexClient;
        this.flexReportStorageService = flexReportStorageService;
        this.tradeRepository = tradeRepository;
    }

    /**
     * Downloads and stores the days of IB's Flex reports not stored yet. Never fails its caller, the sync: a failure —
     * IB refusing, no answer, a report that can't be read or stored — is printed as {@code [tax error]}, and the next
     * sync goes on from the last period stored. Without the keys it does nothing (the startup said which one is
     * missing).
     *
     * @param today the sync's day
     * @throws InterruptedException when the thread is asked to stop while waiting for IB to prepare a report
     */
    public void downloadWhatIsMissing(LocalDate today) throws InterruptedException {
        if (!flexClient.hasKeys()) {
            return;
        }
        if (!oneDownloadAtATime.tryLock()) {
            System.out.println("[tax] Flex skipped: another download is running");
            return;
        }
        try {
            downloadPeriodsDue(today);
        } finally {
            oneDownloadAtATime.unlock();
        }
    }

    private void downloadPeriodsDue(LocalDate today) throws InterruptedException {
        LocalDate lastStoredDate = flexReportStorageService.lastStoredDate().orElse(null);
        LocalDate earliestTypedTradeDate = tradeRepository.findEarliestTradeDate().orElse(null);
        List<FlexReportPeriod> periodsDue =
                new FlexDownloadSchedule(lastStoredDate, earliestTypedTradeDate, today).periodsDue();
        if (periodsDue.isEmpty()) {
            System.out.println("[tax] Flex skipped: " + whyNothingIsDue(lastStoredDate));
            return;
        }
        for (FlexReportPeriod period : periodsDue) {
            try {
                downloadAndStore(period);
            } catch (RuntimeException e) {
                // The periods are in order, so stopping here leaves no gap in the stored days: the next sync asks again
                // from the last period stored.
                System.err.println("[tax error] Flex: " + e.getMessage());
                return;
            }
        }
    }

    private void downloadAndStore(FlexReportPeriod period) throws InterruptedException {
        System.out.println("[tax] Flex: asking IB for " + period);
        FlexReport report = flexClient.downloadReport(period);
        flexReportStorageService.storeReport(report, Instant.now());
        System.out.printf("[tax] Flex: %d trades, %d cash transactions (%s)%n",
                report.trades().size(), report.cashTransactions().size(), report.period());
    }

    /**
     * Nothing is due once the stored reports reach the last weekday before today — and before the first download only
     * when the earliest typed trade's year has no weekday before today yet (the first of January, say).
     */
    private String whyNothingIsDue(LocalDate lastStoredDate) {
        if (lastStoredDate == null) {
            return "no day before today to read";
        }
        return "already read up to " + CONSOLE_DATE_FORMAT.format(lastStoredDate);
    }
}
