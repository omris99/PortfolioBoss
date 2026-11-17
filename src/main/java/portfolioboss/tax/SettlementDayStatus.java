package portfolioboss.tax;

/**
 * What a settlement day needs on the Taxes page (CAPITAL_GAINS_TAX_TODO.md, session 5), worked out from its sales, its
 * expenses and its confirmation — so that which days wait for a confirmation is decided here, in one place, and the page
 * only shows it. Public: it goes into {@code GET /api/taxes} as it is, so its names are JSON values too.
 */
public enum SettlementDayStatus {

    /**
     * Calculated, and not 0. On the account's tab the day waits for the user to confirm what Phoenix recorded; on an
     * investor's tab there is nothing to confirm (decision 7).
     */
    CALCULATED,

    /** The user confirmed what Phoenix recorded for the day: that amount is final (decision 7). The account's days only. */
    CONFIRMED,

    /**
     * Calculated as 0 — a gain one way and a loss the other (decision 11), or gains and losses that cancel out: Phoenix
     * records nothing, so there is nothing to confirm.
     */
    CALCULATED_TO_ZERO,

    /** A sale or a fee of the day waits for the Bank of Israel's rate of a settlement day: a later sync calculates it. */
    WAITING_FOR_A_RATE,

    /**
     * A sale of the day can never be calculated — in a currency without rates, through a corporate action not supported,
     * or with its commission in another currency. On the account's tab the amount Phoenix recorded is typed in.
     */
    CANNOT_BE_CALCULATED,

    /** A sale of the day sold more shares than were bought before it: a trade is missing. */
    SOLD_MORE_THAN_BOUGHT
}
