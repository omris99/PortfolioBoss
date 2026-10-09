package portfolioboss.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/** Pure computation — no Spring, no database: how a holding's quantity, value and cost divide between the investors. */
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

    /** INVESTORS_TODO.md's example: 39 NVDA at 180 that cost IB 120 each — 7,020 / 4,680 in all. */
    @Test
    void eachInvestorsPartIsWorthTheirSharesAtIbsPriceAndTheyAddUpToIbsFigures() {
        PositionTrades nvidia = nvidia(39, buy(ACCOUNT_OWNER_ID, "15"), buy(AVI_ID, "24"));

        Map<Long, InvestorPart> parts = nvidia.partsByInvestor(ACCOUNT_OWNER_ID);

        assertThat(parts).containsOnlyKeys(ACCOUNT_OWNER_ID, AVI_ID);
        InvestorPart avi = parts.get(AVI_ID);
        assertThat(avi.quantity()).isEqualByComparingTo("24");
        assertThat(avi.sharesValue()).isEqualByComparingTo("4320");
        assertThat(avi.sharesCost()).isEqualByComparingTo("2880");
        assertThat(avi.unrealizedPnl()).isEqualByComparingTo("1440");
        assertThat(avi.unrealizedPnlPercent()).isEqualByComparingTo("50");
        InvestorPart accountOwner = parts.get(ACCOUNT_OWNER_ID);
        assertThat(accountOwner.quantity()).isEqualByComparingTo("15");
        assertThat(accountOwner.sharesValue()).isEqualByComparingTo("2700");
        assertThat(accountOwner.sharesCost()).isEqualByComparingTo("1800");
        assertThat(accountOwner.unrealizedPnl()).isEqualByComparingTo("900");
        assertThat(avi.sharesValue().add(accountOwner.sharesValue())).isEqualByComparingTo("7020");
        assertThat(avi.sharesCost().add(accountOwner.sharesCost())).isEqualByComparingTo("4680");
    }

    /** Their own 15 at 100 would cost 1,500; IB says the holding cost 4,680, and 2,880 of it is Avi's. */
    @Test
    void theAccountOwnersPartIsIbsLessTheOthersWhateverTheirOwnTradesSay() {
        PositionTrades nvidia = nvidia(39, buyAt(ACCOUNT_OWNER_ID, "15", "100"), buy(AVI_ID, "24"));

        InvestorPart accountOwner = nvidia.partsByInvestor(ACCOUNT_OWNER_ID).get(ACCOUNT_OWNER_ID);

        assertThat(accountOwner.sharesCost()).isEqualByComparingTo("1800");
        assertThat(accountOwner.unrealizedPnlPercent()).isEqualByComparingTo("50");
    }

    @Test
    void aBuyWithoutAPriceLeavesItsCostUnknownAndTheAccountOwnersToo() {
        PositionTrades nvidia = nvidia(39, buy(ACCOUNT_OWNER_ID, "15"), buyAt(AVI_ID, "24", null));

        Map<Long, InvestorPart> parts = nvidia.partsByInvestor(ACCOUNT_OWNER_ID);

        assertThat(parts.get(AVI_ID).sharesValue()).isEqualByComparingTo("4320");   // the quantity is known, and IB's price
        assertThat(parts.get(AVI_ID).sharesCost()).isNull();
        assertThat(parts.get(AVI_ID).unrealizedPnl()).isNull();
        assertThat(parts.get(AVI_ID).unrealizedPnlPercent()).isNull();
        assertThat(parts.get(ACCOUNT_OWNER_ID).sharesValue()).isEqualByComparingTo("2700");
        assertThat(parts.get(ACCOUNT_OWNER_ID).sharesCost()).isNull();
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

    /** At 120, IB's average cost. */
    private TradeFact buy(long investorId, String quantity) {
        return buyAt(investorId, quantity, "120");
    }

    private TradeFact buyAt(long investorId, String quantity, String price) {
        return trade(investorId, TradeSide.BUY, quantity, price);
    }

    private TradeFact sell(long investorId, String quantity) {
        return trade(investorId, TradeSide.SELL, quantity, "120");
    }

    /** Dated by the order a test creates them: a sell after the buy before it. No commission. */
    private TradeFact trade(long investorId, TradeSide side, String quantity, String price) {
        long tradeId = nextTradeId++;
        BigDecimal priceOrNull = price == null ? null : new BigDecimal(price);
        return new TradeFact(tradeId, investorId, LocalDate.of(2026, 1, 1).plusDays(tradeId), side,
                new BigDecimal(quantity), priceOrNull, BigDecimal.ZERO);
    }
}
