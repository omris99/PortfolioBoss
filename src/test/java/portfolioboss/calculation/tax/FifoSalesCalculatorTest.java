package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;
import portfolioboss.model.portfolio.TradeSide;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static portfolioboss.calculation.tax.TaxTestData.buy;
import static portfolioboss.calculation.tax.TaxTestData.sale;
import static portfolioboss.calculation.tax.TaxTestData.tradeOf;

/** Which shares each sale took, first in first out (CAPITAL_GAINS_TAX_TODO.md, decision 3). Made-up trades of ABCD. */
class FifoSalesCalculatorTest {

    private static final LocalDate FIRST_BUY_DAY = LocalDate.of(2025, 2, 4);
    private static final LocalDate SECOND_BUY_DAY = LocalDate.of(2025, 5, 6);
    private static final LocalDate SALE_DAY = LocalDate.of(2025, 9, 9);

    /** Decision 3's example: at average cost the gain would be $100. */
    @Test
    void aSaleTakesTheEarliestBuyFirst() {
        List<FifoSale> sales = new FifoSalesCalculator(List.of(
                buy("1", "10", "10", FIRST_BUY_DAY),
                buy("2", "10", "20", SECOND_BUY_DAY),
                sale("3", "10", "25", SALE_DAY))).sales();

        assertThat(sales).hasSize(1);
        FifoSale fifoSale = sales.get(0);
        assertThat(fifoSale.sharesSoldFromEachBuy()).hasSize(1);
        assertThat(fifoSale.sharesSoldFromEachBuy().get(0).buy().tradeId()).isEqualTo("1");
        assertThat(fifoSale.gainOfTheSharesSoldFromBuys()).isEqualByComparingTo("150");
        assertThat(fifoSale.soldMoreThanBought()).isFalse();
    }

    @Test
    void aSaleLargerThanTheEarliestBuyTakesTheRestFromTheNextOne() {
        FifoSale fifoSale = new FifoSalesCalculator(List.of(
                buy("1", "10", "10", FIRST_BUY_DAY),
                buy("2", "10", "20", SECOND_BUY_DAY),
                sale("3", "15", "25", SALE_DAY))).sales().get(0);

        List<SharesSoldFromOneBuy> sharesSoldFromEachBuy = fifoSale.sharesSoldFromEachBuy();
        assertThat(sharesSoldFromEachBuy).extracting(sharesSold -> sharesSold.buy().tradeId())
                .containsExactly("1", "2");
        assertThat(sharesSoldFromEachBuy.get(0).quantity()).isEqualByComparingTo("10");
        assertThat(sharesSoldFromEachBuy.get(0).gain()).isEqualByComparingTo("150");
        assertThat(sharesSoldFromEachBuy.get(1).quantity()).isEqualByComparingTo("5");
        assertThat(sharesSoldFromEachBuy.get(1).gain()).isEqualByComparingTo("25");
    }

    /** The second sale takes what the first left of the same buy, and then the next buy. */
    @Test
    void aBuyIsSharedBetweenTwoSales() {
        List<FifoSale> sales = new FifoSalesCalculator(List.of(
                buy("1", "10", "10", FIRST_BUY_DAY),
                sale("2", "4", "15", SECOND_BUY_DAY),
                buy("3", "10", "30", SECOND_BUY_DAY),
                sale("4", "8", "40", SALE_DAY))).sales();

        assertThat(sales.get(0).sharesSoldFromEachBuy()).singleElement()
                .satisfies(sharesSold -> assertThat(sharesSold.quantity()).isEqualByComparingTo("4"));
        List<SharesSoldFromOneBuy> sharesOfTheSecondSale = sales.get(1).sharesSoldFromEachBuy();
        assertThat(sharesOfTheSecondSale).extracting(sharesSold -> sharesSold.buy().tradeId()).containsExactly("1", "3");
        assertThat(sharesOfTheSecondSale.get(0).quantity()).isEqualByComparingTo("6");
        assertThat(sharesOfTheSecondSale.get(1).quantity()).isEqualByComparingTo("2");
    }

    /**
     * The buy's commission goes with its shares into their cost, the sale's comes off their proceeds — each by quantity:
     * 4 of 10 shares take 4/10 of the buy's $5, and all of the sale's $2.
     */
    @Test
    void theCommissionsAreSpreadOverTheSharesByQuantity() {
        SharesSoldFromOneBuy sharesSold = new FifoSalesCalculator(List.of(
                tradeOf("1", TradeSide.BUY, "10", "10", "5", FIRST_BUY_DAY),
                tradeOf("2", TradeSide.SELL, "4", "15", "2", SALE_DAY))).sales().get(0).sharesSoldFromEachBuy().get(0);

        assertThat(sharesSold.cost()).isEqualByComparingTo("42");
        assertThat(sharesSold.proceeds()).isEqualByComparingTo("58");
        assertThat(sharesSold.gain()).isEqualByComparingTo("16");
    }

