package portfolioboss.ib;

import com.ib.client.Bar;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feeds {@link PortfolioWrapper} the callbacks IB would send, so the way it collects positions and daily closes is
 * tested without a TWS. No socket is needed: the wrapper only uses its client to unsubscribe when one is set.
 */
class PortfolioWrapperTest {

    private static final String ACCOUNT = "U1234567";
    private static final int APPLE_CON_ID = 265598;
    private static final int SPY_CON_ID = 756733;

    private final PortfolioWrapper wrapper = new PortfolioWrapper();

    @Test
    void keepsTwoPositionsThatShareASymbolApartByContractId() {
        reportPosition("BRK", 1001, 10);
        reportPosition("BRK", 1002, 5);
        wrapper.accountDownloadEnd(ACCOUNT);

        assertThat(wrapper.snapshot().holdings())
                .extracting(Holding::conId)
                .containsExactly(1001, 1002);
    }

    @Test
    void dropsAPositionIbReportsAsZero() {
        reportPosition("AAPL", 265598, 10);
        reportPosition("AAPL", 265598, 0);
        wrapper.accountDownloadEnd(ACCOUNT);

        assertThat(wrapper.snapshot().holdings()).isEmpty();
    }

    @Test
    void hasNoSnapshotUntilTheDownloadEnds() {
        reportPosition("AAPL", 265598, 10);

        assertThat(wrapper.snapshot()).isNull();
    }

    // ── daily closes ────────────────────────────────────────────────────────────────────────────

    @Test
    void collectsTheDailyClosesOfEachRequestUnderItsContractId() throws InterruptedException {
        wrapper.expectDailyCloses(Map.of(1000, APPLE_CON_ID, 1001, SPY_CON_ID));

        wrapper.historicalData(1000, bar("20261005", 333.5));
        wrapper.historicalData(1000, bar("20261006", 335.0));
        wrapper.historicalDataEnd(1000, "20251006", "20261006");
        wrapper.historicalData(1001, bar("20261006", 671.2));
        wrapper.historicalDataEnd(1001, "20251006", "20261006");

        assertThat(wrapper.awaitDailyCloses(0, TimeUnit.SECONDS)).isTrue();
        assertThat(wrapper.dailyCloses())
                .containsOnlyKeys(APPLE_CON_ID, SPY_CON_ID)
                .containsEntry(APPLE_CON_ID, List.of(new DailyClose(LocalDate.of(2026, 10, 5), 333.5),
                        new DailyClose(LocalDate.of(2026, 10, 6), 335.0)))
                .containsEntry(SPY_CON_ID, List.of(new DailyClose(LocalDate.of(2026, 10, 6), 671.2)));
    }

    /** E.g. no market data permission for one contract: that request ends without closes, the others carry on. */
    @Test
    void anErrorEndsThatContractsRequestWithoutCloses() throws InterruptedException {
        wrapper.expectDailyCloses(Map.of(1000, APPLE_CON_ID, 1001, SPY_CON_ID));

        wrapper.historicalData(1000, bar("20261006", 335.0));
        wrapper.historicalDataEnd(1000, "20251006", "20261006");
        wrapper.error(1001, 0L, 162, "Historical Market Data Service error message: no market data permissions", "");

        assertThat(wrapper.awaitDailyCloses(0, TimeUnit.SECONDS)).isTrue();
        assertThat(wrapper.dailyCloses()).containsOnlyKeys(APPLE_CON_ID);
    }

    /** What a timeout sees: a request that has not ended is left out, not served half-received. */
    @Test
    void leavesOutARequestThatHasNotEnded() throws InterruptedException {
        wrapper.expectDailyCloses(Map.of(1000, APPLE_CON_ID, 1001, SPY_CON_ID));

        wrapper.historicalData(1000, bar("20261006", 335.0));
        wrapper.historicalDataEnd(1000, "20251006", "20261006");
        wrapper.historicalData(1001, bar("20261005", 670.0));   // SPY's closes are still coming in

        assertThat(wrapper.awaitDailyCloses(0, TimeUnit.SECONDS)).isFalse();
        assertThat(wrapper.dailyCloses()).containsOnlyKeys(APPLE_CON_ID);
    }

    @Test
    void skipsABarWhoseDateCannotBeRead() {
        wrapper.expectDailyCloses(Map.of(1000, APPLE_CON_ID));

        wrapper.historicalData(1000, bar("not a date", 1.0));
        wrapper.historicalData(1000, bar("20261006 16:00:00 US/Eastern", 335.0));
        wrapper.historicalDataEnd(1000, "20251006", "20261006");

        assertThat(wrapper.dailyCloses().get(APPLE_CON_ID))
                .containsExactly(new DailyClose(LocalDate.of(2026, 10, 6), 335.0));
    }

    private Bar bar(String time, double close) {
        return new Bar(time, close, close, close, close, Decimal.get(0), 0, Decimal.get(0));
    }

    private void reportPosition(String symbol, int conId, double quantity) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.conid(conId);
        contract.secType("STK");
        contract.currency("USD");
        wrapper.updatePortfolio(contract, Decimal.get(quantity), 200.0, quantity * 200.0, 150.0, 0.0, 0.0, ACCOUNT);
    }
}
