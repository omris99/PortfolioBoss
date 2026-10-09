package portfolioboss.calculation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The default commission of an order. Pure computation — no Spring, no database. */
class OrderCommissionTest {

    @Test
    void anOrderOfUpTo500SharesIsChargedTheFiveDollarMinimum() {
        assertThat(OrderCommission.defaultFor(new BigDecimal("1"))).isEqualByComparingTo("5");
        assertThat(OrderCommission.defaultFor(new BigDecimal("100"))).isEqualByComparingTo("5");
        assertThat(OrderCommission.defaultFor(new BigDecimal("500"))).isEqualByComparingTo("5");
    }

    @Test
    void anOrderOfMoreThan500SharesIsChargedOneCentAShare() {
        assertThat(OrderCommission.defaultFor(new BigDecimal("600"))).isEqualByComparingTo("6");
        assertThat(OrderCommission.defaultFor(new BigDecimal("2500"))).isEqualByComparingTo("25");
    }

    @Test
    void aCommissionIsRoundedToTheCent() {
        assertThat(OrderCommission.defaultFor(new BigDecimal("1234.5"))).isEqualTo(new BigDecimal("12.35"));
    }

    @Test
    void aCommissionEnteredIsKeptAndOnlyAMissingOneIsTheDefault() {
        assertThat(OrderCommission.orDefault(new BigDecimal("1.25"), new BigDecimal("600"))).isEqualByComparingTo("1.25");
        assertThat(OrderCommission.orDefault(BigDecimal.ZERO, new BigDecimal("600"))).isEqualByComparingTo("0");
        assertThat(OrderCommission.orDefault(null, new BigDecimal("600"))).isEqualByComparingTo("6");
    }
}
