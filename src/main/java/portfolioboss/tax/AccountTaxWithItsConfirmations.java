package portfolioboss.tax;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * The account's tax, year by year, with what the user confirmed (CAPITAL_GAINS_TAX_TODO.md, session 4): each year's
 * confirmations go to the year of their settlement day, as the calculation's entries do (decision 16).
 *
 * @param accountTax    the account's tax as calculated, from IB's Flex reports
 * @param confirmations every confirmation stored
 */
record AccountTaxWithItsConfirmations(CapitalGainsTaxCalculator accountTax, List<TaxConfirmation> confirmations) {

    /**
     * Every year with a calculated entry or a confirmation, the earliest first: a confirmation is never left out, even in
     * a year the calculation no longer has a day of (see {@link TaxYearWithItsConfirmations#settlementDays}).
     */
    protected List<TaxYearWithItsConfirmations> taxYears() {
        Map<Integer, TaxYearCalculator> calculatedYearsByYear = new TreeMap<>();
        for (TaxYearCalculator taxYear : accountTax.taxYears()) {
            calculatedYearsByYear.put(taxYear.year(), taxYear);
        }
        Map<Integer, List<TaxConfirmation>> confirmationsByYear = new TreeMap<>();
        for (TaxConfirmation confirmation : confirmations) {
            confirmationsByYear.computeIfAbsent(accountTax.taxYearOf(confirmation.settlementDay()),
                    year -> new ArrayList<>()).add(confirmation);
        }

        TreeSet<Integer> yearsWithAnEntryOrAConfirmation = new TreeSet<>(calculatedYearsByYear.keySet());
        yearsWithAnEntryOrAConfirmation.addAll(confirmationsByYear.keySet());
        return yearsWithAnEntryOrAConfirmation.stream()
                .map(year -> new TaxYearWithItsConfirmations(
                        calculatedYearsByYear.getOrDefault(year, yearWithoutAnEntry(year)),
                        confirmationsByYear.getOrDefault(year, List.of())))
                .toList();
    }

    private TaxYearCalculator yearWithoutAnEntry(int year) {
        return new TaxYearCalculator(year, List.of(), accountTax.lastSyncDay());
    }
}
