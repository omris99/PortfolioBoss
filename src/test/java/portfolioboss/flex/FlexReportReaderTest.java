package portfolioboss.flex;

import org.junit.jupiter.api.Test;
import portfolioboss.exceptions.FlexReportUnavailableException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * IB's Flex answers read into records. The report has the shape of the real one — the query of
 * CAPITAL_GAINS_TAX_TODO.md, session 1.1, run on 2026-11-14, with the Corporate Actions section and {@code fifoPnlRealized}
 * of session 3.0, run on 2026-11-15 — with made-up values (symbols, ids, quantities, prices, amounts) and without the
 * account, which is neither read nor ever written into the repo (decision 9). It keeps fields the reader skips
 * ({@code description}, {@code tradeMoney}, {@code proceeds}, {@code period}), to show they are skipped. The request
 * statuses have the shapes of IB's documentation.
 */
class FlexReportReaderTest {

    private static final String REPORT = """
            <FlexQueryResponse queryName="Cash and Trades" type="AF">
            <FlexStatements count="1">
            <FlexStatement fromDate="20251114" toDate="20261113" period="Last365CalendarDays" whenGenerated="20261114;063800">
            <Trades>
            <Trade tradeDate="20261012" symbol="ABCD" description="ABCD &amp; SONS INC" assetCategory="STK" currency="USD" \
            quantity="-20" tradePrice="150.5" tradeMoney="-3010" proceeds="3010" ibCommission="-5" \
            ibCommissionCurrency="USD" netCash="3005" settleDateTarget="20261013" buySell="SELL" \
            transactionType="ExchTrade" tradeID="1000000001" conid="1111" ibOrderID="2000000001" dateTime="20261012;143015" \
            fifoPnlRealized="245.512345" />
            <Trade tradeDate="20260601" symbol="WXYZ" description="WXYZ CORP" assetCategory="STK" currency="USD" \
            quantity="100" tradePrice="42.25" tradeMoney="4225" proceeds="-4225" ibCommission="-5" \
            ibCommissionCurrency="USD" netCash="-4230" settleDateTarget="20260602" buySell="BUY" \
            transactionType="ExchTrade" tradeID="1000000002" conid="2222" ibOrderID="2000000002" dateTime="20260601;101500" \
            fifoPnlRealized="0" />
            <Trade tradeDate="20251117" symbol="USD.HKD" description="USD.HKD" assetCategory="CASH" currency="HKD" \
            quantity="5000" tradePrice="7.8" tradeMoney="39000" proceeds="-39000" \
            ibCommission="-2.5" ibCommissionCurrency="USD" netCash="0" settleDateTarget="20251119" buySell="BUY" \
            transactionType="ExchTrade" tradeID="1000000003" conid="3333" ibOrderID="2000000003" dateTime="20251117;101500" />
            </Trades>
            <CorporateActions>
            <CorporateAction assetCategory="STK" symbol="WXYZ" \
            description="WXYZ(US0000000001) SPLIT 1 FOR 5 (WXYZ, WXYZ CORP, US0000000002)" conid="2222" \
            dateTime="20260318;202500" amount="0" proceeds="0" quantity="100" type="RS" transactionID="4000000001" \
            actionID="5000000001" levelOfDetail="DETAIL" />
            <CorporateAction assetCategory="STK" symbol="WXYZ.OLD" \
            description="WXYZ(US0000000001) SPLIT 1 FOR 5 (WXYZ.OLD, WXYZ CORP, US0000000001)" conid="4444" \
            dateTime="20260318;202500" amount="0" proceeds="0" quantity="-500" type="RS" transactionID="4000000002" \
            actionID="5000000001" levelOfDetail="DETAIL" />
            <CorporateAction assetCategory="STK" symbol="WXYZ" description="WXYZ SPLIT 1 FOR 5" conid="2222" \
            dateTime="20260318;202500" amount="0" proceeds="0" quantity="100" type="RS" transactionID="4000000003" \
            actionID="5000000001" levelOfDetail="SUMMARY" />
            </CorporateActions>
            <CashTransactions>
            <CashTransaction dateTime="20260915;202000" type="Dividends" \
            description="WXYZ(US0000000000) CASH DIVIDEND USD 0.12 PER SHARE (Ordinary Dividend)" symbol="WXYZ" \
            currency="USD" amount="12" assetCategory="STK" settleDate="20260915" transactionID="3000000001" conid="2222" />
            <CashTransaction dateTime="20260915;202000" type="Withholding Tax" \
            description="WXYZ(US0000000000) CASH DIVIDEND USD 0.12 PER SHARE - US TAX" symbol="WXYZ" currency="USD" \
            amount="-3" assetCategory="STK" settleDate="20260915" transactionID="3000000002" conid="2222" />
            <CashTransaction dateTime="20251204;170412" type="Other Fees" description="X******00:MARKETDATA FOR DEC 2025" \
            symbol="" currency="USD" amount="-7.5" assetCategory="" settleDate="20251204" transactionID="3000000003" conid="" />
            <CashTransaction dateTime="20260105;133931" type="Other Fees" \
            description="CANCEL[X******00:MARKETDATA] FOR DEC 2025" symbol="" currency="USD" amount="7.5" assetCategory="" \
            settleDate="20260105" transactionID="3000000004" conid="" />
            </CashTransactions>
            </FlexStatement>
            </FlexStatements>
            </FlexQueryResponse>
            """;

