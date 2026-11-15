package portfolioboss.tax;

import java.util.List;

/**
 * One Flex report, as IB's Flex Web Service sent it: the days it covers and every row of its two sections.
 *
 * @param period           the days the report covers, as IB wrote them in it — not necessarily the days asked for
 * @param trades           the Trades section, in IB's order (by symbol, not by time)
 * @param cashTransactions the Cash Transactions section, in IB's order
 */
public record FlexReport(FlexReportPeriod period, List<FlexReportTrade> trades,
                         List<FlexReportCashTransaction> cashTransactions) {
}
