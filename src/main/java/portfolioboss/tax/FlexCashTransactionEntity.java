package portfolioboss.tax;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * One row of the {@code flex_cash_transaction} table ({@code V16__flex_reports.sql}): a cash movement that is not a trade,
 * as IB's Flex report gave it — a {@link FlexReportCashTransaction}. Written only by
 * {@link FlexReportStorageService}, like {@link FlexTradeEntity}.
 */
@Entity
@Table(name = "flex_cash_transaction")
public class FlexCashTransactionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ibTransactionId;
    private String transactionType;
    /** {@code null} for a fee, which belongs to no stock. */
    private Integer conId;
    private String symbol;
    private String currency;
    private BigDecimal amount;
    private LocalDateTime transactionTime;
    private LocalDate settleDate;
    private String description;

    /** Required by JPA, which creates entities by reflection. */
    protected FlexCashTransactionEntity() {
    }

    protected FlexCashTransactionEntity(FlexReportCashTransaction cashTransaction) {
        this.ibTransactionId = cashTransaction.ibTransactionId();
        refreshFrom(cashTransaction);
    }

    /** IB reported the row again, in a later report of the same days: every field as IB now writes it. */
    protected void refreshFrom(FlexReportCashTransaction cashTransaction) {
        this.transactionType = cashTransaction.transactionType();
        this.conId = cashTransaction.conId();
        this.symbol = cashTransaction.symbol();
        this.currency = cashTransaction.currency();
        this.amount = cashTransaction.amount();
        this.transactionTime = cashTransaction.transactionTime();
        this.settleDate = cashTransaction.settleDate();
        this.description = cashTransaction.description();
    }

    protected String ibTransactionId() {
        return ibTransactionId;
    }

    /** The row as IB reported it, for the calculation, which works on IB's records rather than on entities. */
    protected FlexReportCashTransaction toFlexReportCashTransaction() {
        return new FlexReportCashTransaction(ibTransactionId, transactionType, conId, symbol, currency, amount,
                transactionTime, settleDate, description);
    }
}
