package portfolioboss.ai;

import jakarta.annotation.Nullable;

import java.time.LocalDate;

/**
 * The analysts' consensus as one source shows it (MarketBeat, Financhill, stockanalysis…), extracted by Claude from
 * that source's search result without merging it with any other (AI_ANALYSIS_TODO.md, decision 16). Which source the
 * UI shows is the code's choice: {@code calculation.ConsensusCalculator}.
 *
 * <p>{@code @Nullable} marks what the source may not show: the schema Claude answers in allows {@code null} there and
 * nowhere else (the SDK reads any annotation named {@code Nullable}).
 *
 * @param publishedDate the search result's date, for telling apart two sources that cover as many analysts
 * @param analystCount  how many analysts the source says it covers
 * @param ratingCounts  how many give each rating; {@code null} when the source shows no breakdown
 * @param averageTarget the average price target
 * @param ratingLabel   the source's own consensus, mapped onto the one scale ("Moderate Buy" is {@code BUY})
 */
public record SourceConsensus(
        String sourceUrl,
        @Nullable LocalDate publishedDate,
        @Nullable Integer analystCount,
        @Nullable RatingCounts ratingCounts,
        @Nullable Double averageTarget,
        @Nullable AnalystRating ratingLabel) {
}