    /** A sale's commission split between two buys is split by their shares: $3 over 10 and 5 shares, $2 and $1. */
    @Test
    void aSalesCommissionIsSplitBetweenTheBuysItTookFrom() {
        List<SharesSoldFromOneBuy> sharesSoldFromEachBuy = new FifoSalesCalculator(List.of(
                buy("1", "10", "10", FIRST_BUY_DAY),
                buy("2", "10", "10", SECOND_BUY_DAY),
                tradeOf("3", TradeSide.SELL, "15", "10", "3", SALE_DAY))).sales().get(0).sharesSoldFromEachBuy();

        assertThat(sharesSoldFromEachBuy.get(0).proceeds()).isEqualByComparingTo("98");
        assertThat(sharesSoldFromEachBuy.get(1).proceeds()).isEqualByComparingTo("49");
    }

    // ── its cost first in first out against the Closed page's average cost (session 5) ───────────────────────────────

    /** Decision 3's example: $100 first in first out, $150 at the average cost the Closed page uses. */
    @Test
    void theCostDiffersFromTheAverageCostWhenTheSharesHeldWereBoughtAtDifferentPrices() {
        FifoSale fifoSale = new FifoSalesCalculator(List.of(
                buy("1", "10", "10", FIRST_BUY_DAY),
                buy("2", "10", "20", SECOND_BUY_DAY),
                sale("3", "10", "25", SALE_DAY))).sales().get(0);

        assertThat(fifoSale.fifoCost()).isEqualByComparingTo("100");
        assertThat(fifoSale.costAtTheAverageCost()).isEqualByComparingTo("150");
        assertThat(fifoSale.fifoCostDiffersFromTheAverageCost()).isTrue();
    }

    /**
     * The average carries from one sale to the next, as on the Closed page: after the first sale the 10 shares left cost
     * $150 there ($15 each) and $200 first in first out, so a second sale of 5 costs $75 against $100.
     */
    @Test
    void theAverageCostCarriesFromOneSaleToTheNext() {
        FifoSale secondSale = new FifoSalesCalculator(List.of(
                buy("1", "10", "10", FIRST_BUY_DAY),
                buy("2", "10", "20", SECOND_BUY_DAY),
                sale("3", "10", "25", SALE_DAY),
                sale("4", "5", "25", SALE_DAY))).sales().get(1);

        assertThat(secondSale.fifoCost()).isEqualByComparingTo("100");
        assertThat(secondSale.costAtTheAverageCost()).isEqualByComparingTo("75");
        assertThat(secondSale.fifoCostDiffersFromTheAverageCost()).isTrue();
    }

    /** Every share held sold: both ways take the whole cost, $300. */
    @Test
    void aSaleOfEveryShareHeldCostsTheSameBothWays() {
        FifoSale fifoSale = new FifoSalesCalculator(List.of(
                buy("1", "10", "10", FIRST_BUY_DAY),
                buy("2", "10", "20", SECOND_BUY_DAY),
                sale("3", "20", "25", SALE_DAY))).sales().get(0);

        assertThat(fifoSale.fifoCost()).isEqualByComparingTo("300");
        assertThat(fifoSale.costAtTheAverageCost()).isEqualByComparingTo("300");
        assertThat(fifoSale.fifoCostDiffersFromTheAverageCost()).isFalse();
    }

    /** One buy, its $5 commission spread the same way: 4 of its 10 shares cost $42 both ways. */
    @Test
    void sharesOfOneBuyCostTheSameBothWays() {
        FifoSale fifoSale = new FifoSalesCalculator(List.of(
                tradeOf("1", TradeSide.BUY, "10", "10", "5", FIRST_BUY_DAY),
                tradeOf("2", TradeSide.SELL, "4", "15", "2", SALE_DAY))).sales().get(0);

        assertThat(fifoSale.costAtTheAverageCost()).isEqualByComparingTo("42");
        assertThat(fifoSale.fifoCostDiffersFromTheAverageCost()).isFalse();
    }

    @Test
    void aSaleOfMoreThanWasBoughtKeepsTheSharesWithoutABuy() {
        FifoSale fifoSale = new FifoSalesCalculator(List.of(
                buy("1", "5", "10", FIRST_BUY_DAY),
                sale("2", "8", "12", SALE_DAY))).sales().get(0);

        assertThat(fifoSale.soldMoreThanBought()).isTrue();
        assertThat(fifoSale.quantitySoldBeyondTheBuys()).isEqualByComparingTo("3");
        assertThat(fifoSale.sharesSoldFromEachBuy()).singleElement()
                .satisfies(sharesSold -> assertThat(sharesSold.quantity()).isEqualByComparingTo("5"));
    }
}
