package portfolioboss.api.tax.response;

import java.util.List;

/**
 * The capital gains tax of one investor other than the account owner, from the trades typed in for them alone (decision
 * 6) — in shekels, shown and never confirmed.
 *
 * @param investorId   the investor
 * @param investorName their name
 * @param taxYears     their years, the earliest first, as calculated
 */
public record InvestorTaxResponse(long investorId, String investorName, List<TaxYearResponse> taxYears) {
}
