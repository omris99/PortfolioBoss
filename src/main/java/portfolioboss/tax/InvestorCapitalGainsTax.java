package portfolioboss.tax;

import portfolioboss.calculation.tax.CapitalGainsTaxCalculator;

/**
 * The capital gains tax of one investor other than the account owner, from the trades typed in for them alone
 * (CAPITAL_GAINS_TAX_TODO.md, decision 6) — in shekels, what Phoenix took for them; it is not converted to dollars and
 * doesn't touch their cash.
 *
 * @param investorId   the investor
 * @param investorName their name, for the console
 * @param tax          their tax, year by year
 */
public record InvestorCapitalGainsTax(long investorId, String investorName, CapitalGainsTaxCalculator tax) {
}
