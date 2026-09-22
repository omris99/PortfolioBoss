package portfolioboss.utils;

/**
 * Small helpers shared by more than one layer of the app.
 */
public final class Utils {

    /**
     * How a figure IB did not report travels through the app. IB, and so {@code Holding} and
     * {@code PortfolioSnapshot}, use {@code NaN} for it; anything that leaves the app must say {@code null}:
     * <ul>
     *   <li>JSON has no {@code NaN} or infinity — depending on the library a writer either fails or emits the
     *       <em>string</em> {@code "NaN"}, which breaks the UI's {@code number | null} types;</li>
     *   <li>a database column holding {@code NaN} would turn every {@code sum()} over it into {@code NaN}.</li>
     * </ul>
     */
    public static Double finiteOrNull(double value) {
        return Double.isFinite(value) ? value : null;
    }

    /** The way back: a column that is {@code NULL} becomes {@code NaN} again, as {@code Holding} says "not reported". */
    public static double nanIfNull(Double value) {
        return value == null ? Double.NaN : value;
    }

    private Utils() {
    }
}
