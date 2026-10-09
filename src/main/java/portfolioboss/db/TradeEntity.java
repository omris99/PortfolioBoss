package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import portfolioboss.calculation.TradeFact;
import portfolioboss.model.TradeSide;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the {@code trade} table: a buy or sell the user entered by hand, for a holding or for a manual position —
 * never both, which the table checks. The buy date, sell date, holding period and closed positions are derived from
 * these, so they are never stored twice.
 *
 * <p>{@code created_at} is filled by the database default and is not mapped.
 */
@Entity
@Table(name = "trade")
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY: loading a trade does not also load its holding until something asks for it. {@code null} if manual. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "holding_id")
    private HoldingEntity holding;

    /** The manual position it belongs to, or {@code null} for a holding's trade. LAZY too. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manual_position_id")
    private ManualPositionEntity manualPosition;

    /**
     * Whose trade it is. A plain id rather than a link to {@link InvestorEntity}: nothing reads the investor through the
     * trade (the UI gets their names from the list of investors), and a link would cost a query to load it.
     */
    private Long investorId;

    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    private TradeSide side;

    /** BigDecimal, not double: typed in by the user, and the sum of trades must be exact. */
    private BigDecimal quantity;
    private BigDecimal price;
    private String note;

    /** Never {@code null}: the API stores the default when the user entered none. */
    private BigDecimal commission;

    /** Required by JPA, which creates entities by reflection. */
    protected TradeEntity() {
    }

    /** A trade the user entered for {@code holding}. {@code price} and {@code note} may be {@code null}. */
    public TradeEntity(HoldingEntity holding, InvestorEntity investor, LocalDate tradeDate, TradeSide side,
                       BigDecimal quantity, BigDecimal price, String note, BigDecimal commission) {
        this.holding = holding;
        this.investorId = investor.id();
        changeDetails(tradeDate, side, quantity, price, note, commission);
    }

    /** A trade the user entered for {@code manualPosition}. {@code price} and {@code note} may be {@code null}. */
    public TradeEntity(ManualPositionEntity manualPosition, InvestorEntity investor, LocalDate tradeDate,
                       TradeSide side, BigDecimal quantity, BigDecimal price, String note, BigDecimal commission) {
        this.manualPosition = manualPosition;
        this.investorId = investor.id();
        changeDetails(tradeDate, side, quantity, price, note, commission);
    }

    /**
     * Another investor's trade from now on. Apart from {@link #changeDetails}: a correction that names no investor keeps
     * the one the trade has.
     */
    public void changeInvestor(InvestorEntity investor) {
        this.investorId = investor.id();
    }

    /** A correction by the user: every field is replaced, and the trade stays on the same holding. */
    public void changeDetails(LocalDate tradeDate, TradeSide side, BigDecimal quantity, BigDecimal price, String note,
                              BigDecimal commission) {
        this.tradeDate = tradeDate;
        this.side = side;
        this.quantity = quantity;
        this.price = price;
        this.note = note;
        this.commission = commission;
    }

    public Long id() {
        return id;
    }

    public LocalDate tradeDate() {
        return tradeDate;
    }

    public TradeSide side() {
        return side;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public BigDecimal price() {
        return price;
    }

    public String note() {
        return note;
    }

    public BigDecimal commission() {
        return commission;
    }

    public long investorId() {
        return investorId;
    }

    /** {@code null} for a holding's trade. */
    public ManualPositionEntity manualPosition() {
        return manualPosition;
    }

    /**
     * Reduced to what {@link portfolioboss.calculation.HoldingHistory} needs: the id, the investor, date, side, amounts and
     * commission.
     */
    public TradeFact toTradeFact() {
        return new TradeFact(id, investorId, tradeDate, side, quantity, price, commission);
    }
}
