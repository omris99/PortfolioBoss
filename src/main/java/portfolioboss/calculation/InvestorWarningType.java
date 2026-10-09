package portfolioboss.calculation;

/**
 * What an {@link InvestorWarning} is about. The names reach the JSON as they are, so they must stay stable — like
 * {@link HoldingWarningType}.
 */
public enum InvestorWarningType {

    /** A trade of theirs in USD has no price, so their cash is unknown. */
    TRADES_WITHOUT_PRICE,

    /** A trade of theirs is in another currency, and their cash counts USD only. */
    TRADES_NOT_IN_USD,

    /** Their cash comes to less than zero: a deposit is probably missing. */
    NEGATIVE_CASH,

    /** In one holding their trades add up to more shares than IB reports for the whole of it. */
    MORE_SHARES_THAN_IB,

    /** Closed positions of theirs without a realized P&L (a price missing, or more sold than bought) are left out of it. */
    CLOSED_POSITIONS_NOT_COUNTED
}
