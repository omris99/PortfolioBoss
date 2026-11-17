package portfolioboss.ib;

import java.util.List;
import java.util.Map;

/** What the Scan again button read: the scan, and the daily closes of the stocks it kept and of SPY. */
public record ScanReading(Scan scan, Map<Integer, List<DailyClose>> dailyCloses) {
}
