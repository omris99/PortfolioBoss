package portfolioboss.domain;

import org.junit.jupiter.api.Test;
import portfolioboss.db.CashMovementType;
import portfolioboss.db.TradeSide;
import portfolioboss.model.Holding;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Pure computation — no Spring, no database. Most tests start from the example of INVESTORS_TODO.md: IB reports a net
 * liquidation value of 100,000 and 40,000 in cash; NVDA is at 180; the shares are worth 60,000 and cost 50,000 (NVDA,
 * and MSFT for the rest). No commissions unless a test says so, to keep the figures round.
 */
class InvestorSummaryCalculatorTest {

    private static final long ACCOUNT_OWNER_ID = 1;
    private static final long AVI_ID = 2;
    private static final double IB_CASH = 40_000.0;
    private static final double IB_NET_LIQUIDATION = 100_000.0;

    @Test
    void theExampleOfThePlan() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(39, buy(ACCOUNT_OWNER_ID, "15", "120"), buy(AVI_ID, "24", "120")),
                        microsoft(),
                        manualPosition("AAPL", "USD", buy(ACCOUNT_OWNER_ID, "10", "100"),
                                sell(ACCOUNT_OWNER_ID, "10", "150"))),
                List.of(deposit(AVI_ID, "30000")));

        InvestorSummary avi = summaries.get(AVI_ID);
        assertThat(avi.depositsMinusWithdrawals()).isEqualByComparingTo("30000");
        assertThat(avi.cash()).isEqualByComparingTo("27120");
        assertThat(avi.sharesValue()).isEqualByComparingTo("4320");
        assertThat(avi.totalValue()).isEqualByComparingTo("31440");
        assertThat(avi.sharesCost()).isEqualByComparingTo("2880");
        assertThat(avi.unrealizedPnl()).isEqualByComparingTo("1440");
        assertThat(avi.unrealizedPnlPercent()).isEqualByComparingTo("50");
        assertThat(avi.realizedPnlByCurrency()).isEmpty();
        assertThat(avi.totalPnl()).isEqualByComparingTo("1440");
        assertThat(avi.warnings()).isEmpty();

        InvestorSummary accountOwner = summaries.get(ACCOUNT_OWNER_ID);
        assertThat(accountOwner.depositsMinusWithdrawals()).isNull();
        assertThat(accountOwner.cash()).isEqualByComparingTo("12880");
        assertThat(accountOwner.sharesValue()).isEqualByComparingTo("55680");
        assertThat(accountOwner.totalValue()).isEqualByComparingTo("68560");
        assertThat(accountOwner.sharesCost()).isEqualByComparingTo("47120");
        assertThat(accountOwner.unrealizedPnl()).isEqualByComparingTo("8560");
        assertThat(accountOwner.unrealizedPnlPercent()).isCloseTo(new BigDecimal("18.17"), within(new BigDecimal("0.01")));
        assertThat(accountOwner.realizedPnlByCurrency()).containsOnlyKeys("USD");
        assertThat(accountOwner.realizedPnlByCurrency().get("USD")).isEqualByComparingTo("500");
        assertThat(accountOwner.totalPnl()).isEqualByComparingTo("9060");
        assertThat(accountOwner.warnings()).isEmpty();
    }

    @Test
    void theInvestorsAlwaysAddUpToIbsFigures() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(39, buy(AVI_ID, "24", "120")), microsoft()),
                List.of(deposit(AVI_ID, "30000")));

        InvestorSummary avi = summaries.get(AVI_ID);
        InvestorSummary accountOwner = summaries.get(ACCOUNT_OWNER_ID);
        assertThat(avi.cash().add(accountOwner.cash())).isEqualByComparingTo("40000");
        assertThat(avi.totalValue().add(accountOwner.totalValue())).isEqualByComparingTo("100000");
        assertThat(avi.sharesValue().add(accountOwner.sharesValue())).isEqualByComparingTo("60000");
        assertThat(avi.sharesCost().add(accountOwner.sharesCost())).isEqualByComparingTo("50000");
    }

    /**
     * INVESTORS_TODO.md, decision 13: an investor's part of every holding, added up, is their card — with a commission and
     * a partial sell, so that the average cost is not a round number.
     */
    @Test
    void eachInvestorsPartsOfEveryHoldingAddUpToTheirCard() {
        List<PositionTrades> holdings = List.of(
                nvidia(35, buy(ACCOUNT_OWNER_ID, "15", "100"), trade(AVI_ID, TradeSide.BUY, "24", "120", "5"),
                        sell(AVI_ID, "4", "180")),
                microsoft());
        Map<Long, InvestorSummary> summaries = summariesOf(holdings, List.of(deposit(AVI_ID, "30000")));

        for (long investorId : List.of(ACCOUNT_OWNER_ID, AVI_ID)) {
            BigDecimal sharesValue = BigDecimal.ZERO;
            BigDecimal sharesCost = BigDecimal.ZERO;
            for (PositionTrades holding : holdings) {
                InvestorPart part = holding.partsByInvestor(ACCOUNT_OWNER_ID).get(investorId);
                if (part != null) {   // Avi holds no MSFT
                    sharesValue = sharesValue.add(part.sharesValue());
                    sharesCost = sharesCost.add(part.sharesCost());
                }
            }
            assertThat(sharesValue).isEqualByComparingTo(summaries.get(investorId).sharesValue());
            assertThat(sharesCost).isEqualByComparingTo(summaries.get(investorId).sharesCost());
        }
    }

    @Test
    void aWithdrawalTakesMoneyOutOfTheirCash() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(39, buy(AVI_ID, "24", "120")), microsoft()),
                List.of(deposit(AVI_ID, "30000"), withdrawal(AVI_ID, "5000")));

        assertThat(summaries.get(AVI_ID).depositsMinusWithdrawals()).isEqualByComparingTo("25000");
        assertThat(summaries.get(AVI_ID).cash()).isEqualByComparingTo("22120");
        assertThat(summaries.get(ACCOUNT_OWNER_ID).cash()).isEqualByComparingTo("17880");
    }

    /** $5 to buy: out of the cash, and into what the shares cost — as IB's average cost counts it. */
    @Test
    void aCommissionComesOutOfTheCashAndGoesIntoTheCost() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(39, trade(AVI_ID, TradeSide.BUY, "24", "120", "5")), microsoft()),
                List.of(deposit(AVI_ID, "30000")));

        InvestorSummary avi = summaries.get(AVI_ID);
        assertThat(avi.cash()).isEqualByComparingTo("27115");
        assertThat(avi.sharesCost()).isEqualByComparingTo("2885");
        assertThat(avi.unrealizedPnl()).isEqualByComparingTo("1435");
    }

    /** INVESTORS_TODO.md, decision 5: 4 of the 24 sold at 180 — +240 realized, and the 20 left still cost 120 each. */
    @Test
    void aPartialSellIsRealizedAndLeavesTheRestAtTheSameAverageCost() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(35, buy(AVI_ID, "24", "120"), sell(AVI_ID, "4", "180")), microsoft()),
                List.of(deposit(AVI_ID, "30000")));

        InvestorSummary avi = summaries.get(AVI_ID);
        assertThat(avi.cash()).isEqualByComparingTo("27840");
        assertThat(avi.sharesValue()).isEqualByComparingTo("3600");
        assertThat(avi.sharesCost()).isEqualByComparingTo("2400");
        assertThat(avi.unrealizedPnl()).isEqualByComparingTo("1200");
        assertThat(avi.realizedPnlByCurrency().get("USD")).isEqualByComparingTo("240");
        assertThat(avi.totalPnl()).isEqualByComparingTo("1440");
    }

    @Test
    void aTradeWithoutAPriceLeavesTheirCashAndCostUnknownAndTheAccountOwnersToo() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(39, buy(AVI_ID, "24", null)), microsoft()),
                List.of(deposit(AVI_ID, "30000")));

        InvestorSummary avi = summaries.get(AVI_ID);
        assertThat(avi.cash()).isNull();
        assertThat(avi.totalValue()).isNull();
        assertThat(avi.sharesValue()).isEqualByComparingTo("4320");   // the quantity is known, and IB's price
        assertThat(avi.sharesCost()).isNull();
        assertThat(avi.unrealizedPnl()).isNull();
        assertThat(avi.totalPnl()).isNull();
        assertThat(avi.warnings()).containsExactly(new InvestorWarning(InvestorWarningType.TRADES_WITHOUT_PRICE,
                "No price on 1 trade, so the cash is unknown."));

        InvestorSummary accountOwner = summaries.get(ACCOUNT_OWNER_ID);
        assertThat(accountOwner.cash()).isNull();
        assertThat(accountOwner.totalValue()).isNull();
        assertThat(accountOwner.sharesCost()).isNull();
        assertThat(accountOwner.sharesValue()).isEqualByComparingTo("55680");
    }

    @Test
    void cashBelowZeroIsAWarning() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(39, buy(AVI_ID, "24", "120")), microsoft()),
                List.of());

        assertThat(summaries.get(AVI_ID).cash()).isEqualByComparingTo("-2880");
        assertThat(summaries.get(AVI_ID).warnings()).containsExactly(new InvestorWarning(
                InvestorWarningType.NEGATIVE_CASH, "The cash comes to -2,880.00 USD: a deposit may be missing."));
    }

    @Test
    void moreSharesThanIbReportsIsAWarning() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(20, buy(AVI_ID, "24", "120")), microsoft()),
                List.of(deposit(AVI_ID, "30000")));

        assertThat(summaries.get(AVI_ID).warnings()).containsExactly(new InvestorWarning(
                InvestorWarningType.MORE_SHARES_THAN_IB, "The trades in NVDA add up to 24 shares; IB reports 20."));
    }

    /** INVESTORS_TODO.md, decision 8: the account owner sold their 15 while Avi still holds 24. */
    @Test
    void oneInvestorsClosedPositionInsideAHoldingStillOpen() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(nvidia(24, buy(ACCOUNT_OWNER_ID, "15", "120"), sell(ACCOUNT_OWNER_ID, "15", "170"),
                        buy(AVI_ID, "24", "120")), microsoft()),
                List.of(deposit(AVI_ID, "30000")));

        assertThat(summaries.get(ACCOUNT_OWNER_ID).realizedPnlByCurrency().get("USD")).isEqualByComparingTo("750");
        assertThat(summaries.get(AVI_ID).realizedPnlByCurrency()).isEmpty();
        assertThat(summaries.get(AVI_ID).sharesCost()).isEqualByComparingTo("2880");
    }

    /** INVESTORS_TODO.md, decision 11: the realized P&amp;L is by currency, the cash USD only. */
    @Test
    void tradesInAnotherCurrencyAreLeftOutOfTheCashButRealizedInTheirOwnCurrency() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(microsoft(), manualPosition("9988.HK", "HKD", buy(AVI_ID, "100", "100"),
                        sell(AVI_ID, "100", "109.5"))),
                List.of(deposit(AVI_ID, "30000")));

        InvestorSummary avi = summaries.get(AVI_ID);
        assertThat(avi.cash()).isEqualByComparingTo("30000");
        assertThat(avi.realizedPnlByCurrency()).containsOnlyKeys("HKD");
        assertThat(avi.realizedPnlByCurrency().get("HKD")).isEqualByComparingTo("950");
        assertThat(avi.totalPnl()).isEqualByComparingTo("0");   // the HKD is not added to a USD total
        assertThat(avi.warnings()).containsExactly(new InvestorWarning(InvestorWarningType.TRADES_NOT_IN_USD,
                "Left out of the cash, which counts USD only: 2 trades in HKD."));
    }

    @Test
    void aClosedPositionWithoutARealizedPnlIsLeftOutWithAWarning() {
        Map<Long, InvestorSummary> summaries = summariesOf(
                List.of(microsoft(),
                        manualPosition("AAPL", "USD", buy(ACCOUNT_OWNER_ID, "10", null),
                                sell(ACCOUNT_OWNER_ID, "10", "150")),
                        manualPosition("CRM", "USD", buy(ACCOUNT_OWNER_ID, "10", "100"),
                                sell(ACCOUNT_OWNER_ID, "10", "120"))),
                List.of());

        InvestorSummary accountOwner = summaries.get(ACCOUNT_OWNER_ID);
        assertThat(accountOwner.realizedPnlByCurrency().get("USD")).isEqualByComparingTo("200");
        assertThat(accountOwner.warnings()).containsExactly(new InvestorWarning(
                InvestorWarningType.CLOSED_POSITIONS_NOT_COUNTED, "Left out of the realized P&L: 1 closed position "
                + "without one (a price is missing, or more was sold than bought)."));
    }

    @Test
    void figuresIbDidNotReportLeaveOnlyTheAccountOwnersUnknown() {
        Map<Long, InvestorSummary> summaries = new InvestorSummaryCalculator(ACCOUNT_OWNER_ID,
                List.of(ACCOUNT_OWNER_ID, AVI_ID), List.of(nvidia(39, buy(AVI_ID, "24", "120")), microsoft()),
                List.of(deposit(AVI_ID, "30000")), null, null).summariesByInvestor();

        assertThat(summaries.get(ACCOUNT_OWNER_ID).cash()).isNull();
        assertThat(summaries.get(ACCOUNT_OWNER_ID).totalValue()).isNull();
        assertThat(summaries.get(ACCOUNT_OWNER_ID).sharesValue()).isEqualByComparingTo("55680");
        assertThat(summaries.get(AVI_ID).cash()).isEqualByComparingTo("27120");
    }

    @Test
    void anInvestorWithADepositAndNoTradeYetHasOnlyCash() {
        InvestorSummary avi = summariesOf(List.of(microsoft()), List.of(deposit(AVI_ID, "30000"))).get(AVI_ID);

        assertThat(avi.cash()).isEqualByComparingTo("30000");
        assertThat(avi.sharesValue()).isEqualByComparingTo("0");
        assertThat(avi.sharesCost()).isEqualByComparingTo("0");
        assertThat(avi.unrealizedPnl()).isEqualByComparingTo("0");
        assertThat(avi.unrealizedPnlPercent()).isNull();
        assertThat(avi.totalValue()).isEqualByComparingTo("30000");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** Each trade gets the next id, in the order a test creates them; JUnit makes a new instance for every test. */
    private long nextTradeId = 1;

    private Map<Long, InvestorSummary> summariesOf(List<PositionTrades> positions,
                                                   List<CashMovementFact> cashMovements) {
        return new InvestorSummaryCalculator(ACCOUNT_OWNER_ID, List.of(ACCOUNT_OWNER_ID, AVI_ID), positions,
                cashMovements, IB_CASH, IB_NET_LIQUIDATION).summariesByInvestor();
    }

    /** At 180, with IB's average cost of 120. */
    private PositionTrades nvidia(double ibPosition, TradeFact... trades) {
        return holding("NVDA", ibPosition, 120.0, 180.0, trades);
    }

    /**
     * What makes up the rest of the example's 60,000 / 50,000. Without NVDA's 39 (7,020 / 4,680) it is 52,980 / 45,320 —
     * however many NVDA a test has IB report, so the account owner's figures stay comparable.
     */
    private PositionTrades microsoft() {
        return holding("MSFT", 20, 2266.0, 2649.0);
    }

    private PositionTrades holding(String symbol, double ibPosition, double averageCost, double marketPrice,
                                   TradeFact... trades) {
        Holding ibHolding = new Holding(symbol, 0, "STK", "USD", ibPosition, averageCost, marketPrice,
                ibPosition * marketPrice, ibPosition * (marketPrice - averageCost), 0.0, "U1234567");
        return new PositionTrades(symbol, "USD", ibHolding, List.of(trades));
    }

    private PositionTrades manualPosition(String symbol, String currency, TradeFact... trades) {
        return new PositionTrades(symbol, currency, null, List.of(trades));
    }

    private TradeFact buy(long investorId, String quantity, String price) {
        return trade(investorId, TradeSide.BUY, quantity, price, "0");
    }

    private TradeFact sell(long investorId, String quantity, String price) {
        return trade(investorId, TradeSide.SELL, quantity, price, "0");
    }

    /** Dated by the order a test creates them: a sell after the buy before it. */
    private TradeFact trade(long investorId, TradeSide side, String quantity, String price, String commission) {
        long tradeId = nextTradeId++;
        BigDecimal priceOrNull = price == null ? null : new BigDecimal(price);
        return new TradeFact(tradeId, investorId, LocalDate.of(2026, 1, 1).plusDays(tradeId), side,
                new BigDecimal(quantity), priceOrNull, new BigDecimal(commission));
    }

    private CashMovementFact deposit(long investorId, String amount) {
        return new CashMovementFact(investorId, CashMovementType.DEPOSIT, new BigDecimal(amount));
    }

    private CashMovementFact withdrawal(long investorId, String amount) {
        return new CashMovementFact(investorId, CashMovementType.WITHDRAWAL, new BigDecimal(amount));
    }
}
