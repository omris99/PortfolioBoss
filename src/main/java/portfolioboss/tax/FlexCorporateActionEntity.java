package portfolioboss.tax;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * One row of the {@code flex_corporate_action} table ({@code V18__flex_corporate_actions_and_ib_fifo_pnl.sql}): a row of
 * a corporate action as IB's Flex report gave it — a {@link FlexReportCorporateAction}. Written only by
 * {@link FlexReportStorageService}, like {@link FlexTradeEntity}.
 */
@Entity
@Table(name = "flex_corporate_action")
public class FlexCorporateActionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ibTransactionId;
    private String ibActionId;
    private String actionType;
    private int conId;
    private String symbol;
    private String assetCategory;
    private LocalDateTime actionTime;
    private BigDecimal quantity;
    private BigDecimal proceeds;
    private BigDecimal amount;
    private String description;

    /** Required by JPA, which creates entities by reflection. */
    protected FlexCorporateActionEntity() {
    }

    protected FlexCorporateActionEntity(FlexReportCorporateAction corporateAction) {
        this.ibTransactionId = corporateAction.ibTransactionId();
        refreshFrom(corporateAction);
    }

    /** IB reported the row again, in a later report of the same days: every field as IB now writes it. */
    protected void refreshFrom(FlexReportCorporateAction corporateAction) {
        this.ibActionId = corporateAction.ibActionId();
        this.actionType = corporateAction.actionType();
        this.conId = corporateAction.conId();
        this.symbol = corporateAction.symbol();
        this.assetCategory = corporateAction.assetCategory();
        this.actionTime = corporateAction.actionTime();
        this.quantity = corporateAction.quantity();
        this.proceeds = corporateAction.proceeds();
        this.amount = corporateAction.amount();
        this.description = corporateAction.description();
    }

    protected String ibTransactionId() {
        return ibTransactionId;
    }

    /** The row as IB reported it, for the calculation, which works on IB's records rather than on entities. */
    protected FlexReportCorporateAction toFlexReportCorporateAction() {
        return new FlexReportCorporateAction(ibTransactionId, ibActionId, actionType, conId, symbol, assetCategory,
                actionTime, quantity, proceeds, amount, description);
    }
}
