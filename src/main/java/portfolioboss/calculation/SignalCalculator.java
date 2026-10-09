package portfolioboss.calculation;

import portfolioboss.ai.AnalystTrend;
import portfolioboss.ai.Sentiment;
import portfolioboss.model.HoldingSignal;
import portfolioboss.model.MomentumLabel;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Works out the colored dot next to a holding (AI_ANALYSIS_TODO.md, decision 6) from three warning signs: weak momentum,
 * analysts deteriorating, negative news. Red for two or more, green for none, yellow for one — and no dot
 * ({@code null}) while fewer than two of the three are known. A plain rule, so what each color means is known.
 *
 * @param momentumLabel from the daily closes; {@code null} while there are not enough of them
 * @param analystTrend  from the latest analysis; {@code null} when the search results showed none
 * @param sentiment     from the latest analysis; {@code null} when there was no recent news
 */
public record SignalCalculator(MomentumLabel momentumLabel, AnalystTrend analystTrend, Sentiment sentiment) {

    public HoldingSignal signal() {
        Boolean weakMomentum = momentumLabel == null ? null : momentumLabel == MomentumLabel.WEAK;
        Boolean analystsDeteriorating = analystTrend == null ? null : analystTrend == AnalystTrend.DETERIORATING;
        Boolean negativeNews = sentiment == null ? null : sentiment == Sentiment.NEGATIVE;
        List<Boolean> knownSigns = Stream.of(weakMomentum, analystsDeteriorating, negativeNews)
                .filter(Objects::nonNull)
                .toList();
        if (knownSigns.size() < 2) {
            return null;
        }
        long warningCount = knownSigns.stream().filter(Boolean::booleanValue).count();
        if (warningCount >= 2) {
            return HoldingSignal.RED;
        }
        return warningCount == 0 ? HoldingSignal.GREEN : HoldingSignal.YELLOW;
    }
}
