package portfolioboss.ib;

import java.util.List;
import java.util.Set;

/**
 * The scans for hot stocks and their filters (RECOMMENDATIONS_TODO.md, decisions 2 and 3), adapted from IBBot's
 * {@code StockScanner} to long-term investing: no penny stocks, no small companies that move a lot on little money, no
 * funds. IB returns at most 50 rows a scan, so two scans are merged — the stocks that rose most over 3 and over 6 months,
 * about 70 different ones in session 0. They also catch a stock that has pulled back, which a scan for stocks near their
 * high would miss.
 */
final class HotStockScan {

    protected static final List<String> SCAN_CODES = List.of("SCAN_histPerfRatio3m_DESC", "SCAN_histPerfRatio6m_DESC");
    protected static final int MOST_ROWS_PER_SCAN = 50;

    /** Stocks — not options or futures — on the major US exchanges. */
    protected static final String INSTRUMENT = "STK";
    protected static final String LOCATION = "STK.US.MAJOR";
    /** Companies only: no ETFs. */
    protected static final String STOCK_TYPE = "CORP";

    // The filters go with the request, under the names of IB's scanner parameters (checked in session 0).
    protected static final int LOWEST_PRICE_DOLLARS = 15;
    protected static final int LOWEST_MARKET_CAP_MILLIONS = 2_000;
    protected static final int LOWEST_AVERAGE_DAILY_VOLUME = 1_000_000;

    /**
     * Checked against the primary exchange of the contract details ({@code primaryExch}), since {@code STK.US.MAJOR} also
     * covers other exchanges. Not against the scan row's {@code marketName}, which for many NYSE stocks is the symbol
     * itself (IBBot's {@code StockScanner} works around that). The stocks left out are printed on every run, with what IB
     * sent: a NYSE or NASDAQ stock among them means this check is wrong.
     */
    protected static final Set<String> ACCEPTED_EXCHANGES = Set.of("NYSE", "NASDAQ");

    private HotStockScan() {
    }
}
