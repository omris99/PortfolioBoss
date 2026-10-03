package portfolioboss.utils;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The helpers that hold a rule of their own. Pure computation — no Spring, no database. */
class UtilsTest {

    @Test
    void anOrderOfUpTo500SharesIsChargedTheFiveDollarMinimum() {
        assertThat(Utils.calculateOrderCommission(new BigDecimal("1"))).isEqualByComparingTo("5");
        assertThat(Utils.calculateOrderCommission(new BigDecimal("100"))).isEqualByComparingTo("5");
        assertThat(Utils.calculateOrderCommission(new BigDecimal("500"))).isEqualByComparingTo("5");
    }

    @Test
    void anOrderOfMoreThan500SharesIsChargedOneCentAShare() {
        assertThat(Utils.calculateOrderCommission(new BigDecimal("600"))).isEqualByComparingTo("6");
        assertThat(Utils.calculateOrderCommission(new BigDecimal("2500"))).isEqualByComparingTo("25");
    }

    @Test
    void aCommissionIsRoundedToTheCent() {
        assertThat(Utils.calculateOrderCommission(new BigDecimal("1234.5"))).isEqualTo(new BigDecimal("12.35"));
    }

    @Test
    void aCommissionEnteredIsKeptAndOnlyAMissingOneIsTheDefault() {
        assertThat(Utils.commissionOrDefault(new BigDecimal("1.25"), new BigDecimal("600"))).isEqualByComparingTo("1.25");
        assertThat(Utils.commissionOrDefault(BigDecimal.ZERO, new BigDecimal("600"))).isEqualByComparingTo("0");
        assertThat(Utils.commissionOrDefault(null, new BigDecimal("600"))).isEqualByComparingTo("6");
    }

    @Test
    void blankTextIsNothingEnteredAndOtherTextLosesItsSurroundingSpaces() {
        assertThat(Utils.trimmedOrNull(null)).isNull();
        assertThat(Utils.trimmedOrNull("   ")).isNull();
        assertThat(Utils.trimmedOrNull("  Technology ")).isEqualTo("Technology");
    }
}
