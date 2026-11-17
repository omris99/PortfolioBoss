package portfolioboss.model.tax;

/**
 * Why a sale's taxable gain was not computed (CAPITAL_GAINS_TAX_TODO.md, session 3.3). Such a sale is left out of the
 * year's balance — never guessed —, and its tax is entered by hand on the Taxes page (session 4). The checks run in this
 * order, and a sale gets the first that applies. Public: it goes into {@code GET /api/taxes} as it is, so its names are
 * JSON values too.
 */
public enum SaleNotComputedReason {

    /**
     * The stock went through a corporate action other than the splits this code knows (a spin-off, a merger, cash for a
     * fraction of a share): its shares' cost can't be followed through it (decision 15).
     */
    CORPORATE_ACTION_NOT_SUPPORTED,

    /** One of the stock's trades was charged its commission in another currency, which can't be added to its price. */
    COMMISSION_IN_ANOTHER_CURRENCY,

    /** The sale sold more shares than the trades before it bought: a buy, a split or a transfer is missing. */
    SOLD_MORE_THAN_BOUGHT,

    /** The Bank of Israel publishes no rate for the trade's currency — the Hong Kong dollar (decision 10). */
    NO_RATE_FOR_THE_CURRENCY,

    /**
     * The rate of the sale's settlement day, or of the settlement day of a buy it took shares from, is not stored yet: the
     * day hasn't come, or its rate isn't published yet — "not computed yet", like Phoenix's 0 (decision 11).
     */
    RATE_NOT_PUBLISHED_YET
}
