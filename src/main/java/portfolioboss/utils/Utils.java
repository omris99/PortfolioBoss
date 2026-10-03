package portfolioboss.utils;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Small helpers shared by more than one class or layer of the app.
 */
public final class Utils {

    private static final BigDecimal COMMISSION_PER_SHARE = new BigDecimal("0.01");
    private static final BigDecimal MIN_COMMISSION_PER_ORDER = new BigDecimal("5");

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
     * The commission a buy or sell of {@code quantity} shares is charged when none was entered: 1 cent a share, with
     * a $5 minimum — 100 shares cost $5, 600 shares $6. Rounded to the cent. The same rule as IBBot's
     * {@code Utils.calculateOrderCommission}; {@code V2__closed_positions.sql} applied it to the trades entered before.
     */
    public static BigDecimal calculateOrderCommission(BigDecimal quantity) {
        return quantity.multiply(COMMISSION_PER_SHARE)
                .max(MIN_COMMISSION_PER_ORDER)
                .setScale(2, RoundingMode.HALF_UP);
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
