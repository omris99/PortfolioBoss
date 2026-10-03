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
import portfolioboss.domain.TradeFact;

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
    public TradeEntity(HoldingEntity holding, LocalDate tradeDate, TradeSide side, BigDecimal quantity,
                       BigDecimal price, String note, BigDecimal commission) {
        this.holding = holding;
        changeDetails(tradeDate, side, quantity, price, note, commission);
    }

    /** A trade the user entered for {@code manualPosition}. {@code price} and {@code note} may be {@code null}. */
    public TradeEntity(ManualPositionEntity manualPosition, LocalDate tradeDate, TradeSide side, BigDecimal quantity,
                       BigDecimal price, String note, BigDecimal commission) {
        this.manualPosition = manualPosition;
        changeDetails(tradeDate, side, quantity, price, note, commission);
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

    /** {@code null} for a holding's trade. */
    public ManualPositionEntity manualPosition() {
        return manualPosition;
    }

    /** Reduced to what {@link portfolioboss.domain.HoldingHistory} needs: the id, date, side, amounts and commission. */
    public TradeFact toTradeFact() {
        return new TradeFact(id, tradeDate, side, quantity, price, commission);
    }
}
