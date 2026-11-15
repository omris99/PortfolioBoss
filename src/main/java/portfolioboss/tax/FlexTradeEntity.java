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
 * One row of the {@code flex_trade} table ({@code V16__flex_reports.sql}): an execution as IB's Flex report gave it —
 * a {@link FlexReportTrade}. Written only by {@link FlexReportStorageService}, which adds it the first time IB reports
 * it and refreshes it whenever IB reports it again.
 */
@Entity
@Table(name = "flex_trade")
public class FlexTradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String ibTradeId;
    private String ibOrderId;
    private int conId;
    private String symbol;
    private String assetCategory;
    private String currency;
    private LocalDate tradeDate;
    private LocalDateTime tradeTime;
    private LocalDate settleDate;
    private String buySell;
    private BigDecimal quantity;
    private BigDecimal tradePrice;
    private BigDecimal ibCommission;
    private String ibCommissionCurrency;
    private BigDecimal netCash;
    private String transactionType;

    /** Required by JPA, which creates entities by reflection. */
    protected FlexTradeEntity() {
    }

    protected FlexTradeEntity(FlexReportTrade trade) {
        this.ibTradeId = trade.ibTradeId();
        refreshFrom(trade);
    }

    /** IB reported the trade again, in a later report of the same days: every field as IB now writes it. */
    protected void refreshFrom(FlexReportTrade trade) {
        this.ibOrderId = trade.ibOrderId();
        this.conId = trade.conId();
        this.symbol = trade.symbol();
        this.assetCategory = trade.assetCategory();
        this.currency = trade.currency();
        this.tradeDate = trade.tradeDate();
        this.tradeTime = trade.tradeTime();
        this.settleDate = trade.settleDate();
        this.buySell = trade.buySell();
        this.quantity = trade.quantity();
        this.tradePrice = trade.tradePrice();
        this.ibCommission = trade.ibCommission();
        this.ibCommissionCurrency = trade.ibCommissionCurrency();
        this.netCash = trade.netCash();
        this.transactionType = trade.transactionType();
    }

    protected String ibTradeId() {
        return ibTradeId;
    }
}
