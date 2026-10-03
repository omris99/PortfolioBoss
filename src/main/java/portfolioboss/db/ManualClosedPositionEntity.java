package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import portfolioboss.domain.ClosedPosition;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the {@code manual_closed_position} table: a position bought and sold back to zero that PortfolioBoss never
 * saw as a holding — sold before the first sync — entered by hand as one round trip, one buy and one sell. It belongs
 * to no {@link HoldingEntity}: only the sync creates holdings.
 *
 * <p>{@code created_at} is filled by the database default and is not mapped.
 */
@Entity
@Table(name = "manual_closed_position")
public class ManualClosedPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String currency;
    private String sector;

    /** BigDecimal, not double: typed in by the user, like the amounts of {@link TradeEntity}. */
    private BigDecimal quantity;
    private LocalDate buyDate;
    private BigDecimal buyPrice;
    private LocalDate sellDate;
    private BigDecimal sellPrice;

    /** Of the buy and the sell together. Never {@code null}: the API stores the default when none was entered. */
    private BigDecimal commission;
    private String note;

    /** Required by JPA, which creates entities by reflection. */
    protected ManualClosedPositionEntity() {
    }

    /** {@code sector} and {@code note} may be {@code null}. */
    public ManualClosedPositionEntity(String symbol, String currency, String sector, BigDecimal quantity,
                                      LocalDate buyDate, BigDecimal buyPrice, LocalDate sellDate, BigDecimal sellPrice,
                                      BigDecimal commission, String note) {
        changeDetails(symbol, currency, sector, quantity, buyDate, buyPrice, sellDate, sellPrice, commission, note);
    }

    /** A correction by the user: every field is replaced. */
    public void changeDetails(String symbol, String currency, String sector, BigDecimal quantity,
                              LocalDate buyDate, BigDecimal buyPrice, LocalDate sellDate, BigDecimal sellPrice,
                              BigDecimal commission, String note) {
        this.symbol = symbol;
        this.currency = currency;
        this.sector = sector;
        this.quantity = quantity;
        this.buyDate = buyDate;
        this.buyPrice = buyPrice;
        this.sellDate = sellDate;
        this.sellPrice = sellPrice;
        this.commission = commission;
        this.note = note;
    }

    public Long id() {
        return id;
    }

    public String symbol() {
        return symbol;
    }

    public String currency() {
        return currency;
    }

    public String sector() {
        return sector;
    }

    public String note() {
        return note;
    }

    /**
     * The round trip as the totals {@link ClosedPosition} derives its figures from, so a row entered by hand and a
     * position period derived from trades are computed the same way. Every share bought was sold.
     */
    public ClosedPosition toClosedPosition() {
        return new ClosedPosition(buyDate, sellDate, quantity, quantity,
                quantity.multiply(buyPrice), quantity.multiply(sellPrice), commission);
    }
}
