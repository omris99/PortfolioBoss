package portfolioboss.calculation;

import org.junit.jupiter.api.Test;
import portfolioboss.ai.Sentiment;
import portfolioboss.model.AnalystTrend;
import portfolioboss.model.HoldingSignal;
import portfolioboss.model.MomentumLabel;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The dot of AI_ANALYSIS_TODO.md, decision 6, from three warning signs — weak momentum, analysts deteriorating,
 * negative news: red for two or more, green for none, yellow for one, none while fewer than two are known.
 */
class SignalCalculatorTest {

    @Test
    void noWarningSignIsGreen() {
        assertThat(signalOf(MomentumLabel.STRONG, AnalystTrend.IMPROVING, Sentiment.POSITIVE))
                .isEqualTo(HoldingSignal.GREEN);
        assertThat(signalOf(MomentumLabel.NEUTRAL, AnalystTrend.STABLE, Sentiment.NEUTRAL))
                .isEqualTo(HoldingSignal.GREEN);
    }

    @Test
    void oneWarningSignIsYellow() {
        assertThat(signalOf(MomentumLabel.WEAK, AnalystTrend.STABLE, Sentiment.NEUTRAL))
                .isEqualTo(HoldingSignal.YELLOW);
        assertThat(signalOf(MomentumLabel.STRONG, AnalystTrend.DETERIORATING, Sentiment.POSITIVE))
                .isEqualTo(HoldingSignal.YELLOW);
        assertThat(signalOf(MomentumLabel.STRONG, AnalystTrend.IMPROVING, Sentiment.NEGATIVE))
                .isEqualTo(HoldingSignal.YELLOW);
    }

    @Test
    void twoWarningSignsOrMoreAreRed() {
        assertThat(signalOf(MomentumLabel.WEAK, AnalystTrend.DETERIORATING, Sentiment.NEUTRAL))
                .isEqualTo(HoldingSignal.RED);
        assertThat(signalOf(MomentumLabel.STRONG, AnalystTrend.DETERIORATING, Sentiment.NEGATIVE))
                .isEqualTo(HoldingSignal.RED);
        assertThat(signalOf(MomentumLabel.WEAK, AnalystTrend.DETERIORATING, Sentiment.NEGATIVE))
                .isEqualTo(HoldingSignal.RED);
    }

    /** With two of the three known the dot is still decided, the unknown sign counting as no warning. */
    @Test
    void twoKnownSignsAreEnoughForADot() {
        assertThat(signalOf(MomentumLabel.STRONG, null, Sentiment.POSITIVE)).isEqualTo(HoldingSignal.GREEN);
        assertThat(signalOf(MomentumLabel.WEAK, AnalystTrend.STABLE, null)).isEqualTo(HoldingSignal.YELLOW);
        assertThat(signalOf(null, AnalystTrend.DETERIORATING, Sentiment.NEGATIVE)).isEqualTo(HoldingSignal.RED);
    }

    @Test
    void fewerThanTwoKnownSignsHaveNoDot() {
        assertThat(signalOf(MomentumLabel.WEAK, null, null)).isNull();
        assertThat(signalOf(null, null, Sentiment.NEGATIVE)).isNull();
        assertThat(signalOf(null, null, null)).isNull();
    }

    private HoldingSignal signalOf(MomentumLabel momentumLabel, AnalystTrend analystTrend, Sentiment sentiment) {
        return new SignalCalculator(momentumLabel, analystTrend, sentiment).signal();
    }
}
