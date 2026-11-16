package portfolioboss.tax;

import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import portfolioboss.exceptions.FlexReportUnavailableException;
import portfolioboss.utils.Utils;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the XML IB's Flex Web Service answers with: a report — the Trades, Cash Transactions and Corporate Actions sections
 * of the query (CAPITAL_GAINS_TAX_TODO.md, sessions 1.1 and 3.0) —, or, when it sends none, its status of the request for
 * one. Java's own XML
 * reader, so no library is added: Jackson's XML module would also have Spring read and write XML on every endpoint,
 * which weakens CLAUDE.md's JSON-only rule.
 *
 * <p>Every field the tax needs is required: a row without one fails the whole report with the field's name — a field not
 * ticked in the query, most likely — rather than storing a row the calculation can't use. The fields not read here (the
 * account, a trade's description, its proceeds, …) are skipped.
 */
class FlexReportReader {

    /** How IB writes a date in a Flex report, and a date with its time: the query's default formats. */
    private static final DateTimeFormatter IB_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    private static final DateTimeFormatter IB_DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd;HHmmss");
    /** The outer element of a report, and of the service's status of a request. */
    private static final String REPORT_ROOT_ELEMENT = "FlexQueryResponse";
    private static final String REQUEST_STATUS_ROOT_ELEMENT = "FlexStatementResponse";
    /**
     * The level of a corporate action's rows that is read: should IB add summary rows of the same actions, counting them
     * too would count every action twice.
     */
    private static final String DETAIL_ROWS = "DETAIL";

    /** {@code true} for a report, {@code false} for the service's status of the request — still being prepared, say. */
    protected boolean isReport(byte[] answerXml) {
        return REPORT_ROOT_ELEMENT.equals(rootElementOf(answerXml).getTagName());
    }

    protected FlexReport readReport(byte[] reportXml) {
        Element rootElement = rootElementOf(reportXml);
        requireRootElement(rootElement, REPORT_ROOT_ELEMENT);
        List<Element> statements = rowsNamed(rootElement, "FlexStatement");
        if (statements.isEmpty()) {
            throw new FlexReportUnavailableException("IB's report has no <FlexStatement>");
        }
        FlexReportPeriod period = periodOf(new ReportRow(statements.get(0), null));
        List<FlexReportTrade> trades = rowsNamed(rootElement, "Trade").stream()
                .map(tradeElement -> tradeOf(new ReportRow(tradeElement, "tradeID")))
                .toList();
        List<FlexReportCashTransaction> cashTransactions = rowsNamed(rootElement, "CashTransaction").stream()
                .map(cashTransactionElement -> cashTransactionOf(new ReportRow(cashTransactionElement, "transactionID")))
                .toList();
        List<FlexReportCorporateAction> corporateActions = rowsNamed(rootElement, "CorporateAction").stream()
                .map(corporateActionElement -> new ReportRow(corporateActionElement, "transactionID"))
                .filter(corporateActionRow -> DETAIL_ROWS.equals(corporateActionRow.requiredText("levelOfDetail")))
                .map(this::corporateActionOf)
                .toList();
        return new FlexReport(period, trades, cashTransactions, corporateActions);
    }

    protected FlexReportRequestStatus readRequestStatus(byte[] statusXml) {
        Element rootElement = rootElementOf(statusXml);
        requireRootElement(rootElement, REQUEST_STATUS_ROOT_ELEMENT);
        return new FlexReportRequestStatus(
                childTextOf(rootElement, "Status"),
                childTextOf(rootElement, "ReferenceCode"),
                errorCodeOf(rootElement),
                childTextOf(rootElement, "ErrorMessage"));
    }

    /** The days the report covers, as IB wrote them on its {@code <FlexStatement>}. */
    private FlexReportPeriod periodOf(ReportRow statementRow) {
        return new FlexReportPeriod(statementRow.requiredDate("fromDate"), statementRow.requiredDate("toDate"));
    }

