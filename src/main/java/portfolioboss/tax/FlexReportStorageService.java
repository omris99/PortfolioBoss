package portfolioboss.tax;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Saves the Flex reports downloaded from IB in the database (CAPITAL_GAINS_TAX_TODO.md, session 1.3) — their trades in
 * {@code flex_trade}, their cash transactions in {@code flex_cash_transaction}, a line per report in the
 * {@code flex_report} log — and says up to which day they are saved. Nothing here touches the trades typed in by hand or
 * the holdings.
 */
@Service
public class FlexReportStorageService {

    private final FlexTradeRepository flexTradeRepository;
    private final FlexCashTransactionRepository flexCashTransactionRepository;
    private final FlexReportRepository flexReportRepository;

    public FlexReportStorageService(FlexTradeRepository flexTradeRepository,
                                    FlexCashTransactionRepository flexCashTransactionRepository,
                                    FlexReportRepository flexReportRepository) {
        this.flexTradeRepository = flexTradeRepository;
        this.flexCashTransactionRepository = flexCashTransactionRepository;
        this.flexReportRepository = flexReportRepository;
    }

    /** The last day of the stored reports; empty before the first download. */
    @Transactional(readOnly = true)
    public Optional<LocalDate> lastStoredDate() {
        return flexReportRepository.findLatestToDate();
    }

    /**
     * One report into the database, whole or not at all: each trade and cash transaction replaces the stored row with
     * the same IB id, or is added, and the report's days are logged. Storing the same days again adds nothing.
     */
    @Transactional
    public void storeReport(FlexReport report, Instant downloadedAt) {
        storeTrades(report.trades());
        storeCashTransactions(report.cashTransactions());
        flexReportRepository.save(new FlexReportEntity(report, downloadedAt));
    }

    private void storeTrades(List<FlexReportTrade> trades) {
        // A trade IB sent twice in one report is stored once, as it came the second time: two new rows with one IB id
        // would break the table's UNIQUE, and with it the whole report.
        Map<String, FlexReportTrade> tradesByIbTradeId = new LinkedHashMap<>();
        for (FlexReportTrade trade : trades) {
            tradesByIbTradeId.put(trade.ibTradeId(), trade);
        }
        Map<String, FlexTradeEntity> storedTradesByIbTradeId =
                flexTradeRepository.storedTradesByIbTradeId(tradesByIbTradeId.keySet());

        List<FlexTradeEntity> newTrades = new ArrayList<>();
        tradesByIbTradeId.forEach((ibTradeId, trade) -> {
            FlexTradeEntity storedTrade = storedTradesByIbTradeId.get(ibTradeId);
            if (storedTrade == null) {
                newTrades.add(new FlexTradeEntity(trade));
            } else {
                storedTrade.refreshFrom(trade);
            }
        });
        flexTradeRepository.saveAll(newTrades);
    }

    private void storeCashTransactions(List<FlexReportCashTransaction> cashTransactions) {
        // As with the trades: a row IB sent twice in one report is stored once.
        Map<String, FlexReportCashTransaction> cashTransactionsByIbTransactionId = new LinkedHashMap<>();
        for (FlexReportCashTransaction cashTransaction : cashTransactions) {
            cashTransactionsByIbTransactionId.put(cashTransaction.ibTransactionId(), cashTransaction);
        }
        Map<String, FlexCashTransactionEntity> storedCashTransactionsByIbTransactionId = flexCashTransactionRepository
                .storedCashTransactionsByIbTransactionId(cashTransactionsByIbTransactionId.keySet());

        List<FlexCashTransactionEntity> newCashTransactions = new ArrayList<>();
        cashTransactionsByIbTransactionId.forEach((ibTransactionId, cashTransaction) -> {
            FlexCashTransactionEntity storedCashTransaction =
                    storedCashTransactionsByIbTransactionId.get(ibTransactionId);
            if (storedCashTransaction == null) {
                newCashTransactions.add(new FlexCashTransactionEntity(cashTransaction));
            } else {
                storedCashTransaction.refreshFrom(cashTransaction);
            }
        });
        flexCashTransactionRepository.saveAll(newCashTransactions);
    }
}
