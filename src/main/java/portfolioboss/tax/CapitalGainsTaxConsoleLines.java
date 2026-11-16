package portfolioboss.tax;

import portfolioboss.tax.OurFifoAgainstIbsFifo.SaleWhoseGainDiffersFromIbs;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * What a sync prints of the capital gains tax (CAPITAL_GAINS_TAX_TODO.md, session 3.6) — until the Taxes page, the only
 * place to see it: a line for each of the account's tax years, a line for each year of each other investor, and how our
 * FIFO compares with IB's.
 *
 * <pre>
 * [tax] capital gains tax 2025: 1,234.56 ILS on a taxable gain of 4,938.24 ILS (22 sales)
 * [tax] capital gains tax 2026: 0.00 ILS on a taxable gain of -1,200.00 ILS, a shield of 300.00 ILS left (53 sales; not computed: 1 in a currency without exchange rates)
 * [tax] capital gains tax of Avi 2026: 120.00 ILS on a taxable gain of 480.00 ILS (1 sale)
 * [tax] our FIFO against IB's: 75 of 75 sales match
 * </pre>
 *
 * @param capitalGainsTax what {@link CapitalGainsTaxService} worked out
 */
record CapitalGainsTaxConsoleLines(CapitalGainsTaxOfTheAccountAndInvestors capitalGainsTax) {

    /** A FIFO gone wrong would differ on most sales: the first ten say enough. */
    private static final int MOST_DIFFERENCES_LISTED = 10;

    protected List<String> lines() {
        List<String> lines = new ArrayList<>();
        for (TaxYearCalculator taxYear : capitalGainsTax.accountTax().taxYears()) {
            lines.add("[tax] capital gains tax " + yearLine(taxYear));
        }
        for (InvestorCapitalGainsTax investorTax : capitalGainsTax.otherInvestorsTax()) {
            for (TaxYearCalculator taxYear : investorTax.tax().taxYears()) {
                lines.add("[tax] capital gains tax of " + investorTax.investorName() + " " + yearLine(taxYear));
            }
        }
        lines.addAll(ourFifoAgainstIbsFifoLines());
        return lines;
    }

    /** "2025: 1,234.56 ILS on a taxable gain of 4,938.24 ILS (22 sales)", with the shield left when there is one. */
    private String yearLine(TaxYearCalculator taxYear) {
        TaxYearBalance balance = taxYear.balance();
        String shieldLeft = balance.shieldLeft().signum() > 0
                ? ", a shield of " + shekels(balance.shieldLeft()) + " left"
                : "";
        return "%d: %s on a taxable gain of %s%s (%s)".formatted(taxYear.year(), shekels(balance.taxOfTheYearSoFar()),
                shekels(taxYear.taxableGain()), shieldLeft, salesCountsOf(taxYear.sales()));
    }

    /** "22 sales", or "53 sales; not computed: 1 in a currency without exchange rates". */
    private String salesCountsOf(List<SaleTaxableGain> sales) {
        Map<SaleNotComputedReason, Long> salesNotComputedByReason = new EnumMap<>(SaleNotComputedReason.class);
        for (SaleTaxableGain sale : sales) {
            if (!sale.isComputed()) {
                salesNotComputedByReason.merge(sale.notComputedReason(), 1L, Long::sum);
            }
        }
        String salesCount = countOf(sales.size(), "sale");
        if (salesNotComputedByReason.isEmpty()) {
            return salesCount;
        }
        String notComputed = salesNotComputedByReason.entrySet().stream()
                .map(reason -> reason.getValue() + " " + wordsOf(reason.getKey()))
                .collect(Collectors.joining(", "));
        return salesCount + "; not computed: " + notComputed;
    }

    /** "our FIFO against IB's: 75 of 75 sales match", then each sale that doesn't — the first ten. */
    private List<String> ourFifoAgainstIbsFifoLines() {
        OurFifoAgainstIbsFifo ourFifoAgainstIbsFifo = capitalGainsTax.ourFifoAgainstIbsFifo();
        int salesCompared = ourFifoAgainstIbsFifo.salesCompared().size();
        int salesWithoutIbsGain = ourFifoAgainstIbsFifo.accountSales().size() - salesCompared;
        List<SaleWhoseGainDiffersFromIbs> salesThatDiffer = ourFifoAgainstIbsFifo.salesWhoseGainDiffersFromIbs();

        List<String> lines = new ArrayList<>();
        String withoutIbsGain = salesWithoutIbsGain == 0
                ? ""
                : ", " + countOf(salesWithoutIbsGain, "sale") + " without IB's FIFO gain";
        lines.add("[tax] our FIFO against IB's: %d of %d sales match%s".formatted(
                salesCompared - salesThatDiffer.size(), salesCompared, withoutIbsGain));
        salesThatDiffer.stream()
                .limit(MOST_DIFFERENCES_LISTED)
                .map(this::differenceLine)
                .forEach(lines::add);
        if (salesThatDiffer.size() > MOST_DIFFERENCES_LISTED) {
            lines.add("[tax] our FIFO against IB's: and " + (salesThatDiffer.size() - MOST_DIFFERENCES_LISTED)
                    + " more");
        }
        return lines;
    }

    /** "ABCD sold on 2025-06-24 (IB trade 1000000001): ours 856.12 USD, IB's 860.00 USD". */
    private String differenceLine(SaleWhoseGainDiffersFromIbs saleThatDiffers) {
        TaxCalculationTrade sale = saleThatDiffers.fifoSale().sale();
        return "[tax] our FIFO against IB's: %s sold on %s (IB trade %s): ours %s %s, IB's %s %s".formatted(
                sale.symbol(), sale.tradeDate(), sale.tradeId(), amount(saleThatDiffers.ourFifoGain()), sale.currency(),
                amount(saleThatDiffers.ibsFifoGain()), sale.currency());
    }

    private String wordsOf(SaleNotComputedReason reason) {
        return switch (reason) {
            case CORPORATE_ACTION_NOT_SUPPORTED -> "after a corporate action not followed";
            case COMMISSION_IN_ANOTHER_CURRENCY -> "with a commission in another currency";
            case SOLD_MORE_THAN_BOUGHT -> "selling more than was bought";
            case NO_RATE_FOR_THE_CURRENCY -> "in a currency without exchange rates";
            case RATE_NOT_PUBLISHED_YET -> "whose exchange rate isn't published yet";
        };
    }

    private String shekels(BigDecimal amount) {
        return amount(amount) + " ILS";
    }

    /** Locale.ROOT: "1,234.56" on every machine, whatever its language settings. */
    private String amount(BigDecimal amount) {
        return String.format(Locale.ROOT, "%,.2f", amount);
    }

    /** "1 sale", "22 sales". */
    private String countOf(long count, String thing) {
        return count == 1 ? "1 " + thing : count + " " + thing + "s";
    }
}
