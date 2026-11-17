package portfolioboss.ib;

/**
 * One row of one scan, as IB's {@code scannerData} sends it.
 *
 * @param scanCode which scan found the stock
 * @param rank     where the scan placed it: 0 is the top
 * @param conId    IB's contract id
 */
public record ScanRow(String scanCode, int rank, int conId, String symbol) {
}
