package portfolioboss.tax;

import org.springframework.stereotype.Service;
import portfolioboss.calculation.tax.UsSettlementDayEstimate;
import portfolioboss.db.portfolio.TradeRepository;
import portfolioboss.db.tax.FlexCashTransactionRepository;
import portfolioboss.db.tax.FlexTradeRepository;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

/**
 * Makes sure every settlement day the tax converts has the dollar's rate (CAPITAL_GAINS_TAX_TODO.md, session 2): gathers
 * the settlement days in dollars — of the account's trades and cash transactions in IB's Flex reports, and of the trades
 * typed in by hand, for each investor's tax —, and has {@link ExchangeRateDownloadService} download the rates still
 * missing. Called by {@code sync.SyncService} after every sync that reached TWS, after the Flex reports, which bring the
 * account's settlement days.
 *
 * <p>Only the dollar: the Bank of Israel publishes no rate for the Hong Kong dollar, in any of its series (checked
 * 2026-11-15), so a sale in it is entered by hand (decision 10); the account trades in no other currency.
 */
@Service
public class SettlementDayRatesService {

    /**
     * The one currency the account trades in that the Bank of Israel publishes rates for — also the currency whose rates
     * the tax converts with ({@link CapitalGainsTaxService}).
     */
    protected static final String US_DOLLAR = "USD";

    private final FlexTradeRepository flexTradeRepository;
    private final FlexCashTransactionRepository flexCashTransactionRepository;
    private final TradeRepository tradeRepository;
    private final ExchangeRateDownloadService exchangeRateDownloadService;

    public SettlementDayRatesService(FlexTradeRepository flexTradeRepository,
                                     FlexCashTransactionRepository flexCashTransactionRepository,
                                     TradeRepository tradeRepository,
                                     ExchangeRateDownloadService exchangeRateDownloadService) {
        this.flexTradeRepository = flexTradeRepository;
        this.flexCashTransactionRepository = flexCashTransactionRepository;
        this.tradeRepository = tradeRepository;
        this.exchangeRateDownloadService = exchangeRateDownloadService;
    }

    /**
     * Downloads the dollar's rates of the settlement days that have none yet; never fails the sync
     * ({@link ExchangeRateDownloadService} prints a failure).
     *
     * @param today the sync's day
     */
    public void downloadMissingDollarRates(LocalDate today) {
        exchangeRateDownloadService.downloadMissingRates(US_DOLLAR, settlementDaysInDollars(), today);
    }

    /**
     * Every settlement day in dollars: IB's own for the Flex trades — buys too, whose rate is a lot's cost in shekels
     * (decision 11) — and the Flex cash transactions — a fee is a tax shield —, and the estimated one for each typed
     * trade.
     */
    private Set<LocalDate> settlementDaysInDollars() {
        Set<LocalDate> settlementDays = new HashSet<>();
        settlementDays.addAll(flexTradeRepository.findSettleDatesInCurrency(US_DOLLAR));
        settlementDays.addAll(flexCashTransactionRepository.findSettleDatesInCurrency(US_DOLLAR));
        for (LocalDate typedTradeDate : tradeRepository.findTradeDatesInCurrency(US_DOLLAR)) {
            settlementDays.add(new UsSettlementDayEstimate(typedTradeDate).estimatedSettlementDay());
        }
        return settlementDays;
    }
}