    /** IB's attribute names on the left of each line: the query's field names, as XML writes them. */
    private FlexReportTrade tradeOf(ReportRow tradeRow) {
        return new FlexReportTrade(
                tradeRow.requiredText("tradeID"),
                tradeRow.optionalText("ibOrderID"),
                tradeRow.requiredInteger("conid"),
                tradeRow.requiredText("symbol"),
                tradeRow.requiredText("assetCategory"),
                tradeRow.requiredText("currency"),
                tradeRow.requiredDate("tradeDate"),
                tradeRow.requiredDateTime("dateTime"),
                tradeRow.requiredDate("settleDateTarget"),
                tradeRow.requiredText("buySell"),
                tradeRow.requiredDecimal("quantity"),
                tradeRow.requiredDecimal("tradePrice"),
                tradeRow.requiredDecimal("ibCommission"),
                tradeRow.requiredText("ibCommissionCurrency"),
                tradeRow.requiredDecimal("netCash"),
                tradeRow.optionalText("transactionType"),
                tradeRow.optionalDecimal("fifoPnlRealized"));
    }

    private FlexReportCashTransaction cashTransactionOf(ReportRow cashTransactionRow) {
        return new FlexReportCashTransaction(
                cashTransactionRow.requiredText("transactionID"),
                cashTransactionRow.requiredText("type"),
                cashTransactionRow.optionalInteger("conid"),
                cashTransactionRow.optionalText("symbol"),
                cashTransactionRow.requiredText("currency"),
                cashTransactionRow.requiredDecimal("amount"),
                cashTransactionRow.requiredDateTime("dateTime"),
                cashTransactionRow.requiredDate("settleDate"),
                cashTransactionRow.optionalText("description"));
    }

    private FlexReportCorporateAction corporateActionOf(ReportRow corporateActionRow) {
        return new FlexReportCorporateAction(
                corporateActionRow.requiredText("transactionID"),
                corporateActionRow.requiredText("actionID"),
                corporateActionRow.requiredText("type"),
                corporateActionRow.requiredInteger("conid"),
                corporateActionRow.requiredText("symbol"),
                corporateActionRow.requiredText("assetCategory"),
                corporateActionRow.requiredDateTime("dateTime"),
                corporateActionRow.requiredDecimal("quantity"),
                corporateActionRow.requiredDecimal("proceeds"),
                corporateActionRow.requiredDecimal("amount"),
                corporateActionRow.optionalText("description"));
    }

    private Integer errorCodeOf(Element statusRootElement) {
        String errorCodeText = childTextOf(statusRootElement, "ErrorCode");
        if (errorCodeText == null) {
            return null;
        }
        try {
            return Integer.valueOf(errorCodeText);
        } catch (NumberFormatException e) {
            throw new FlexReportUnavailableException("IB's error code is not a number: " + errorCodeText);
        }
    }

    /** Every element of that name in the answer, in IB's order: all the {@code <Trade>}s, say. */
    private List<Element> rowsNamed(Element rootElement, String elementName) {
        NodeList elements = rootElement.getElementsByTagName(elementName);
        List<Element> rows = new ArrayList<>();
        for (int index = 0; index < elements.getLength(); index++) {
            rows.add((Element) elements.item(index));
        }
        return rows;
    }

    /** The text inside the first child element of that name — {@code <Status>Success</Status>} —; {@code null} without one. */
    private String childTextOf(Element element, String childName) {
        NodeList children = element.getElementsByTagName(childName);
        if (children.getLength() == 0) {
            return null;
        }
        return Utils.trimmedOrNull(children.item(0).getTextContent());
    }

    private void requireRootElement(Element rootElement, String expectedName) {
        if (!expectedName.equals(rootElement.getTagName())) {
            throw new FlexReportUnavailableException("IB answered with <" + rootElement.getTagName()
                    + "> where <" + expectedName + "> was expected");
        }
    }

