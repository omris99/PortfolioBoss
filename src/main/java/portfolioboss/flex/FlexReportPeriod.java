package portfolioboss.flex;

import java.time.LocalDate;

/**
 * The days one Flex report covers, both ends included: what a sync asks IB for, and what IB says its report covered.
 *
 * @param fromDate the first day
 * @param toDate   the last day, not before {@code fromDate}
 */
public record FlexReportPeriod(LocalDate fromDate, LocalDate toDate) {

    public FlexReportPeriod {
        if (toDate.isBefore(fromDate)) {
            throw new IllegalArgumentException("A period can't end (" + toDate + ") before it starts (" + fromDate + ")");
        }
    }

    /** How the console writes it: "2025-11-14 – 2026-11-13". */
    @Override
    public String toString() {
        return fromDate + " – " + toDate;
    }
}
