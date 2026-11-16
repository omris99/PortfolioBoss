package portfolioboss.tax;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The rule of CAPITAL_GAINS_TAX_TODO.md's decision 11 — the gain two ways, the one closer to zero taxed —, on the four
 * rows of its table: made-up figures, without commissions.
 */
class TaxableGainOfSharesFromOneBuyTest {

    private static final LocalDate BUY_SETTLEMENT_DAY = LocalDate.of(2025, 3, 4);

    /** $100 at 3.5 (350 ₪), sold for $110 at 3.2 (352 ₪): +$10 × 3.2 = 32 in dollars, +2 in shekels. */
    @Test
    void bothWaysAGainTaxTheSmallerGain() {
        TaxableGainOfSharesFromOneBuy gain = gainOf("100", "3.5", "110", "3.2");

        assertThat(gain.gainInDollarsAtTheSaleRate()).isEqualByComparingTo("32");
        assertThat(gain.gainInShekels()).isEqualByComparingTo("2");
        assertThat(gain.taxableGain()).isEqualByComparingTo("2");
    }

    /** $100 at 3.0 (300 ₪), sold for $80 at 3.2 (256 ₪): −$20 × 3.2 = −64 in dollars, −44 in shekels. */
    @Test
    void bothWaysALossTaxTheSmallerLoss() {
        TaxableGainOfSharesFromOneBuy gain = gainOf("100", "3.0", "80", "3.2");

        assertThat(gain.gainInDollarsAtTheSaleRate()).isEqualByComparingTo("-64");
        assertThat(gain.gainInShekels()).isEqualByComparingTo("-44");
        assertThat(gain.taxableGain()).isEqualByComparingTo("-44");
    }

    /** $100 at 3.0 (300 ₪), sold for $98 at 3.2 (313.60 ₪): −6.40 in dollars, +13.60 in shekels — no tax, no shield. */
    @Test
    void aLossInDollarsAndAGainInShekelsTaxNothing() {
        TaxableGainOfSharesFromOneBuy gain = gainOf("100", "3.0", "98", "3.2");

        assertThat(gain.gainInDollarsAtTheSaleRate()).isEqualByComparingTo("-6.40");
        assertThat(gain.gainInShekels()).isEqualByComparingTo("13.60");
        assertThat(gain.taxableGain()).isEqualByComparingTo("0");
    }

    /** $100 at 3.5 (350 ₪), sold for $105 at 3.2 (336 ₪): +16 in dollars, −14 in shekels — no tax, no shield. */
    @Test
    void aGainInDollarsAndALossInShekelsTaxNothing() {
        TaxableGainOfSharesFromOneBuy gain = gainOf("100", "3.5", "105", "3.2");

        assertThat(gain.gainInDollarsAtTheSaleRate()).isEqualByComparingTo("16");
        assertThat(gain.gainInShekels()).isEqualByComparingTo("-14");
        assertThat(gain.taxableGain()).isEqualByComparingTo("0");
    }

    /** One share, so that the cost and the proceeds are the plan's dollar amounts. */
    private TaxableGainOfSharesFromOneBuy gainOf(String cost, String buyRate, String proceeds, String saleRate) {
        SharesSoldFromOneBuy sharesSold = new SharesSoldFromOneBuy(TaxTestData.buy("1", "1", cost, BUY_SETTLEMENT_DAY),
                BigDecimal.ONE, new BigDecimal(cost), new BigDecimal(proceeds));
        return new TaxableGainOfSharesFromOneBuy(sharesSold, new BigDecimal(buyRate), new BigDecimal(saleRate));
    }
}
