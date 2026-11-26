package portfolioboss.sources.finviz;

import org.junit.jupiter.api.Test;
import portfolioboss.sources.CompanyFacts;
import portfolioboss.sources.EarningsDate;
import portfolioboss.sources.Stock;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Finviz as the first source of the company figures (RECOMMENDATIONS_FINVIZ_TODO.md, decision 9), with
 * {@link FinvizClient} a stand-in that answers made-up screens: one request for every holding, IB's symbols turned into
 * Finviz's and back, the four figures out of Finviz's columns — the sales of the latest quarter against a year before
 * standing in for the revenue growth (decision 12) —, and a stock without a row left out for the next source.
 */
class FinvizCompanyFiguresTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final String HEADER = String.join(",", "\"Ticker\"", "\"Company\"", "\"Sector\"", "\"Industry\"",
            "\"Market Cap\"", "\"Price\"", "\"P/E\"", "\"Forward P/E\"", "\"PEG\"", "\"P/S\"", "\"P/B\"",
            "\"P/Free Cash Flow\"",
            "\"EPS growth past 5 years\"", "\"Sales growth past 5 years\"", "\"Sales growth quarter over quarter\"",
            "\"Gross Margin\"", "\"Operating Margin\"", "\"Net Profit Margin\"", "\"Total Debt/Equity\"",
            "\"Earnings Date\"");

    private final FinvizClient finvizClient = mock(FinvizClient.class);
    private final FinvizCompanyFigures finvizCompanyFigures = new FinvizCompanyFigures(finvizClient);

    @Test
    void everyStockIsAskedForInOneRequestAndAnsweredByIbsSymbol() {
        given(finvizClient.screenAsCsv(eq(""), isNull(), eq(List.of("ABCD", "BRK-B"))))
                .willReturn(screenOf(rowOf("ABCD", "9.40%", "15.30%", "0.42", "34.10", "11/20/2026 4:30:00 PM"),
                        rowOf("BRK-B", "-2.10%", "18.00%", "0.25", "15.20", "-")));

        Map<String, Stock> answers = finvizCompanyFigures.answersFor(List.of("ABCD", "BRK B"), TODAY);

        assertThat(answers).containsExactly(
                Map.entry("ABCD", new Stock("ABCD",
                        new EarningsDate(LocalDate.of(2026, 11, 20), EarningsDate.AFTER_THE_MARKET_CLOSES),
                        new CompanyFacts(9.40, 15.30, 0.42, 34.10), "Finviz")),
                Map.entry("BRK B", new Stock("BRK B", null, new CompanyFacts(-2.10, 18.00, 0.25, 15.20), "Finviz")));
    }

    @Test
    void aStockFinvizHasNoRowForIsLeftOutAndARowNotAskedForIsSkipped() {
        given(finvizClient.screenAsCsv(eq(""), isNull(), anyList()))
                .willReturn(screenOf(rowOf("ABCD", "9.40%", "15.30%", "0.42", "34.10", "-"),
                        rowOf("OTHER", "1.00%", "1.00%", "1.00", "10.00", "-")));

        Map<String, Stock> answers = finvizCompanyFigures.answersFor(List.of("ABCD", "NONE"), TODAY);

        assertThat(answers).containsOnlyKeys("ABCD");
    }

    /** An address of a hundred symbols stays short: 150 holdings are two requests. */
    @Test
    void moreThanAHundredStocksAreAskedForAHundredAtATime() {
        List<String> symbols = IntStream.rangeClosed(1, 150).mapToObj(number -> "S" + number).toList();
        given(finvizClient.screenAsCsv(eq(""), isNull(), anyList())).willReturn(screenOf());

        finvizCompanyFigures.answersFor(symbols, TODAY);

        verify(finvizClient).screenAsCsv("", null, symbols.subList(0, 100));
        verify(finvizClient).screenAsCsv("", null, symbols.subList(100, 150));
        verify(finvizClient, times(2)).screenAsCsv(eq(""), isNull(), anyList());
    }

    @Test
    void itIsConnectedWhileFinvizsTokenIsSet() {
        given(finvizClient.hasToken()).willReturn(true);
        assertThat(finvizCompanyFigures.isConnected()).isTrue();

        given(finvizClient.hasToken()).willReturn(false);
        assertThat(finvizCompanyFigures.isConnected()).isFalse();
        assertThat(finvizCompanyFigures.sourceName()).isEqualTo("Finviz");
    }

    /** A made-up stock: only the four figures and the date differ from one row to another. */
    private String rowOf(String ticker, String salesQuarterOverQuarter, String netMargin, String debtToEquity,
                         String pe, String earningsDate) {
        return String.join(",", "\"" + ticker + "\"", "\"" + ticker + " Inc\"", "\"Technology\"", "\"Software\"",
                "\"5230.40\"", "\"82.15\"", "\"" + pe + "\"", "\"28.40\"", "\"1.80\"", "\"6.10\"", "\"4.20\"",
                "\"30.20\"", "\"12.50%\"",
                "\"18.30%\"", "\"" + salesQuarterOverQuarter + "\"", "\"71.20%\"", "\"22.10%\"",
                "\"" + netMargin + "\"", "\"" + debtToEquity + "\"", "\"" + earningsDate + "\"");
    }

    private String screenOf(String... rows) {
        StringBuilder csv = new StringBuilder(HEADER).append('\n');
        for (String row : rows) {
            csv.append(row).append('\n');
        }
        return csv.toString();
    }
}
