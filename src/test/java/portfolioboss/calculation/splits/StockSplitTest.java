package portfolioboss.calculation.splits;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** One split's arithmetic and its day (STOCK_SPLITS_TODO.md, decisions 3 and 4), on made-up splits. */
class StockSplitTest {

    private static final LocalDateTime SPLIT_TIME = LocalDateTime.of(2026, 3, 10, 20, 25);

    private final StockSplit fourForOne = new StockSplit("A1", "WXYZ", 1111, 1111, SPLIT_TIME, 1, 4);
    private final StockSplit oneForThree = new StockSplit("A2", "WXYZ", 1111, 2222, SPLIT_TIME, 3, 1);

    @Test
    void aSplitOfFourForOneMakesEveryShareFourAtAQuarterOfThePrice() {
        assertThat(fourForOne.quantityAfterTheSplit(new BigDecimal("10"))).isEqualByComparingTo("40");
        assertThat(fourForOne.priceAfterTheSplit(new BigDecimal("400"))).isEqualByComparingTo("100");
    }

    @Test
    void aSaleKeepsItsSign() {
        assertThat(fourForOne.quantityAfterTheSplit(new BigDecimal("-4"))).isEqualByComparingTo("-16");
    }

    /** 7 shares in a reverse split of 1 for 3: a fraction no number of digits holds, kept to 16 of them. */
    @Test
    void aReverseSplitOfOneForThreeKeepsSixteenDigits() {
        assertThat(oneForThree.quantityAfterTheSplit(new BigDecimal("7")).toPlainString())
                .isEqualTo("2.333333333333333");
        assertThat(oneForThree.priceAfterTheSplit(new BigDecimal("10"))).isEqualByComparingTo("30");
    }

    /** IB books it at 20:25, after the market closed: a trade typed in for that day was before it, one of the next not. */
    @Test
    void aTradeTypedInForTheSplitsDayWasBeforeIt() {
        assertThat(fourForOne.isBeforeTheSplit(LocalDate.of(2026, 3, 9))).isTrue();
        assertThat(fourForOne.isBeforeTheSplit(LocalDate.of(2026, 3, 10))).isTrue();
        assertThat(fourForOne.isBeforeTheSplit(LocalDate.of(2026, 3, 11))).isFalse();
    }

    @Test
    void anExecutionInIbsReportsIsBeforeTheSplitByItsTime() {
        assertThat(fourForOne.isBeforeTheSplit(SPLIT_TIME.minusMinutes(1))).isTrue();
        assertThat(fourForOne.isBeforeTheSplit(SPLIT_TIME)).isFalse();
    }

    @Test
    void aSplitIntoANewContractDoesntKeepTheSameContract() {
        assertThat(fourForOne.keepsTheSameContract()).isTrue();
        assertThat(oneForThree.keepsTheSameContract()).isFalse();
    }

    @Test
    void theRatioIsWrittenAsIbWritesIt() {
        assertThat(fourForOne.ratioInIbsWords()).isEqualTo("4 for 1");
        assertThat(oneForThree.ratioInIbsWords()).isEqualTo("1 for 3");
        assertThat(fourForOne.splitDay()).isEqualTo(LocalDate.of(2026, 3, 10));
    }
}
