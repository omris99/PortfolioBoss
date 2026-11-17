package portfolioboss.calculation.tax;

import portfolioboss.exchangerates.ExchangeRates;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The capital gains tax ("מס רווחי הון") of the account, or of one investor (CAPITAL_GAINS_TAX_TODO.md, session 3.5) —
 * the same calculation for both (decision 5): FIFO over each stock's trades ({@link FifoSalesCalculator}), each sale's
 * taxable gain ({@link SaleTaxableGain}), an entry for each settlement day with a sale or an expense
 * ({@link SettlementDayEntry}), and each tax year's balance with its monthly settlements ({@link TaxYearCalculator}).
 *
 * <p>The account's comes from IB's Flex reports, an investor's from the trades typed in for them; the account owner's is
 * what the account's leaves after the other investors' (decision 6), and is not computed on its own.
 *
 * @param tradesOfEveryStock      every stock's trades, each in the order they were made
 * @param expenses                the expenses that lower the taxable gain: the account's IB fees; none for an investor
 * @param exchangeRatesByCurrency the stored rates of each currency the Bank of Israel publishes
 * @param lastSyncDay             the day of the last sync: which months are settled
 */
public record CapitalGainsTaxCalculator(List<TradesOfOneStock> tradesOfEveryStock, List<TaxDeductibleExpense> expenses,
                                 Map<String, ExchangeRates> exchangeRatesByCurrency, LocalDate lastSyncDay) {

    /** Every sale, computed or not, in the order of their settlement days. */
    public List<SaleTaxableGain> sales() {
        List<SaleTaxableGain> sales = new ArrayList<>();
        for (TradesOfOneStock stock : tradesOfEveryStock) {
            for (FifoSale fifoSale : new FifoSalesCalculator(stock.tradesInOrder()).sales()) {
                sales.add(new SaleTaxableGain(fifoSale, stock.notComputedReasonOfEverySale(), exchangeRatesByCurrency));
            }
        }
        sales.sort(Comparator.comparing(sale -> sale.sale().settlementDay()));
        return sales;
    }

    /** An entry for every settlement day with a sale or an expense, the earliest first. */
    public List<SettlementDayEntry> settlementDayEntries() {
        Map<LocalDate, List<SaleTaxableGain>> salesBySettlementDay = new TreeMap<>();
        for (SaleTaxableGain sale : sales()) {
            salesBySettlementDay.computeIfAbsent(sale.sale().settlementDay(), day -> new ArrayList<>()).add(sale);
        }
        Map<LocalDate, List<TaxDeductibleExpense>> expensesBySettlementDay = new TreeMap<>();
        for (TaxDeductibleExpense expense : expenses) {
            expensesBySettlementDay.computeIfAbsent(expense.settlementDay(), day -> new ArrayList<>()).add(expense);
        }

        Set<LocalDate> daysWithASaleOrAnExpense = new TreeSet<>(salesBySettlementDay.keySet());
        daysWithASaleOrAnExpense.addAll(expensesBySettlementDay.keySet());
        return daysWithASaleOrAnExpense.stream()
                .map(settlementDay -> new SettlementDayEntry(settlementDay,
                        salesBySettlementDay.getOrDefault(settlementDay, List.of()),
                        expensesBySettlementDay.getOrDefault(settlementDay, List.of()),
                        exchangeRatesByCurrency))
                .toList();
    }

    /** Every tax year with an entry, the earliest first. */
    public List<TaxYearCalculator> taxYears() {
        Map<Integer, List<SettlementDayEntry>> entriesByTaxYear = new TreeMap<>();
        for (SettlementDayEntry entry : settlementDayEntries()) {
            entriesByTaxYear.computeIfAbsent(taxYearOf(entry.settlementDay()), year -> new ArrayList<>()).add(entry);
        }
        return entriesByTaxYear.entrySet().stream()
                .map(year -> new TaxYearCalculator(year.getKey(), year.getValue(), lastSyncDay))
                .toList();
    }

    /**
     * The year of the settlement day, not of the trade (decision 16): a sale on 31 December that settles on 1 January
     * belongs to the new year, as Phoenix records every sale on its settlement day.
     */
    protected int taxYearOf(LocalDate settlementDay) {
        return settlementDay.getYear();
    }
}
