package portfolioboss.domain;

import org.junit.jupiter.api.Test;
import portfolioboss.db.TradeSide;
import portfolioboss.model.Holding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure computation — no Spring, no database: how a holding's quantity divides between the investors. */
class PositionTradesTest {

    private static final long ACCOUNT_OWNER_ID = 1;
    private static final long AVI_ID = 2;

    @Test
    void anotherInvestorHoldsWhatTheirTradesAddUpToAndTheAccountOwnerTheRest() {
        PositionTrades nvidia = nvidia(39, buy(ACCOUNT_OWNER_ID, "15"), buy(AVI_ID, "24"));

        assertThat(quantitiesOf(nvidia)).containsExactly(Map.entry(ACCOUNT_OWNER_ID, 15), Map.entry(AVI_ID, 24));
    }

    /** IB is the source of truth for the quantity: the account owner's own trades don't change their part. */
    @Test
    void theAccountOwnerHoldsWhatIbReportsWhateverTheirOwnTradesSay() {
        PositionTrades nvidia = nvidia(39, buy(ACCOUNT_OWNER_ID, "10"), buy(AVI_ID, "24"));

        assertThat(quantitiesOf(nvidia)).containsExactly(Map.entry(ACCOUNT_OWNER_ID, 15), Map.entry(AVI_ID, 24));
    }

    @Test
    void withNoTradesEverythingIsTheAccountOwners() {
        assertThat(quantitiesOf(nvidia(10))).containsExactly(Map.entry(ACCOUNT_OWNER_ID, 10));
    }

    @Test
    void anInvestorWhoHoldsNothingIsLeftOut() {
        PositionTrades nvidia = nvidia(24, buy(ACCOUNT_OWNER_ID, "15"), sell(ACCOUNT_OWNER_ID, "15"),
                buy(AVI_ID, "24"));

        assertThat(quantitiesOf(nvidia)).containsExactly(Map.entry(AVI_ID, 24));
    }

    /** The sum still equals IB's 20; the other investor's card points at the trades to fix. */
    @Test
    void moreSharesThanIbReportsLeaveTheAccountOwnerBelowZero() {
        PositionTrades nvidia = nvidia(20, buy(AVI_ID, "24"));

        assertThat(quantitiesOf(nvidia)).containsExactly(Map.entry(ACCOUNT_OWNER_ID, -4), Map.entry(AVI_ID, 24));
    }

    @Test
    void eachInvestorsHistoryIsMadeOfTheirOwnTradesOnly() {
        PositionTrades nvidia = nvidia(24, buy(ACCOUNT_OWNER_ID, "15"), sell(ACCOUNT_OWNER_ID, "15"),
                buy(AVI_ID, "24"));

        assertThat(nvidia.investorIds()).containsExactly(ACCOUNT_OWNER_ID, AVI_ID);
        assertThat(nvidia.historyOf(ACCOUNT_OWNER_ID).closedPositions()).hasSize(1);
        assertThat(nvidia.historyOf(AVI_ID).closedPositions()).isEmpty();
        assertThat(nvidia.quantityOf(AVI_ID)).isEqualByComparingTo("24");
    }

    @Test
    void aManualPositionHasNoSharesAtIb() {
        PositionTrades manualPosition = new PositionTrades("AAPL", "USD", null, List.of());

        assertThat(manualPosition.isHolding()).isFalse();
        assertThat(manualPosition.ibQuantity()).isEqualByComparingTo("0");
        assertThat(manualPosition.ibMarketPrice()).isNull();
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private long nextTradeId = 1;

    /** As whole numbers, so the expected maps read plainly. */
    private Map<Long, Integer> quantitiesOf(PositionTrades position) {
        Map<Long, Integer> quantities = new TreeMap<>();
        position.quantitiesByInvestor(ACCOUNT_OWNER_ID)
                .forEach((investorId, quantity) -> quantities.put(investorId, quantity.intValueExact()));
        return quantities;
    }

    private PositionTrades nvidia(double ibPosition, TradeFact... trades) {
        Holding ibHolding = new Holding("NVDA", 4815747, "STK", "USD", ibPosition, 120.0, 180.0, ibPosition * 180.0,
                ibPosition * 60.0, 0.0, "U1234567");
        return new PositionTrades("NVDA", "USD", ibHolding, List.of(trades));
    }

    private TradeFact buy(long investorId, String quantity) {
        return trade(investorId, TradeSide.BUY, quantity);
    }

    private TradeFact sell(long investorId, String quantity) {
        return trade(investorId, TradeSide.SELL, quantity);
    }

    /** Dated by the order a test creates them: a sell after the buy before it. */
    private TradeFact trade(long investorId, TradeSide side, String quantity) {
        long tradeId = nextTradeId++;
        return new TradeFact(tradeId, investorId, LocalDate.of(2026, 1, 1).plusDays(tradeId), side,
                new BigDecimal(quantity), new BigDecimal("120"), BigDecimal.ZERO);
    }
}
