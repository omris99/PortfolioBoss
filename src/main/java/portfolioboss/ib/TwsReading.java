package portfolioboss.ib;

import java.util.List;
import java.util.Map;

/**
 * What one {@code ./run.sh} connection read: the portfolio, the daily closes of its holdings, of SPY and of the stocks
 * the scan kept (maybe none), and the scan — {@code null} when none was due or none answered.
 */
public record TwsReading(PortfolioSnapshot snapshot, Map<Integer, List<DailyClose>> dailyCloses, Scan scan) {
}
