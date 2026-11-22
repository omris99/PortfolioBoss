package portfolioboss.calculation.tax;

/**
 * Why a corporate action of a split's type in IB's Flex reports was not understood as a split
 * ({@link SplitsInFlexCorporateActions}): it is not stored, and the console says why, on every sync.
 */
public enum WhySplitNotUnderstood {

    ROWS_OF_ANOTHER_KIND("its rows are of another kind than a split's: one row of the stock, or two — the old"
            + " contract's shares taken away and the new contract's added, at the same time"),
    IT_BRINGS_MONEY("it brings money, cash for a fraction of a share say"),
    NO_SPLIT_IN_IBS_WORDS("IB's words for it don't say \"SPLIT n FOR m\""),
    SHARES_DONT_MATCH("the shares it moves don't match IB's words and the shares the account's trades held before"
            + " it — a trade may be missing");

    private final String description;

    WhySplitNotUnderstood(String description) {
        this.description = description;
    }

    /** In English, for the console line. */
    public String description() {
        return description;
    }
}
