package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ib.Holding;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * A holding's share of the whole account, the Overview's way: the dollar holdings plus the cash. Made-up holdings of
 * $1,500 and $3,500 and $5,000 of cash make an account of $10,000.
 */
class PortfolioWeightsCalculatorTest {

    private final Holding abcd = holdingOf("ABCD", 1, "USD", 1_500);
    private final Holding wxyz = holdingOf("WXYZ", 2, "USD", 3_500);

    @Test
    void aHoldingsShareOfTheDollarHoldingsAndTheCash() {
        PortfolioWeightsCalculator weights = new PortfolioWeightsCalculator(List.of(abcd, wxyz), 5_000.0);

        assertThat(weights.wholeAccount()).isEqualTo(10_000.0);
        assertThat(weights.percentOfAccountOf(abcd)).isCloseTo(15.0, within(1e-9));
    }

    /** No exchange rate yet: a holding in Hong Kong dollars is neither weighed nor part of the whole. */
    @Test
    void aHoldingInAnotherCurrencyIsLeftOut() {
        Holding hongKongHolding = holdingOf("700", 3, "HKD", 40_000);
        PortfolioWeightsCalculator weights =
                new PortfolioWeightsCalculator(List.of(abcd, wxyz, hongKongHolding), 5_000.0);

        assertThat(weights.wholeAccount()).isEqualTo(10_000.0);
        assertThat(weights.percentOfAccountOf(hongKongHolding)).isNull();
    }

    @Test
    void aHoldingWithNoMarketValueOrNoneAboveZeroIsLeftOut() {
        Holding withoutMarketValue = holdingOf("NOMV", 4, "USD", Double.NaN);
        Holding shortPosition = holdingOf("SHRT", 5, "USD", -2_000);
        PortfolioWeightsCalculator weights =
                new PortfolioWeightsCalculator(List.of(abcd, wxyz, withoutMarketValue, shortPosition), 5_000.0);

        assertThat(weights.wholeAccount()).isEqualTo(10_000.0);
        assertThat(weights.percentOfAccountOf(withoutMarketValue)).isNull();
        assertThat(weights.percentOfAccountOf(shortPosition)).isNull();
    }

    @Test
    void withoutTheCashNothingIsWeighed() {
        PortfolioWeightsCalculator weights = new PortfolioWeightsCalculator(List.of(abcd, wxyz), null);

        assertThat(weights.wholeAccount()).isNull();
        assertThat(weights.percentOfAccountOf(abcd)).isNull();
    }

    private Holding holdingOf(String symbol, int conId, String currency, double marketValue) {
        return new Holding(symbol, conId, "STK", currency, 10, 100.0, marketValue / 10, marketValue, 0.0, 0.0,
                "U7654321");
    }
}
