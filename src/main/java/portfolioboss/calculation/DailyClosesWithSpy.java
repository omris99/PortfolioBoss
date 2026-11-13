package portfolioboss.calculation;

import portfolioboss.ib.Benchmark;
import portfolioboss.ib.DailyClose;

import java.util.List;
import java.util.Map;

/**
 * The stored daily closes of some stocks and of SPY, by contract id — what a stock's momentum needs, since it is measured
 * against SPY. Read in one query ({@code DailyCloseRepository.dailyClosesWithSpy}) by the portfolio, the Recommendations
 * page and the alerts.
 *
 * @param dailyClosesByConId each contract's closes in date order; a contract without any is missing
 */
public record DailyClosesWithSpy(Map<Integer, List<DailyClose>> dailyClosesByConId) {

    /** Empty for a contract with no stored closes. */
    public List<DailyClose> closesOf(int conId) {
        return dailyClosesByConId.getOrDefault(conId, List.of());
    }

    /** {@code null} for a contract with no stored closes. */
    public Momentum momentumOf(int conId) {
        return Momentum.of(closesOf(conId), closesOf(Benchmark.SPY_CON_ID));
    }
}
