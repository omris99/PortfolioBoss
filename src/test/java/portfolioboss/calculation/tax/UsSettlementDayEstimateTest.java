package portfolioboss.calculation.tax;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** A typed trade's settlement day, estimated: two business days after the trade before 28.05.2024, one since. */
class UsSettlementDayEstimateTest {

    @Test
    void beforeTheChangeATradeSettlesTwoWeekdaysLater() {
        LocalDate thursday = LocalDate.of(2024, 5, 16);

        assertThat(new UsSettlementDayEstimate(thursday).estimatedSettlementDay()).isEqualTo(LocalDate.of(2024, 5, 20));
    }

    /** The first day the US market settled the next business day: its trades settled on Wednesday the 29th. */
    @Test
    void fromTheDayOfTheChangeATradeSettlesTheNextWeekday() {
        LocalDate tuesday = LocalDate.of(2024, 5, 28);

        assertThat(new UsSettlementDayEstimate(tuesday).estimatedSettlementDay()).isEqualTo(LocalDate.of(2024, 5, 29));
    }

    @Test
    void aFridayTradeSettlesOnMonday() {
        LocalDate friday = LocalDate.of(2026, 11, 13);

        assertThat(new UsSettlementDayEstimate(friday).estimatedSettlementDay()).isEqualTo(LocalDate.of(2026, 11, 16));
    }

    @Test
    void beforeTheChangeAThursdayTradeSettlesOnMondayAndAFridayTradeOnTuesday() {
        assertThat(new UsSettlementDayEstimate(LocalDate.of(2023, 6, 1)).estimatedSettlementDay())
                .isEqualTo(LocalDate.of(2023, 6, 5));
        assertThat(new UsSettlementDayEstimate(LocalDate.of(2023, 6, 2)).estimatedSettlementDay())
                .isEqualTo(LocalDate.of(2023, 6, 6));
    }
}
