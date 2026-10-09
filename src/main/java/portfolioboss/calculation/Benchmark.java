package portfolioboss.calculation;

/**
 * The market a holding's momentum is measured against: SPY, the S&amp;P 500 ETF. Its daily closes are requested on
 * every connection whether or not the account holds SPY — holding it or selling it changes nothing.
 */
public final class Benchmark {

    /**
     * IB's contract id for SPY: fixed by IB, the same for every account. Checked once against the SPY holding in the
     * real database (07.10.2026), but it does not depend on that holding.
     */
    public static final int SPY_CON_ID = 756733;

    private Benchmark() {
    }
}
