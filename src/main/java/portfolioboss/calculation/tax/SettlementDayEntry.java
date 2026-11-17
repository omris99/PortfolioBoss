package portfolioboss.calculation.tax;

import portfolioboss.exchangerates.ExchangeRates;
import portfolioboss.model.tax.SaleNotComputedReason;
import portfolioboss.model.tax.SettlementDayStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

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
public record SettlementDayEntry(LocalDate settlementDay, List<SaleTaxableGain> sales, List<TaxDeductibleExpense> expenses,
                          Map<String, ExchangeRates> exchangeRatesByCurrency) {

    /** 25%, without the surtax ("מס יסף") — decision 2. */
    private static final BigDecimal CAPITAL_GAINS_TAX_RATE = new BigDecimal("0.25");

    /** Phoenix's entries are in agorot. */
    private static final int AGOROT_DECIMAL_PLACES = 2;

    /** The reasons no later sync will change: the amount Phoenix recorded is typed in instead. */
    private static final Set<SaleNotComputedReason> SALE_REASONS_THAT_CANNOT_BE_CALCULATED = EnumSet.of(
            SaleNotComputedReason.CORPORATE_ACTION_NOT_SUPPORTED, SaleNotComputedReason.COMMISSION_IN_ANOTHER_CURRENCY,
            SaleNotComputedReason.NO_RATE_FOR_THE_CURRENCY);

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
    public Optional<BigDecimal> rateOf(TaxDeductibleExpense expense) {
        ExchangeRates exchangeRates = exchangeRatesByCurrency.get(expense.currency());
        if (exchangeRates == null) {
            return Optional.empty();
        }
        return exchangeRates.rateForSettlementDay(expense.settlementDay());
    }

    /** One expense in shekels, with IB's sign; empty while its rate isn't known — then it waits, out of the entry. */
    public Optional<BigDecimal> inShekels(TaxDeductibleExpense expense) {
        return rateOf(expense).map(rate -> expense.amount().multiply(rate));
    }

    /** The computed sales' taxable gains and the expenses, in shekels: negative when the day lost. */
    public BigDecimal taxableGainOfTheDay() {
        BigDecimal taxableGainOfTheSales = computedSales().stream()
                .map(SaleTaxableGain::taxableGain)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return taxableGainOfTheSales.add(expensesInShekels());
    }

    /**
     * 25% of the day's taxable gain, in agorot: positive — "tax to pay" —, negative — a "tax shield". Rounded half up, an
     * assumption the confirmations of session 4 will check against Phoenix's entries.
     */
    public BigDecimal taxAmount() {
        return taxableGainOfTheDay().multiply(CAPITAL_GAINS_TAX_RATE)
                .setScale(AGOROT_DECIMAL_PLACES, RoundingMode.HALF_UP);
    }

    /**
     * What the day needs, from its calculation alone — its confirmation is {@link SettlementDayWithItsConfirmation}'s. The
     * first that holds: a sale sold beyond its buys, a sale that can never be calculated, a sale or a fee waiting for its
     * rate, a tax amount of 0; otherwise the day is calculated.
     */
    public SettlementDayStatus status() {
        if (hasASaleNotComputedBecause(SaleNotComputedReason.SOLD_MORE_THAN_BOUGHT)) {
            return SettlementDayStatus.SOLD_MORE_THAN_BOUGHT;
        }
        if (hasASaleThatCannotBeCalculated()) {
            return SettlementDayStatus.CANNOT_BE_CALCULATED;
        }
        if (hasASaleNotComputedBecause(SaleNotComputedReason.RATE_NOT_PUBLISHED_YET) || hasAnExpenseWaitingForItsRate()) {
            return SettlementDayStatus.WAITING_FOR_A_RATE;
        }
        if (taxAmount().signum() == 0) {
            return SettlementDayStatus.CALCULATED_TO_ZERO;
        }
        return SettlementDayStatus.CALCULATED;
    }

    private boolean hasASaleNotComputedBecause(SaleNotComputedReason reason) {
        return salesNotComputed().stream().anyMatch(sale -> sale.notComputedReason() == reason);
    }

    private boolean hasASaleThatCannotBeCalculated() {
        return salesNotComputed().stream()
                .anyMatch(sale -> SALE_REASONS_THAT_CANNOT_BE_CALCULATED.contains(sale.notComputedReason()));
    }

    private boolean hasAnExpenseWaitingForItsRate() {
        return expensesWithTheirRate().size() < expenses.size();
    }
}
