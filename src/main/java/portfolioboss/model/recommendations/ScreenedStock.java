package portfolioboss.model.recommendations;

import portfolioboss.sources.EarningsDate;

/**
 * One stock of a {@link RecommendationList}, as the app keeps it (RECOMMENDATIONS_FINVIZ_TODO.md, decision 4: the rest of
 * the app never sees a source's own record) — translated from the source's row when the lists are downloaded, and stored
 * as JSON in {@code listed_stock.figures}, so a component may be added, never renamed. Every figure is {@code null} when
 * the source has none for the stock, never 0. Percents are percents ({@code 27.5} is 27.5%), ratios are ratios.
 *
 * @param symbol                               as IB writes it: "BRK B"
 * @param marketCapMillions                    in millions of dollars
 * @param pe                                   the price against the earnings of the last 12 months
 * @param forwardPe                            the price against the earnings expected in the next 12 months
 * @param peg                                  the P/E against the expected growth of the earnings
 * @param priceToBook                          the price against the book value per share
 * @param salesGrowthQuarterOverQuarterPercent the latest quarter's sales against the same quarter a year before
 * @param debtToEquity                         the total debt divided by the equity
 * @param nextEarningsDate                     {@code null} when the source knows no date
 * @param analystTargetPrice                   the analysts' average price target
 * @param analystRecommendation                the analysts' average rating, from 1 (strong buy) to 5 (strong sell)
 */
public record ScreenedStock(
        String symbol,
        String companyName,
        String sector,
        String industry,
        String country,
        Double marketCapMillions,
        Double price,
        Double pe,
        Double forwardPe,
        Double peg,
        Double priceToSales,
        Double priceToBook,
        Double priceToFreeCashFlow,
        Double epsGrowthPast5YearsPercent,
        Double salesGrowthPast5YearsPercent,
        Double salesGrowthQuarterOverQuarterPercent,
        Double grossMarginPercent,
        Double operatingMarginPercent,
        Double netProfitMarginPercent,
        Double debtToEquity,
        EarningsDate nextEarningsDate,
        Double analystTargetPrice,
        Double analystRecommendation) {
}
