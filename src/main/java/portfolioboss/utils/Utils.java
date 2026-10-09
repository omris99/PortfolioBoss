package portfolioboss.utils;

import java.math.BigDecimal;

/**
 * Small helpers shared by more than one class or layer of the app.
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

    /**
     * An IB figure as an exact decimal, so it can be added to the amounts entered by hand ({@code BigDecimal}):
     * {@code null} when IB did not report it, whether it comes as {@code null} (a stored column) or {@code NaN}.
     */
    public static BigDecimal decimalOrNull(Double value) {
        return value == null || !Double.isFinite(value) ? null : BigDecimal.valueOf(value);
    }

    /** Surrounding spaces are dropped, and text that is then empty becomes {@code null}: nothing was entered. */
    public static String trimmedOrNull(String typedText) {
        if (typedText == null || typedText.isBlank()) {
            return null;
        }
        return typedText.strip();
    }

    private Utils() {
    }
}
