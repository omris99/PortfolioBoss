package portfolioboss.sources.finviz;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import portfolioboss.sources.CompanyFacts;
import portfolioboss.sources.CompanyFiguresSource;
import portfolioboss.sources.Stock;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finviz as the first source of the company figures (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9): a screen without
 * filters, of only the stocks asked about — every holding in one request, where Finnhub needs two calls a stock. Its row
 * gives the four figures and the next report; a stock Finviz has no row for is left out, and Finnhub is asked about it.
 */
@Component
@Order(1)
public class FinvizCompanyFigures implements CompanyFiguresSource {

    /** No screen filters: only the stocks named. */
    private static final String NO_FILTERS = "";
    /** At most this many symbols in one address, so that it stays short; a portfolio is one request. */
    private static final int SYMBOLS_PER_REQUEST = 100;

    private final FinvizClient finvizClient;

    protected FinvizCompanyFigures(FinvizClient finvizClient) {
        this.finvizClient = finvizClient;
    }

    @Override
    public String sourceName() {
        return FinvizClient.SOURCE_NAME;
    }

    @Override
    public boolean isConnected() {
        return finvizClient.hasToken();
    }

    /** By IB's symbol, as asked: Finviz writes a share class with a "-" where IB writes a space ("BRK-B", "BRK B"). */
    @Override
    public Map<String, Stock> answersFor(List<String> symbols, LocalDate today) {
        Map<String, String> ibSymbolByFinvizSymbol = new LinkedHashMap<>();
        symbols.forEach(ibSymbol -> ibSymbolByFinvizSymbol.put(finvizSymbolOf(ibSymbol), ibSymbol));
        List<String> finvizSymbols = List.copyOf(ibSymbolByFinvizSymbol.keySet());

        Map<String, Stock> answersBySymbol = new LinkedHashMap<>();
        for (int start = 0; start < finvizSymbols.size(); start += SYMBOLS_PER_REQUEST) {
            List<String> oneRequest = finvizSymbols.subList(start,
                    Math.min(start + SYMBOLS_PER_REQUEST, finvizSymbols.size()));
            String csv = finvizClient.screenAsCsv(NO_FILTERS, null, oneRequest);
            for (FinvizStock finvizStock : new FinvizScreenerReader(today).stocksIn(csv)) {
                String ibSymbol = ibSymbolByFinvizSymbol.get(finvizStock.symbol());
                if (ibSymbol != null) {
                    answersBySymbol.put(ibSymbol, companyFiguresOf(ibSymbol, finvizStock));
                }
            }
        }
        return answersBySymbol;
    }

    /**
     * Finviz has no revenue growth of the last 12 months, Finnhub's figure: its latest quarter against the same quarter a
     * year before stands in for it (decision 12).
     */
    private Stock companyFiguresOf(String ibSymbol, FinvizStock finvizStock) {
        CompanyFacts companyFacts = new CompanyFacts(finvizStock.salesGrowthQuarterOverQuarterPercent(),
                finvizStock.netProfitMarginPercent(), finvizStock.debtToEquity(), finvizStock.pe());
        return new Stock(ibSymbol, finvizStock.nextEarningsDate(), companyFacts, FinvizClient.SOURCE_NAME);
    }

    private String finvizSymbolOf(String ibSymbol) {
        return ibSymbol.trim().replace(' ', '-');
    }
}