    private Element rootElementOf(byte[] answerXml) {
        if (answerXml == null || answerXml.length == 0) {
            throw new FlexReportUnavailableException("IB's answer is empty");
        }
        try {
            return newSafeDocumentBuilder().parse(new ByteArrayInputStream(answerXml)).getDocumentElement();
        } catch (SAXException | IOException e) {
            throw new FlexReportUnavailableException("IB's answer is not XML that can be read (" + e.getMessage() + ")");
        }
    }

    /**
     * Java's XML reader with what makes reading a file from outside unsafe turned off: the file may not declare a
     * {@code DOCTYPE}, which is how an XML file asks its reader to load other files or addresses, or to expand an entity
     * without end. IB's answers have none. Its error handler is quiet: an answer that can't be read becomes the
     * exception, without a line the reader would otherwise print on its own.
     */
    private DocumentBuilder newSafeDocumentBuilder() {
        try {
            DocumentBuilderFactory documentBuilderFactory = DocumentBuilderFactory.newInstance();
            documentBuilderFactory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            documentBuilderFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            documentBuilderFactory.setXIncludeAware(false);
            documentBuilderFactory.setExpandEntityReferences(false);
            DocumentBuilder documentBuilder = documentBuilderFactory.newDocumentBuilder();
            documentBuilder.setErrorHandler(new DefaultHandler());
            return documentBuilder;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("Java's XML reader could not be set up safely", e);
        }
    }

    /**
     * One element of the report — a trade, a cash transaction, the statement — and its attributes, as IB wrote them. A
     * field IB left empty ({@code conid=""} on a fee) is read as missing.
     *
     * @param element         the element
     * @param idAttributeName the attribute that tells this row apart, for the error messages — {@code tradeID} —;
     *                        {@code null} for the statement, of which there is one
     */
    private record ReportRow(Element element, String idAttributeName) {

        private String optionalText(String attributeName) {
            return Utils.trimmedOrNull(element.getAttribute(attributeName));
        }

        private String requiredText(String attributeName) {
            String text = optionalText(attributeName);
            if (text == null) {
                throw new FlexReportUnavailableException(rowDescription() + " has no " + attributeName
                        + ": is it ticked in the Flex query?");
            }
            return text;
        }

        private BigDecimal requiredDecimal(String attributeName) {
            return decimalOf(attributeName, requiredText(attributeName));
        }

        private BigDecimal optionalDecimal(String attributeName) {
            String text = optionalText(attributeName);
            return text == null ? null : decimalOf(attributeName, text);
        }

        private BigDecimal decimalOf(String attributeName, String text) {
            try {
                return new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw unreadableValue(attributeName, text);
            }
        }

        private int requiredInteger(String attributeName) {
            return integerOf(attributeName, requiredText(attributeName));
        }

        private Integer optionalInteger(String attributeName) {
            String text = optionalText(attributeName);
            return text == null ? null : integerOf(attributeName, text);
        }

        private LocalDate requiredDate(String attributeName) {
            String text = requiredText(attributeName);
            try {
                return LocalDate.parse(text, IB_DATE_FORMAT);
            } catch (DateTimeParseException e) {
                throw unreadableValue(attributeName, text);
            }
        }

        private LocalDateTime requiredDateTime(String attributeName) {
            String text = requiredText(attributeName);
            try {
                return LocalDateTime.parse(text, IB_DATE_TIME_FORMAT);
            } catch (DateTimeParseException e) {
                throw unreadableValue(attributeName, text);
            }
        }

        private int integerOf(String attributeName, String text) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException e) {
                throw unreadableValue(attributeName, text);
            }
        }

        private FlexReportUnavailableException unreadableValue(String attributeName, String text) {
            return new FlexReportUnavailableException(rowDescription() + " has a " + attributeName
                    + " that can't be read: " + text);
        }

        /** The row as the error names it: {@code <Trade tradeID="1000000001">}, or {@code <FlexStatement>}. */
        private String rowDescription() {
            if (idAttributeName == null) {
                return "<" + element.getTagName() + ">";
            }
            return "<" + element.getTagName() + " " + idAttributeName + "=\"" + element.getAttribute(idAttributeName)
                    + "\">";
        }
    }
}
