package portfolioboss.tax.response;

import portfolioboss.tax.SettlementDayStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * One settlement day's entry, as Phoenix records it (decision 4): every sale settled that day and every expense of that
 * day, netted into one amount — and, on the account's days, what the user confirmed for it.
 *
 * @param settlementDay       the day
 * @param sales               the sales settled that day, computed or not
 * @param expenses            IB's fees settled that day — the account's only
 * @param taxableGainOfTheDay the computed sales' taxable gains and the expenses, in shekels: negative when the day lost
 * @param computedTaxAmount   25% of it, to the agora: positive — tax to pay —, negative — a tax shield
 * @param confirmation        what the user confirmed for the day, final; {@code null} on an account's day still waiting,
 *                            and always on an investor's day — an investor's tax is shown, not confirmed (decision 7)
 * @param status              what the day needs on the Taxes page: {@code CONFIRMED} only on the account's days
 */
public record SettlementDayResponse(LocalDate settlementDay, List<SaleResponse> sales,
                                    List<TaxDeductibleExpenseResponse> expenses, BigDecimal taxableGainOfTheDay,
                                    BigDecimal computedTaxAmount, TaxConfirmationResponse confirmation,
                                    SettlementDayStatus status) {
}
