package portfolioboss.tax;

import portfolioboss.exchangerates.ExchangeRates;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What one settlement day adds to the year's balance, as Phoenix records it (CAPITAL_GAINS_TAX_TODO.md, decision 4):
 * every sale settled that day and every expense of that day net out into one entry — "tax to pay" ("מס לשלם") when the
 * day gained, a "tax shield" ("מגן מס") when it lost. Six short trades of one day that lost money overall are one shield
 * of 25% of the day's net loss.
 *
 * @param settlementDay           the day
 * @param sales                   the sales settled that day, computed or not
 * @param expenses                the expenses settled that day — the account's IB fees; none for an investor
 * @param exchangeRatesByCurrency the stored rates of each currency the Bank of Israel publishes, for the expenses
 */
record SettlementDayEntry(LocalDate settlementDay, List<SaleTaxableGain> sales, List<TaxDeductibleExpense> expenses,
                          Map<String, ExchangeRates> exchangeRatesByCurrency) {

    /** 25%, without the surtax ("מס יסף") — decision 2. */
    private static final BigDecimal CAPITAL_GAINS_TAX_RATE = new BigDecimal("0.25");

    /** Phoenix's entries are in agorot. */
    private static final int AGOROT_DECIMAL_PLACES = 2;

    protected List<SaleTaxableGain> computedSales() {
        return sales.stream().filter(SaleTaxableGain::isComputed).toList();
    }

    /** Left out of the entry: a sale is never guessed ({@link SaleNotComputedReason}). */
    protected List<SaleTaxableGain> salesNotComputed() {
        return sales.stream().filter(sale -> !sale.isComputed()).toList();
    }

    /** The expenses the day's rate is known for; an expense in a currency without rates, or before its rate is out, waits. */
    protected List<TaxDeductibleExpense> expensesWithTheirRate() {
        return expenses.stream().filter(expense -> rateOf(expense).isPresent()).toList();
    }

    /** In shekels, with IB's sign: negative for the day's fees, positive for a fee IB cancelled. */
    protected BigDecimal expensesInShekels() {
        return expenses.stream()
                .map(this::inShekels)
                .flatMap(Optional::stream)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The rate of the expense's settlement day; empty in a currency without rates, or before the day's rate is out. */
    protected Optional<BigDecimal> rateOf(TaxDeductibleExpense expense) {
        ExchangeRates exchangeRates = exchangeRatesByCurrency.get(expense.currency());
        if (exchangeRates == null) {
            return Optional.empty();
        }
        return exchangeRates.rateForSettlementDay(expense.settlementDay());
    }

    /** One expense in shekels, with IB's sign; empty while its rate isn't known — then it waits, out of the entry. */
    protected Optional<BigDecimal> inShekels(TaxDeductibleExpense expense) {
        return rateOf(expense).map(rate -> expense.amount().multiply(rate));
    }

    /** The computed sales' taxable gains and the expenses, in shekels: negative when the day lost. */
    protected BigDecimal taxableGainOfTheDay() {
        BigDecimal taxableGainOfTheSales = computedSales().stream()
                .map(SaleTaxableGain::taxableGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return taxableGainOfTheSales.add(expensesInShekels());
    }

    /**
     * 25% of the day's taxable gain, in agorot: positive — "tax to pay" —, negative — a "tax shield". Rounded half up, an
     * assumption the confirmations of session 4 will check against Phoenix's entries.
     */
    protected BigDecimal taxAmount() {
        return taxableGainOfTheDay().multiply(CAPITAL_GAINS_TAX_RATE)
                .setScale(AGOROT_DECIMAL_PLACES, RoundingMode.HALF_UP);
    }
}
