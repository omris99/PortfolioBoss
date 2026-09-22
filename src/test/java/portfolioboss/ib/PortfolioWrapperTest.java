package portfolioboss.ib;

import com.ib.client.Contract;
import com.ib.client.Decimal;
import org.junit.jupiter.api.Test;
import portfolioboss.model.Holding;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Feeds {@link PortfolioWrapper} the callbacks IB would send, so the way it collects positions is tested
 * without a TWS. No socket is needed: the wrapper only uses its client to unsubscribe when one is set.
 */
class PortfolioWrapperTest {

    private static final String ACCOUNT = "U1234567";

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

    private void reportPosition(String symbol, int conId, double quantity) {
        Contract contract = new Contract();
        contract.symbol(symbol);
        contract.conid(conId);
        contract.secType("STK");
        contract.currency("USD");
        wrapper.updatePortfolio(contract, Decimal.get(quantity), 200.0, quantity * 200.0, 150.0, 0.0, 0.0, ACCOUNT);
    }
}
