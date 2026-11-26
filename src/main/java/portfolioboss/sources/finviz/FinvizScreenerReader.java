package portfolioboss.sources.finviz;

import portfolioboss.exceptions.FinvizUnavailableException;
import portfolioboss.sources.EarningsDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A screen Finviz exported as CSV, read into {@link FinvizStock}s (RECOMMENDATIONS_FINVIZ_TODO.md, session 1). Finviz's
 * column names live only here. The CSV was not seen before the subscription, so the reader takes what can be told apart
 * safely: each column is found by its name — the export's full name or the screen's short one ("Sales growth quarter
 * over quarter" or "Sales Q/Q"), in any case and spacing —, in any order; a percent with its "%" or without; a market cap
 * in millions or with its letter ("10.33B"); a thousands separator; "-" or nothing for a value Finviz doesn't have; and
 * an earnings date in the export's form ("11/20/2026 4:30:00 PM") or the screen's ("Nov 20/a", "Nov 20 AMC"). Anything
 * else fails the whole screen, naming the column and the row, the way {@code FlexReportReader} does: a figure read wrong
 * would be worse than none.
 *
 * @param today the day of the download: a date Finviz writes without its year is the one nearest to it
 */
public record FinvizScreenerReader(LocalDate today) {

    /** A date without a year more than this far from {@code today} belongs to the year before or after. */
    private static final int MONTHS_A_DATE_WITHOUT_A_YEAR_IS_FROM_TODAY = 6;
    private static final String FINVIZ_MISSING_VALUE = "-";
    private static final String BYTE_ORDER_MARK = "﻿";
    /** The US market opens at 9:30 and closes at 16:00, New York's time — the time Finviz writes. */
    private static final LocalTime MARKET_OPEN = LocalTime.of(9, 30);
    private static final LocalTime MARKET_CLOSE = LocalTime.of(16, 0);

    private static final List<DateTimeFormatter> DATE_AND_TIME_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm:ss a", Locale.US),
            DateTimeFormatter.ofPattern("M/d/yyyy h:mm a", Locale.US));
    private static final List<DateTimeFormatter> DATE_FORMATS = List.of(
            DateTimeFormatter.ofPattern("M/d/yyyy", Locale.US),
            DateTimeFormatter.ISO_LOCAL_DATE);
    private static final DateTimeFormatter MONTH_AND_DAY_FORMAT = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendPattern("MMM d")
            .toFormatter(Locale.US);
    /** "Nov 20", "Nov 20/a" (after the close), "Nov 20/b" (before the open), "Nov 20 AMC", "Nov 20 BMO". */
    private static final Pattern MONTH_AND_DAY_WITH_ITS_HOUR = Pattern.compile(
            "([A-Za-z]{3} \\d{1,2})(?:\\s*/\\s*([abAB])|\\s+(AMC|BMO|amc|bmo))?");

    /**
     * Every stock of the screen, in Finviz's order.
     *
     * @throws FinvizUnavailableException when the answer isn't a screen's CSV, a column the app needs is missing, a row
     *                                    has more or fewer fields than the header, or a value can't be read
     */
    public List<FinvizStock> stocksIn(String csv) {
        String text = csv == null ? "" : csv;
        if (text.startsWith(BYTE_ORDER_MARK)) {
            text = text.substring(BYTE_ORDER_MARK.length());
        }
        List<List<String>> rows = rowsOf(text);
        if (rows.isEmpty()) {
            throw new FinvizUnavailableException("Finviz's answer is empty");
        }
        Map<FinvizColumn, Integer> placeOfEachColumn = placeOfEachColumnIn(rows.getFirst(), text);
        int headerFieldCount = rows.getFirst().size();
        List<FinvizStock> stocks = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> fields = rows.get(rowIndex);
            if (isBlankLine(fields)) {
                continue;
            }
            int lineNumber = rowIndex + 1;
            if (fields.size() != headerFieldCount) {
                throw new FinvizUnavailableException("line " + lineNumber + " of Finviz's CSV has " + fields.size()
                        + " fields, its header " + headerFieldCount);
            }
            stocks.add(stockIn(new CsvRow(lineNumber, fields, placeOfEachColumn)));
        }
        return stocks;
    }

    private FinvizStock stockIn(CsvRow row) {
        return new FinvizStock(
                row.requiredTextOf(FinvizColumn.TICKER),
                row.textOf(FinvizColumn.COMPANY),
                row.textOf(FinvizColumn.SECTOR),
                row.textOf(FinvizColumn.INDUSTRY),
                row.textOf(FinvizColumn.COUNTRY),
                row.marketCapMillionsOf(FinvizColumn.MARKET_CAP),
                row.figureOf(FinvizColumn.PRICE),
                row.figureOf(FinvizColumn.PE),
                row.figureOf(FinvizColumn.FORWARD_PE),
                row.figureOf(FinvizColumn.PEG),
                row.figureOf(FinvizColumn.PRICE_TO_SALES),
                row.figureOf(FinvizColumn.PRICE_TO_BOOK),
                row.figureOf(FinvizColumn.PRICE_TO_FREE_CASH_FLOW),
                row.figureOf(FinvizColumn.EPS_GROWTH_PAST_5_YEARS),
                row.figureOf(FinvizColumn.SALES_GROWTH_PAST_5_YEARS),
                row.figureOf(FinvizColumn.SALES_GROWTH_QUARTER_OVER_QUARTER),
                row.figureOf(FinvizColumn.GROSS_MARGIN),
                row.figureOf(FinvizColumn.OPERATING_MARGIN),
                row.figureOf(FinvizColumn.NET_PROFIT_MARGIN),
                row.figureOf(FinvizColumn.DEBT_TO_EQUITY),
                earningsDateIn(row),
                row.figureOf(FinvizColumn.TARGET_PRICE),
                row.figureOf(FinvizColumn.ANALYST_RECOMMENDATION));
    }

    /** Where each column is; one the app needs that isn't there fails the screen, one it can do without is left out. */
    private Map<FinvizColumn, Integer> placeOfEachColumnIn(List<String> header, String text) {
        Map<FinvizColumn, Integer> placeOfEachColumn = new EnumMap<>(FinvizColumn.class);
        for (int place = 0; place < header.size(); place++) {
            FinvizColumn column = FinvizColumn.named(header.get(place));
            if (column != null && !placeOfEachColumn.containsKey(column)) {
                placeOfEachColumn.put(column, place);
            }
        }
        if (!placeOfEachColumn.containsKey(FinvizColumn.TICKER)) {
            String looksLike = text.stripLeading().startsWith("<")
                    ? " — it looks like a web page, as when the token isn't accepted"
                    : "";
            throw new FinvizUnavailableException(
                    "Finviz's answer is not a screen's CSV: its first line has no \"Ticker\" column" + looksLike);
        }
        for (FinvizColumn column : FinvizColumn.values()) {
            if (column.isNeeded() && !placeOfEachColumn.containsKey(column)) {
                throw new FinvizUnavailableException("Finviz's CSV has no column " + column.namesInWords()
                        + ": is it still among the screen's columns?");
            }
        }
        return placeOfEachColumn;
    }

    /** The export's form, with the time; a date alone; or the screen's, without the year. {@code null} for none. */
    private EarningsDate earningsDateIn(CsvRow row) {
        String value = row.textOf(FinvizColumn.EARNINGS_DATE);
        if (value == null) {
            return null;
        }
        for (DateTimeFormatter format : DATE_AND_TIME_FORMATS) {
            try {
                LocalDateTime dateAndTime = LocalDateTime.parse(value, format);
                return new EarningsDate(dateAndTime.toLocalDate(), hourOf(dateAndTime.toLocalTime()));
            } catch (DateTimeParseException e) {
                // not this form: the next one is tried
            }
        }
        for (DateTimeFormatter format : DATE_FORMATS) {
            try {
                return new EarningsDate(LocalDate.parse(value, format), null);
            } catch (DateTimeParseException e) {
                // not this form: the next one is tried
            }
        }
        Matcher monthAndDayWithItsHour = MONTH_AND_DAY_WITH_ITS_HOUR.matcher(value);
        if (monthAndDayWithItsHour.matches()) {
            try {
                MonthDay monthAndDay = MonthDay.parse(monthAndDayWithItsHour.group(1), MONTH_AND_DAY_FORMAT);
                return new EarningsDate(nearestToToday(monthAndDay),
                        hourOf(monthAndDayWithItsHour.group(2), monthAndDayWithItsHour.group(3)));
            } catch (DateTimeParseException e) {
                // falls through to the failure below
            }
        }
        throw row.unreadable(FinvizColumn.EARNINGS_DATE, value, "not a date");
    }

    /** Midnight is a date without its hour; otherwise before the open, after the close, or during the trading hours. */
    private String hourOf(LocalTime time) {
        if (time.equals(LocalTime.MIDNIGHT)) {
            return null;
        }
        if (time.isBefore(MARKET_OPEN)) {
            return EarningsDate.BEFORE_THE_MARKET_OPENS;
        }
        if (time.isBefore(MARKET_CLOSE)) {
            return EarningsDate.DURING_THE_TRADING_HOURS;
        }
        return EarningsDate.AFTER_THE_MARKET_CLOSES;
    }

    /** "a" or "AMC" after the close, "b" or "BMO" before the open; {@code null} when the date has neither. */
    private String hourOf(String letter, String word) {
        if ("a".equalsIgnoreCase(letter) || "AMC".equalsIgnoreCase(word)) {
            return EarningsDate.AFTER_THE_MARKET_CLOSES;
        }
        if ("b".equalsIgnoreCase(letter) || "BMO".equalsIgnoreCase(word)) {
            return EarningsDate.BEFORE_THE_MARKET_OPENS;
        }
        return null;
    }

    private LocalDate nearestToToday(MonthDay monthAndDay) {
        LocalDate thisYears = monthAndDay.atYear(today.getYear());
        if (thisYears.isBefore(today.minusMonths(MONTHS_A_DATE_WITHOUT_A_YEAR_IS_FROM_TODAY))) {
            return monthAndDay.atYear(today.getYear() + 1);
        }
        if (thisYears.isAfter(today.plusMonths(MONTHS_A_DATE_WITHOUT_A_YEAR_IS_FROM_TODAY))) {
            return monthAndDay.atYear(today.getYear() - 1);
        }
        return thisYears;
    }

    /**
     * The CSV's rows and their fields: a field may be in quotes, with commas and doubled quotes ({@code ""}) inside it;
     * a line ends with a line feed, with or without a carriage return before it.
     */
    private List<List<String>> rowsOf(String text) {
        List<List<String>> rows = new ArrayList<>();
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean insideQuotes = false;
        int index = 0;
        while (index < text.length()) {
            char character = text.charAt(index);
            boolean nextIsAQuote = index + 1 < text.length() && text.charAt(index + 1) == '"';
            if (insideQuotes && character == '"' && nextIsAQuote) {
                field.append('"');
                index++;
            } else if (character == '"') {
                insideQuotes = !insideQuotes;
            } else if (insideQuotes || (character != ',' && character != '\n' && character != '\r')) {
                field.append(character);
            } else if (character == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (character == '\n' || !(index + 1 < text.length() && text.charAt(index + 1) == '\n')) {
                // a line feed, or a carriage return alone; a carriage return before a line feed waits for it
                fields.add(field.toString());
                field.setLength(0);
                rows.add(fields);
                fields = new ArrayList<>();
            }
            index++;
        }
        if (field.length() > 0 || !fields.isEmpty()) {
            fields.add(field.toString());
            rows.add(fields);
        }
        return rows;
    }

    private boolean isBlankLine(List<String> fields) {
        return fields.stream().allMatch(String::isBlank);
    }

    /** One stock's line: its number in the CSV (the header is line 1), its fields, and where each column is. */
    private record CsvRow(int lineNumber, List<String> fields, Map<FinvizColumn, Integer> placeOfEachColumn) {

        /** The column's text, trimmed; {@code null} for "-", for nothing, and for a column the CSV doesn't have. */
        private String textOf(FinvizColumn column) {
            Integer place = placeOfEachColumn.get(column);
            if (place == null) {
                return null;
            }
            String value = fields.get(place).trim();
            return value.isEmpty() || value.equals(FINVIZ_MISSING_VALUE) ? null : value;
        }

        private String requiredTextOf(FinvizColumn column) {
            String value = textOf(column);
            if (value == null) {
                throw new FinvizUnavailableException("line " + lineNumber + " of Finviz's CSV has no "
                        + column.namesInWords());
            }
            return value;
        }

        /** "27.50%", "27.5", "1,234.5", "$12.30" — or {@code null}. */
        private Double figureOf(FinvizColumn column) {
            String value = textOf(column);
            if (value == null) {
                return null;
            }
            return numberIn(column, value, value.replace(",", "").replace("%", "").replace("$", "").trim());
        }

        /** "2950103.42" — millions, the export's form — or "10.33B", "950.2M", "2.95T", "800K": in millions. */
        private Double marketCapMillionsOf(FinvizColumn column) {
            String value = textOf(column);
            if (value == null) {
                return null;
            }
            String digits = value.replace(",", "").replace("$", "").trim();
            char lastCharacter = Character.toUpperCase(digits.charAt(digits.length() - 1));
            double millionsInOne = switch (lastCharacter) {
                case 'T' -> 1_000_000;
                case 'B' -> 1_000;
                case 'M' -> 1;
                case 'K' -> 0.001;
                default -> 0;
            };
            if (millionsInOne == 0) {
                return numberIn(column, value, digits);
            }
            return numberIn(column, value, digits.substring(0, digits.length() - 1)) * millionsInOne;
        }

        private Double numberIn(FinvizColumn column, String value, String digits) {
            try {
                return Double.parseDouble(digits);
            } catch (NumberFormatException e) {
                throw unreadable(column, value, "not a figure");
            }
        }

        private FinvizUnavailableException unreadable(FinvizColumn column, String value, String why) {
            String ticker = textOf(FinvizColumn.TICKER);
            String stock = ticker == null ? "" : " (" + ticker + ")";
            return new FinvizUnavailableException("line " + lineNumber + stock + " of Finviz's CSV: "
                    + column.namesInWords() + " is \"" + value + "\", " + why);
        }
    }
}
