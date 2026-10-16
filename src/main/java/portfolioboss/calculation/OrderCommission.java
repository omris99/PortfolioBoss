package portfolioboss.calculation;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The commission a buy or sell of {@code quantity} shares is charged when none was entered: 1 cent a share, with a $5
 * minimum — 100 shares cost $5, 600 shares $6. Rounded to the cent. The same rule as IBBot's
 * {@code Utils.calculateOrderCommission}; {@code V2__closed_positions.sql} applied it to the trades entered before.
 */
public final class OrderCommission {

    private static final BigDecimal COMMISSION_PER_SHARE = new BigDecimal("0.01");
    private static final BigDecimal MIN_COMMISSION_PER_ORDER = new BigDecimal("5");

    public static BigDecimal defaultFor(BigDecimal quantity) {
        return quantity.multiply(COMMISSION_PER_SHARE)
                .max(MIN_COMMISSION_PER_ORDER)
                .setScale(2, RoundingMode.HALF_UP);
    }

    /** The commission entered for an order — 0 included — or, when none was, the default for this many shares. */
    public static BigDecimal orDefault(BigDecimal enteredCommission, BigDecimal quantity) {
        return enteredCommission != null ? enteredCommission : defaultFor(quantity);
    }

    private OrderCommission() {
    }
}