    private final FlexReportReader flexReportReader = new FlexReportReader();

    @Test
    void aSaleIsReadWithIbsSigns() {
        FlexReportTrade sale = flexReportReader.readReport(bytesOf(REPORT)).trades().get(0);

        assertThat(sale).isEqualTo(new FlexReportTrade("1000000001", "2000000001", 1111, "ABCD", "STK", "USD",
                LocalDate.of(2026, 10, 12), LocalDateTime.of(2026, 10, 12, 14, 30, 15), LocalDate.of(2026, 10, 13),
                "SELL", new BigDecimal("-20"), new BigDecimal("150.5"), new BigDecimal("-5"), "USD",
                new BigDecimal("3005"), "ExchTrade", new BigDecimal("245.512345")));
    }

    /** IB's FIFO gain is only for the check against our FIFO: a report without it is still read. */
    @Test
    void aTradeWithoutIbsFifoGainIsStillRead() {
        FlexReportTrade conversion = flexReportReader.readReport(bytesOf(REPORT)).trades().get(2);

        assertThat(conversion.fifoPnlRealized()).isNull();
    }

    /** IB writes an action as rows sharing its actionID; should summary rows come too, only the detail rows count. */
    @Test
    void theDetailRowsOfACorporateActionAreRead() {
        FlexReport report = flexReportReader.readReport(bytesOf(REPORT));

        assertThat(report.corporateActions()).extracting(FlexReportCorporateAction::ibTransactionId)
                .containsExactly("4000000001", "4000000002");
        assertThat(report.corporateActions().get(1)).isEqualTo(new FlexReportCorporateAction("4000000002",
                "5000000001", "RS", 4444, "WXYZ.OLD", "STK", LocalDateTime.of(2026, 3, 18, 20, 25, 0),
                new BigDecimal("-500"), BigDecimal.ZERO, BigDecimal.ZERO,
                "WXYZ(US0000000001) SPLIT 1 FOR 5 (WXYZ.OLD, WXYZ CORP, US0000000001)"));
    }

    @Test
    void aBuyIsReadWithWhatItCost() {
        FlexReportTrade buy = flexReportReader.readReport(bytesOf(REPORT)).trades().get(1);

        assertThat(buy.buySell()).isEqualTo("BUY");
        assertThat(buy.quantity()).isEqualTo(new BigDecimal("100"));
        assertThat(buy.netCash()).isEqualTo(new BigDecimal("-4230"));
        assertThat(buy.settleDate()).isEqualTo(LocalDate.of(2026, 6, 2));
    }

    /** A conversion to Hong Kong dollars is a trade in HKD whose commission IB charged in dollars. */
    @Test
    void aCurrencyConversionIsReadLikeATradeWithItsCommissionsCurrency() {
        FlexReportTrade conversion = flexReportReader.readReport(bytesOf(REPORT)).trades().get(2);

        assertThat(conversion.assetCategory()).isEqualTo("CASH");
        assertThat(conversion.currency()).isEqualTo("HKD");
        assertThat(conversion.ibCommission()).isEqualTo(new BigDecimal("-2.5"));
        assertThat(conversion.ibCommissionCurrency()).isEqualTo("USD");
    }

    @Test
    void aDividendAndTheTaxWithheldOnItAreReadWithTheirStock() {
        FlexReport report = flexReportReader.readReport(bytesOf(REPORT));

        assertThat(report.cashTransactions().get(0)).isEqualTo(new FlexReportCashTransaction("3000000001", "Dividends",
                2222, "WXYZ", "USD", new BigDecimal("12"), LocalDateTime.of(2026, 9, 15, 20, 20, 0),
                LocalDate.of(2026, 9, 15), "WXYZ(US0000000000) CASH DIVIDEND USD 0.12 PER SHARE (Ordinary Dividend)"));
        assertThat(report.cashTransactions().get(1).transactionType()).isEqualTo("Withholding Tax");
        assertThat(report.cashTransactions().get(1).amount()).isEqualTo(new BigDecimal("-3"));
    }

    /** IB writes a fee's stock as empty ({@code conid=""}); IB cancelling a fee is a row of its own, positive. */
    @Test
    void aFeeHasNoStockAndItsCancellationIsPositive() {
        FlexReport report = flexReportReader.readReport(bytesOf(REPORT));

        FlexReportCashTransaction fee = report.cashTransactions().get(2);
        assertThat(fee.transactionType()).isEqualTo("Other Fees");
        assertThat(fee.conId()).isNull();
        assertThat(fee.symbol()).isNull();
        assertThat(fee.amount()).isEqualTo(new BigDecimal("-7.5"));
        assertThat(report.cashTransactions().get(3).amount()).isEqualTo(new BigDecimal("7.5"));
    }

