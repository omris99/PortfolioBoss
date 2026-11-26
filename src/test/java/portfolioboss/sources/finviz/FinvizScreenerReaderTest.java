package portfolioboss.sources.finviz;

import org.junit.jupiter.api.Test;
import portfolioboss.exceptions.FinvizUnavailableException;
import portfolioboss.sources.EarningsDate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Finviz's CSV of a screen read into {@link FinvizStock}s. The CSV was not seen before the subscription
 * (RECOMMENDATIONS_FINVIZ_TODO.md, session 0 checks it), so these cases are the forms it may take: the export's column
 * names or the screen's short ones, in any order; percents with "%" or without; a market cap in millions or with its
 * letter; "-" or nothing for a missing value; the earnings date with its time or the screen's "Nov 20/a". Every stock
 * and every value is made up.
 */
class FinvizScreenerReaderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 26);
    private static final FinvizScreenerReader READER = new FinvizScreenerReader(TODAY);

    /** The export's names, in the order of the user's columns ({@code c=} of decision 12). */
    private static final List<String> EXPORT_HEADER = List.of("No.", "Ticker", "Company", "Sector", "Industry",
            "Country", "Market Cap", "P/E", "Price", "Change", "Volume", "Forward P/E", "PEG", "P/S", "P/B", "P/Free Cash Flow",
            "EPS growth past 5 years", "Sales growth past 5 years", "Sales growth quarter over quarter", "Gross Margin",
            "Operating Margin", "Net Profit Margin", "Total Debt/Equity", "Earnings Date", "Target Price",
            "Analyst Recom");

    @Test
    void readsEveryColumnOfTheExportByItsName() {
        String csv = csvOf(EXPORT_HEADER, List.of(
                "1", "VELQ", "Velquor Bio Inc", "Healthcare", "Biotechnology", "USA", "10333.98", "101.54", "68.37",
                "1.25%", "2500530", "24.66", "2.10", "11.51", "9.30", "120.78", "-", "426.73%", "60.74%", "82.74%",
                "21.48%",
                "11.81%", "0.35", "11/20/2026 4:30:00 PM", "92.50", "1.80"));

        List<FinvizStock> stocks = READER.stocksIn(csv);

        assertThat(stocks).containsExactly(new FinvizStock("VELQ", "Velquor Bio Inc", "Healthcare", "Biotechnology",
                "USA", 10333.98, 68.37, 101.54, 24.66, 2.10, 11.51, 9.30, 120.78, null, 426.73, 60.74, 82.74, 21.48,
                11.81, 0.35,
                new EarningsDate(LocalDate.of(2026, 11, 20), EarningsDate.AFTER_THE_MARKET_CLOSES), 92.50, 1.80));
    }

    @Test
    void readsTheScreensShortNamesInAnotherOrder() {
        List<String> shortHeader = List.of("Ticker", "Sales Q/Q", "Debt/Eq", "Earnings", "Company", "Sector",
                "Industry", "Market Cap", "Price", "P/E", "Fwd P/E", "PEG", "P/S", "P/B", "P/FCF", "EPS past 5Y",
                "Sales past 5Y", "Gross M", "Oper M", "Profit M", "Recom");
        String csv = csvOf(shortHeader, List.of("ORVA", "44.9%", "0.8", "Nov 20/a", "Orvana Robotics", "Industrials",
                "Specialty Industrial Machinery", "3.9B", "41.75", "64.2", "30.1", "1.4", "5.5", "3.2", "55.0", "12.0%",
                "158.0%", "58.3%", "15.0%", "9.9%", "2.1"));

        FinvizStock orva = READER.stocksIn(csv).getFirst();

        assertThat(orva.salesGrowthQuarterOverQuarterPercent()).isEqualTo(44.9);
        assertThat(orva.debtToEquity()).isEqualTo(0.8);
        assertThat(orva.forwardPe()).isEqualTo(30.1);
        assertThat(orva.peg()).isEqualTo(1.4);
        assertThat(orva.priceToBook()).isEqualTo(3.2);
        assertThat(orva.priceToFreeCashFlow()).isEqualTo(55.0);
        assertThat(orva.netProfitMarginPercent()).isEqualTo(9.9);
        assertThat(orva.marketCapMillions()).isEqualTo(3900.0);
        assertThat(orva.analystRecommendation()).isEqualTo(2.1);
        assertThat(orva.country()).isNull();
        assertThat(orva.analystTargetPrice()).isNull();
    }

    @Test
    void aPercentIsReadWithItsSignOrWithoutAndAThousandsSeparatorIsSkipped() {
        FinvizStock stock = READER.stocksIn(csvOfOneStockWith("Sales growth past 5 years", "1,234.5%",
                "Sales growth quarter over quarter", "-3.2", "Price", "1,045.20")).getFirst();

        assertThat(stock.salesGrowthPast5YearsPercent()).isEqualTo(1234.5);
        assertThat(stock.salesGrowthQuarterOverQuarterPercent()).isEqualTo(-3.2);
        assertThat(stock.price()).isEqualTo(1045.20);
    }

    @Test
    void aMarketCapIsInMillionsWithOrWithoutItsLetter() {
        assertThat(marketCapMillionsOf("5230.40")).isEqualTo(5230.40);
        assertThat(marketCapMillionsOf("10.33B")).isEqualTo(10330.0);
        assertThat(marketCapMillionsOf("950.2M")).isEqualTo(950.2);
        assertThat(marketCapMillionsOf("2.95T")).isEqualTo(2_950_000.0);
    }

    @Test
    void aMissingValueIsNullNeverZero() {
        FinvizStock stock = READER.stocksIn(csvOfOneStockWith("P/E", "-", "Forward P/E", "", "Earnings Date", "-"))
                .getFirst();

        assertThat(stock.pe()).isNull();
        assertThat(stock.forwardPe()).isNull();
        assertThat(stock.nextEarningsDate()).isNull();
    }

    @Test
    void theEarningsDateWithItsTimeSaysWhenInTheDay() {
        assertThat(earningsDateOf("11/20/2026 4:30:00 PM"))
                .isEqualTo(new EarningsDate(LocalDate.of(2026, 11, 20), EarningsDate.AFTER_THE_MARKET_CLOSES));
        assertThat(earningsDateOf("11/20/2026 8:00:00 AM"))
                .isEqualTo(new EarningsDate(LocalDate.of(2026, 11, 20), EarningsDate.BEFORE_THE_MARKET_OPENS));
        assertThat(earningsDateOf("11/20/2026 12:30 PM"))
                .isEqualTo(new EarningsDate(LocalDate.of(2026, 11, 20), EarningsDate.DURING_THE_TRADING_HOURS));
    }

    /** Midnight, or no time at all, is a date whose hour Finviz doesn't know. */
    @Test
    void theEarningsDateWithoutItsTimeHasNoHour() {
        assertThat(earningsDateOf("11/20/2026 12:00:00 AM"))
                .isEqualTo(new EarningsDate(LocalDate.of(2026, 11, 20), null));
        assertThat(earningsDateOf("11/20/2026")).isEqualTo(new EarningsDate(LocalDate.of(2026, 11, 20), null));
        assertThat(earningsDateOf("2026-11-20")).isEqualTo(new EarningsDate(LocalDate.of(2026, 11, 20), null));
    }

    /** The screen's form has no year: the date nearest to the download's day, before or after it. */
    @Test
    void theScreensEarningsDateTakesTheYearNearestToToday() {
        assertThat(earningsDateOf("Nov 20/a"))
                .isEqualTo(new EarningsDate(LocalDate.of(2026, 11, 20), EarningsDate.AFTER_THE_MARKET_CLOSES));
        assertThat(earningsDateOf("Feb 3/b"))
                .isEqualTo(new EarningsDate(LocalDate.of(2027, 2, 3), EarningsDate.BEFORE_THE_MARKET_OPENS));
        assertThat(earningsDateOf("Jan 28 AMC"))
                .isEqualTo(new EarningsDate(LocalDate.of(2027, 1, 28), EarningsDate.AFTER_THE_MARKET_CLOSES));
        assertThat(earningsDateOf("Dec 2")).isEqualTo(new EarningsDate(LocalDate.of(2026, 12, 2), null));
    }

    @Test
    void aCompanyInQuotesKeepsItsCommasAndQuotes() {
        String csv = "\"Ticker\",\"Company\"," + restOfTheHeader() + "\n"
                + "\"ABCD\",\"Abcd, Inc. \"\"Class A\"\"\"," + restOfARow() + "\n";

        assertThat(READER.stocksIn(csv).getFirst().companyName()).isEqualTo("Abcd, Inc. \"Class A\"");
    }

    @Test
    void aByteOrderMarkCarriageReturnsAndAnEmptyLastLineAreRead() {
        String csv = "﻿" + csvOf(EXPORT_HEADER, aRowOf("ABCD"), aRowOf("WXYZ")).replace("\n", "\r\n") + "\r\n";

        assertThat(READER.stocksIn(csv)).extracting(FinvizStock::symbol).containsExactly("ABCD", "WXYZ");
    }

    @Test
    void aScreenWithoutAStockIsAnEmptyList() {
        assertThat(READER.stocksIn(csvOf(EXPORT_HEADER))).isEmpty();
    }

    @Test
    void aNeededColumnMissingFailsTheScreenNamingIt() {
        List<String> withoutTheDebt = EXPORT_HEADER.stream().filter(name -> !name.equals("Total Debt/Equity")).toList();
        List<String> row = new ArrayList<>(aRowOf("ABCD"));
        row.remove(EXPORT_HEADER.indexOf("Total Debt/Equity"));

        assertThatThrownBy(() -> READER.stocksIn(csvOf(withoutTheDebt, row)))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("Finviz's CSV has no column \"Total Debt/Equity\" (or \"Debt/Eq\"): is it still among the "
                        + "screen's columns?");
    }

    @Test
    void aValueThatIsNotAFigureFailsNamingTheLineTheStockAndTheColumn() {
        assertThatThrownBy(() -> READER.stocksIn(csvOfOneStockWith("P/E", "n/a")))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("line 2 (ABCD) of Finviz's CSV: \"P/E\" is \"n/a\", not a figure");
    }

    @Test
    void anEarningsDateThatIsNotADateFailsNamingIt() {
        assertThatThrownBy(() -> READER.stocksIn(csvOfOneStockWith("Earnings Date", "soon")))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("line 2 (ABCD) of Finviz's CSV: \"Earnings Date\" (or \"Earnings\") is \"soon\", not a date");
    }

    @Test
    void aLineWithAnotherNumberOfFieldsThanTheHeaderFails() {
        List<String> shortRow = aRowOf("ABCD").subList(0, 10);

        assertThatThrownBy(() -> READER.stocksIn(csvOf(EXPORT_HEADER, shortRow)))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("line 2 of Finviz's CSV has 10 fields, its header 26");
    }

    /** What an export answers when the token isn't accepted may be the site's login page. */
    @Test
    void aWebPageIsNotAScreen() {
        assertThatThrownBy(() -> READER.stocksIn("<!DOCTYPE html>\n<html><body>Login</body></html>"))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("Finviz's answer is not a screen's CSV: its first line has no \"Ticker\" column — it "
                        + "looks like a web page, as when the token isn't accepted");
    }

    @Test
    void anEmptyAnswerFails() {
        assertThatThrownBy(() -> READER.stocksIn(""))
                .isInstanceOf(FinvizUnavailableException.class)
                .hasMessage("Finviz's answer is empty");
    }

    // ── the made-up CSV ─────────────────────────────────────────────────────────────────────────────

    /** A healthy made-up stock, every column of {@link #EXPORT_HEADER} filled. */
    private List<String> aRowOf(String ticker) {
        return List.of("1", ticker, ticker + " Inc", "Technology", "Software - Application", "USA", "5230.40", "34.10",
                "82.15", "1.20%", "1203400", "28.40", "1.80", "6.10", "4.20", "30.20", "12.50%", "18.30%", "9.40%", "71.20%",
                "22.10%", "15.30%", "0.42", "11/20/2026 4:30:00 PM", "95.00", "1.90");
    }

    /** ABCD's row with the given columns' values in place of its own: "P/E", "n/a", "Price", "1,045.20". */
    private String csvOfOneStockWith(String... columnsAndValues) {
        List<String> row = new ArrayList<>(aRowOf("ABCD"));
        for (int index = 0; index < columnsAndValues.length; index += 2) {
            row.set(EXPORT_HEADER.indexOf(columnsAndValues[index]), columnsAndValues[index + 1]);
        }
        return csvOf(EXPORT_HEADER, row);
    }

    private Double marketCapMillionsOf(String marketCap) {
        return READER.stocksIn(csvOfOneStockWith("Market Cap", marketCap)).getFirst().marketCapMillions();
    }

    private EarningsDate earningsDateOf(String earningsDate) {
        return READER.stocksIn(csvOfOneStockWith("Earnings Date", earningsDate)).getFirst().nextEarningsDate();
    }

    /** The header after "Ticker" and "Company", for a row written by hand. */
    private String restOfTheHeader() {
        return quoted(EXPORT_HEADER.subList(3, EXPORT_HEADER.size()));
    }

    private String restOfARow() {
        return quoted(aRowOf("ABCD").subList(3, EXPORT_HEADER.size()));
    }

    /** Every field in quotes, as Finviz's export writes them. */
    @SafeVarargs
    private String csvOf(List<String> header, List<String>... rows) {
        StringBuilder csv = new StringBuilder(quoted(header)).append('\n');
        for (List<String> row : rows) {
            csv.append(quoted(row)).append('\n');
        }
        return csv.toString();
    }

    private String quoted(List<String> fields) {
        return fields.stream().map(field -> "\"" + field + "\"").collect(Collectors.joining(","));
    }
}
