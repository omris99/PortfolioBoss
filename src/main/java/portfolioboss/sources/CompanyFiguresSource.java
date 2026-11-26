package portfolioboss.sources;

/**
 * The socket of the company figures (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9): a stock's next earnings report and
 * four of the company's figures, for the risk signs and the timing note. Finviz first ({@code FinvizCompanyFigures},
 * {@code @Order(1)}), then Finnhub ({@code FinnhubCompanyFigures}, {@code @Order(2)}). Only the symbols leave the app.
 */
public interface CompanyFiguresSource extends StockDataSource<Stock> {
}