    @Test
    void thePeriodIsTheOneIbWroteInTheReport() {
        FlexReport report = flexReportReader.readReport(bytesOf(REPORT));

        assertThat(report.period())
                .isEqualTo(new FlexReportPeriod(LocalDate.of(2025, 11, 14), LocalDate.of(2026, 11, 13)));
    }

    @Test
    void aReportOfDaysWithoutTradesHasEmptySections() {
        FlexReport report = flexReportReader.readReport(bytesOf("""
                <FlexQueryResponse queryName="Cash and Trades" type="AF"><FlexStatements count="1">
                <FlexStatement fromDate="20261114" toDate="20261114"><Trades /><CashTransactions /></FlexStatement>
                </FlexStatements></FlexQueryResponse>
                """));

        assertThat(report.trades()).isEmpty();
        assertThat(report.cashTransactions()).isEmpty();
        assertThat(report.corporateActions()).isEmpty();
    }

    @Test
    void aReportIsToldFromTheStatusOfARequest() {
        assertThat(flexReportReader.isReport(bytesOf(REPORT))).isTrue();
        assertThat(flexReportReader.isReport(bytesOf(statusOf("Warn", 1019,
                "Statement generation in progress. Please try again shortly.")))).isFalse();
    }

    @Test
    void anAcceptedRequestHasTheCodeToFetchTheReportBy() {
        FlexReportRequestStatus requestStatus = flexReportReader.readRequestStatus(bytesOf("""
                <FlexStatementResponse timestamp="15 November, 2026 08:30 AM EST">
                <Status>Success</Status>
                <ReferenceCode>1234567890</ReferenceCode>
                <Url>https://ndcdyn.interactivebrokers.com/AccountManagement/FlexWebService/GetStatement</Url>
                </FlexStatementResponse>
                """));

        assertThat(requestStatus.wasAccepted()).isTrue();
        assertThat(requestStatus.referenceCode()).isEqualTo("1234567890");
    }

    @Test
    void aReportStillBeingPreparedSaysTryAgainShortly() {
        FlexReportRequestStatus requestStatus = flexReportReader.readRequestStatus(bytesOf(statusOf("Warn", 1019,
                "Statement generation in progress. Please try again shortly.")));

        assertThat(requestStatus.wasAccepted()).isFalse();
        assertThat(requestStatus.saysTryAgainShortly()).isTrue();
    }

    @Test
    void anExpiredTokenIsARefusalWithIbsWords() {
        FlexReportRequestStatus requestStatus =
                flexReportReader.readRequestStatus(bytesOf(statusOf("Fail", 1012, "Token has expired.")));

        assertThat(requestStatus.saysTryAgainShortly()).isFalse();
        assertThat(requestStatus.errorCodeAndMessage()).isEqualTo("1012 Token has expired.");
    }

    /** Most likely a field not ticked in the query: the whole report fails, and says which field of which row. */
    @Test
    void aTradeWithoutAFieldTheTaxNeedsFailsWithTheFieldsName() {
        String reportWithoutTheTradesTime = REPORT.replace(" dateTime=\"20261012;143015\"", "");

        assertThatThrownBy(() -> flexReportReader.readReport(bytesOf(reportWithoutTheTradesTime)))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessage("<Trade tradeID=\"1000000001\"> has no dateTime: is it ticked in the Flex query?");
    }

    @Test
    void aFieldThatCantBeReadFailsWithItsValue() {
        String reportWithAWrongQuantity = REPORT.replace("quantity=\"-20\"", "quantity=\"-20 shares\"");

        assertThatThrownBy(() -> flexReportReader.readReport(bytesOf(reportWithAWrongQuantity)))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessage("<Trade tradeID=\"1000000001\"> has a quantity that can't be read: -20 shares");
    }

    /**
     * A {@code DOCTYPE} is how an XML file asks its reader to load another file — here one of the machine's own —; the
     * reader refuses any.
     */
    @Test
    void anAnswerWithADoctypeIsRefused() {
        String answerAskingForAFile = """
                <?xml version="1.0"?>
                <!DOCTYPE FlexQueryResponse [<!ENTITY secret SYSTEM "file:///etc/passwd">]>
                <FlexQueryResponse>&secret;</FlexQueryResponse>
                """;

        assertThatThrownBy(() -> flexReportReader.readReport(bytesOf(answerAskingForAFile)))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessageContaining("DOCTYPE");
    }

    @Test
    void anAnswerThatIsNotXmlFails() {
        assertThatThrownBy(() -> flexReportReader.readReport(bytesOf("Service Temporarily Unavailable")))
                .isInstanceOf(FlexReportUnavailableException.class)
                .hasMessageStartingWith("IB's answer is not XML that can be read");
    }

    private String statusOf(String status, int errorCode, String errorMessage) {
        return """
                <FlexStatementResponse timestamp="15 November, 2026 08:30 AM EST">
                <Status>%s</Status>
                <ErrorCode>%d</ErrorCode>
                <ErrorMessage>%s</ErrorMessage>
                </FlexStatementResponse>
                """.formatted(status, errorCode, errorMessage);
    }

    private byte[] bytesOf(String xml) {
        return xml.getBytes(StandardCharsets.UTF_8);
    }
}
