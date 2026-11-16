package portfolioboss.tax.response;

import java.time.LocalDate;
import java.util.List;

/**
 * What {@code GET /api/taxes} answers: the capital gains tax of the account and of every other investor
 * (CAPITAL_GAINS_TAX_TODO.md, session 4). The component names are JSON keys.
 *
 * @param lastSyncDay       the day of the last sync — the calculation's "today": which months are settled
 * @param accountTaxYears   the account's years, the earliest first — what Phoenix withholds, as recorded: a confirmed day
 *                          counts with its confirmed amount, every other day with its calculated one
 * @param otherInvestorsTax every investor other than the account owner, in id order, from their own trades (decision 6)
 */
public record TaxesResponse(LocalDate lastSyncDay, List<TaxYearResponse> accountTaxYears,
                            List<InvestorTaxResponse> otherInvestorsTax) {
}
