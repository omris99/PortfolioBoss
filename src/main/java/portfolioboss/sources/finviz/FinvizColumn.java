package portfolioboss.sources.finviz;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The columns {@link FinvizScreenerReader} reads, each under every name it may have in the CSV: the export's full name
 * first, then the screen's short one ("Sales growth quarter over quarter", "Sales Q/Q"). A name is matched in any case
 * and spacing. A column the app needs fails the screen when it is missing; the country and the two analysts' columns are
 * only looked at, and may be missing.
 */
enum FinvizColumn {

    TICKER(true, "Ticker"),
    COMPANY(true, "Company"),
    SECTOR(true, "Sector"),
    INDUSTRY(true, "Industry"),
    COUNTRY(false, "Country"),
    MARKET_CAP(true, "Market Cap"),
    PRICE(true, "Price"),
    PE(true, "P/E"),
    FORWARD_PE(true, "Forward P/E", "Fwd P/E"),
    PEG(true, "PEG"),
    PRICE_TO_SALES(true, "P/S"),
    PRICE_TO_BOOK(true, "P/B"),
    PRICE_TO_FREE_CASH_FLOW(true, "P/Free Cash Flow", "P/FCF"),
    EPS_GROWTH_PAST_5_YEARS(true, "EPS growth past 5 years", "EPS past 5Y"),
    SALES_GROWTH_PAST_5_YEARS(true, "Sales growth past 5 years", "Sales past 5Y"),
    SALES_GROWTH_QUARTER_OVER_QUARTER(true, "Sales growth quarter over quarter", "Sales Q/Q"),
    GROSS_MARGIN(true, "Gross Margin", "Gross M"),
    OPERATING_MARGIN(true, "Operating Margin", "Oper M"),
    NET_PROFIT_MARGIN(true, "Net Profit Margin", "Profit Margin", "Profit M"),
    DEBT_TO_EQUITY(true, "Total Debt/Equity", "Debt/Eq"),
    EARNINGS_DATE(true, "Earnings Date", "Earnings"),
    TARGET_PRICE(false, "Target Price"),
    ANALYST_RECOMMENDATION(false, "Analyst Recom", "Recom");

    /** Every name of every column, as {@link #comparableFormOf} writes it. Filled once, when the enum is loaded. */
    private static final Map<String, FinvizColumn> COLUMN_BY_NAME = new HashMap<>();

    static {
        for (FinvizColumn column : values()) {
            column.names.forEach(name -> COLUMN_BY_NAME.put(comparableFormOf(name), column));
        }
    }

    private final boolean needed;
    private final List<String> names;

    private FinvizColumn(boolean needed, String... names) {
        this.needed = needed;
        this.names = List.of(names);
    }

    /** The column a header names, or {@code null} for one the app doesn't read ("No.", "Change", "Volume"). */
    protected static FinvizColumn named(String header) {
        return COLUMN_BY_NAME.get(comparableFormOf(header));
    }

    protected boolean isNeeded() {
        return needed;
    }

    /** For a message: "\"Total Debt/Equity\" (or \"Debt/Eq\")". */
    protected String namesInWords() {
        String firstName = "\"" + names.getFirst() + "\"";
        if (names.size() == 1) {
            return firstName;
        }
        return firstName + names.stream().skip(1)
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(", ", " (or ", ")"));
    }

    /** Lower case, letters, digits and "/" only: "Sales Q/Q" and "sales q/q" are the same name. */
    private static String comparableFormOf(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/]", "");
    }
}
