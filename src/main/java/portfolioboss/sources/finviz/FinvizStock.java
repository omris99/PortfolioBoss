package portfolioboss.sources.finviz;

import portfolioboss.sources.EarningsDate;

/**
 * One row of a screen Finviz exported (RECOMMENDATIONS_FINVIZ_TODO.md): a stock of one of its three screens, or a
 * holding whose company figures were asked for. Finviz's columns, under readable names; only this package reads them
 * from the CSV ({@link FinvizScreenerReader}). Every figure is {@code null} when Finviz has none for the stock ("-" in
 * its CSV), never 0. Percents are percents ({@code 27.5} is 27.5%), ratios are ratios.
 *
 * @param symbol                               as Finviz writes it: "BRK-B" where IB writes "BRK B"
 * @param marketCapMillions                    in millions of dollars
 * @param pe                                   the price against the earnings of the last 12 months
 * @param forwardPe                            the price against the earnings expected in the next 12 months
 * @param peg                                  the P/E against the expected growth of the earnings
 * @param priceToBook                          the price against the book value per share
 * @param salesGrowthQuarterOverQuarterPercent the latest quarter's sales against the same quarter a year before
 * @param debtToEquity                         the total debt divided by the equity
 * @param nextEarningsDate                     {@code null} when Finviz knows no date
 * @param analystTargetPrice                   the analysts' average price target; {@code null} also when the column
 *                                             isn't in the CSV
 * @param analystRecommendation                the analysts' average rating, from 1 (strong buy) to 5 (strong sell);
 *                                             {@code null} also when the column isn't in the CSV
 */
public record FinvizStock(
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
