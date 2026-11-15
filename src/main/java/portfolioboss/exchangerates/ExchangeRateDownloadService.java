package portfolioboss.exchangerates;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Downloads the Bank of Israel's rates of the settlement days whose rate is missing (CAPITAL_GAINS_TAX_TODO.md, session
 * 2): asks {@link ExchangeRateDownloadSchedule} which days are really missing — before any call —, asks the Bank of
 * Israel for them ({@link BankOfIsraelClient}) and stores what it published. Which days need a rate is the caller's to
 * say ({@code tax.SettlementDayRatesService}); this package knows nothing about trades or the tax.
 *
 * <p>Not {@code @Transactional}: no database connection should wait on the Bank of Israel. The rates of a request are
 * stored together, by {@code saveAll}, in a transaction of their own.
 */
@Service
public class ExchangeRateDownloadService {

    /** A date in the console: "15.11". */
    private static final DateTimeFormatter CONSOLE_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.MM");

    private final BankOfIsraelClient bankOfIsraelClient;
    private final ExchangeRateRepository exchangeRateRepository;
    /**
     * The startup's sync and a Sync now pressed during its download could both reach here; one download at a time, so
     * that the two don't store the same new rates at once — the second would break the table's UNIQUE.
     */
    private final ReentrantLock oneDownloadAtATime = new ReentrantLock();

    public ExchangeRateDownloadService(BankOfIsraelClient bankOfIsraelClient,
                                       ExchangeRateRepository exchangeRateRepository) {
        this.bankOfIsraelClient = bankOfIsraelClient;
        this.exchangeRateRepository = exchangeRateRepository;
    }

    /**
     * Downloads and stores the currency's rates of the settlement days that have none yet. Never fails its caller, the
     * sync: a failure — no answer, an HTTP error, an answer that can't be read — is printed as
     * {@code [exchangerates error]}, and the next sync asks again for what is still missing.
     *
     * @param currency                   the currency's code, "USD"
     * @param settlementDaysNeedingARate the days whose rate is needed; those already known are not asked for
     * @param today                      the sync's day: a day after it can't have a rate yet
     */
    public void downloadMissingRates(String currency, Set<LocalDate> settlementDaysNeedingARate, LocalDate today) {
        if (!oneDownloadAtATime.tryLock()) {
            System.out.println("[exchangerates] Bank of Israel skipped: another download is running");
            return;
        }
        try {
            downloadPeriodsDue(currency, settlementDaysNeedingARate, today);
        } finally {
            oneDownloadAtATime.unlock();
        }
    }

    private void downloadPeriodsDue(String currency, Set<LocalDate> settlementDaysNeedingARate, LocalDate today) {
        ExchangeRates storedRates = exchangeRateRepository.storedRatesOf(currency);
        List<ExchangeRateDownloadPeriod> periodsToDownload =
                new ExchangeRateDownloadSchedule(storedRates, settlementDaysNeedingARate, today).periodsToDownload();
        if (periodsToDownload.isEmpty()) {
            System.out.println("[exchangerates] Bank of Israel skipped: every settlement day up to "
                    + CONSOLE_DATE_FORMAT.format(today) + " has its " + currency + " rate");
            return;
        }
        for (ExchangeRateDownloadPeriod period : periodsToDownload) {
            try {
                downloadAndStore(currency, period);
            } catch (RuntimeException e) {
                // The next sync asks again for every day still missing.
                System.err.println("[exchangerates error] " + e.getMessage());
                return;
            }
        }
    }

    private void downloadAndStore(String currency, ExchangeRateDownloadPeriod period) {
        System.out.println("[exchangerates] Bank of Israel: asking for the " + currency + " rates of " + period);
        List<BankOfIsraelRate> publishedRates = bankOfIsraelClient.exchangeRates(currency, period);
        if (publishedRates.isEmpty()) {
            System.out.println("[exchangerates] Bank of Israel: no " + currency + " rate published in " + period);
            return;
        }
        List<ExchangeRateEntity> newRates = publishedRates.stream()
                .map(publishedRate -> new ExchangeRateEntity(currency, publishedRate))
                .toList();
        exchangeRateRepository.saveAll(newRates);
        System.out.printf("[exchangerates] Bank of Israel: stored %d %s rates (%s – %s)%n", newRates.size(), currency,
                publishedRates.getFirst().rateDate(), publishedRates.getLast().rateDate());
    }
}
