package portfolioboss.model;

import java.time.Instant;
import java.util.List;

/**
 * One point-in-time read of the account: its holdings plus the two account-level figures the
 * portfolio report shows. A figure IB did not report is {@code NaN}.
 */
public record PortfolioSnapshot(
        String account,
        Instant asOf,
        double netLiquidation,
        double totalCashValue,
        List<Holding> holdings) {
}
