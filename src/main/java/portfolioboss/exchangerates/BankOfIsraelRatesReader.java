package portfolioboss.exchangerates;

import portfolioboss.exceptions.ExchangeRatesUnavailableException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the CSV the Bank of Israel answers a request for rates with: a header line naming the columns, then a line per
 * day — 15 columns, of which two are read, {@code TIME_PERIOD} (the day, "2025-10-03") and {@code OBS_VALUE} (the rate,
 * "3.315"). The columns are found by their names, not their places, so a column added or moved changes nothing.
 *
 * <p>No line of the dollar's rates of 2020–2026 had quotes or an empty value (checked 2026-11-15). A line with another
 * number of fields than the header — a comma inside a quoted comment, say — would shift the columns, so it fails the
 * whole answer naming the line, rather than storing a wrong rate.
 */
class BankOfIsraelRatesReader {

    private static final String FIELD_SEPARATOR = ",";
    private static final String DATE_COLUMN_NAME = "TIME_PERIOD";
    private static final String RATE_COLUMN_NAME = "OBS_VALUE";

    protected List<BankOfIsraelRate> readRates(String csvText) {
        List<String> lines = csvText.lines()
                .filter(line -> !line.isBlank())
                .toList();
        if (lines.isEmpty()) {
            throw new ExchangeRatesUnavailableException("the Bank of Israel's answer is empty");
        }
        List<String> columnNames = fieldsOf(lines.getFirst());
        int dateColumnIndex = indexOfColumn(columnNames, DATE_COLUMN_NAME);
        int rateColumnIndex = indexOfColumn(columnNames, RATE_COLUMN_NAME);

        List<BankOfIsraelRate> rates = new ArrayList<>();
        for (int lineIndex = 1; lineIndex < lines.size(); lineIndex++) {
            int lineNumber = lineIndex + 1;
            List<String> fields = fieldsOf(lines.get(lineIndex));
            if (fields.size() != columnNames.size()) {
                throw new ExchangeRatesUnavailableException("line " + lineNumber + " of the Bank of Israel's answer has "
                        + fields.size() + " fields, its header " + columnNames.size());
            }
            rates.add(new BankOfIsraelRate(
                    dateOf(fields.get(dateColumnIndex), lineNumber),
                    rateOf(fields.get(rateColumnIndex), lineNumber)));
        }
        return rates;
    }

    /** The line's fields, empty ones kept: {@code split}'s limit of -1 keeps the empty fields at the end of a line. */
    private List<String> fieldsOf(String line) {
        return List.of(line.split(FIELD_SEPARATOR, -1));
    }

    private int indexOfColumn(List<String> columnNames, String columnName) {
        int columnIndex = columnNames.indexOf(columnName);
        if (columnIndex < 0) {
            throw new ExchangeRatesUnavailableException("the Bank of Israel's answer has no " + columnName + " column");
        }
        return columnIndex;
    }

    private LocalDate dateOf(String dateText, int lineNumber) {
        try {
            return LocalDate.parse(dateText);
        } catch (DateTimeParseException e) {
            throw new ExchangeRatesUnavailableException("line " + lineNumber + " of the Bank of Israel's answer has a "
                    + DATE_COLUMN_NAME + " that isn't a date: \"" + dateText + "\"");
        }
    }

    private BigDecimal rateOf(String rateText, int lineNumber) {
        try {
            return new BigDecimal(rateText);
        } catch (NumberFormatException e) {
            throw new ExchangeRatesUnavailableException("line " + lineNumber + " of the Bank of Israel's answer has an "
                    + RATE_COLUMN_NAME + " that isn't a number: \"" + rateText + "\"");
        }
    }
}
