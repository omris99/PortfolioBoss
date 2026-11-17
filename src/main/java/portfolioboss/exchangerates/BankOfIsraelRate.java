package portfolioboss.exchangerates;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One representative rate the Bank of Israel published: what one unit of a currency was worth in shekels that day —
 * "on 2025-10-03 a dollar was 3.315 shekels".
 *
 * @param rateDate the day of the rate
 * @param rate     shekels for one unit of the currency
 */
public record BankOfIsraelRate(LocalDate rateDate, BigDecimal rate) {

    /**
     * The longest the Bank of Israel went without publishing a rate, between two published days: 7 days, from 31.03 to
     * 07.04.2026 (Pesach), and from 04.04 to 11.04.2023 (checked 2026-11-15 over the dollar's rates of 2020–2026). It
     * publishes Monday to Friday, except on Israeli and international holidays (1.1, Good Friday, 25–26.12, …).
     */
    public static final int LONGEST_GAP_BETWEEN_RATES_IN_DAYS = 7;
}
