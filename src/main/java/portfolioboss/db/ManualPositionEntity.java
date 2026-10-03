package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import portfolioboss.domain.HoldingHistory;

import java.util.ArrayList;
import java.util.List;

/**
 * One row of the {@code manual_position} table: a position PortfolioBoss never saw as a holding — sold before the
 * first sync — entered by hand with its own buys and sells ({@link TradeEntity} rows). It is not a
 * {@link HoldingEntity}: only the sync creates holdings. Its closed positions are derived from its trades exactly like a
 * holding's ({@link #tradeHistory()}).
 *
 * <p>{@code created_at} is filled by the database default and is not mapped. Deleting the row deletes its trades
 * ({@code ON DELETE CASCADE}).
 */
@Entity
@Table(name = "manual_position")
public class ManualPositionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String currency;
    private String sector;
    private String note;

    @OneToMany(mappedBy = "manualPosition")
    @OrderBy("tradeDate, id")
    private List<TradeEntity> trades = new ArrayList<>();

    /** Required by JPA, which creates entities by reflection. */
    protected ManualPositionEntity() {
    }

    /** {@code sector} and {@code note} may be {@code null}. Its trades are added as rows of their own. */
    public ManualPositionEntity(String symbol, String currency, String sector, String note) {
        changeDetails(symbol, currency, sector, note);
    }

    /** A correction by the user: every field is replaced; the trades stay as they are. */
    public void changeDetails(String symbol, String currency, String sector, String note) {
        this.symbol = symbol;
        this.currency = currency;
        this.sector = sector;
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

    /** Ordered by trade date then id ({@code @OrderBy} on the field above). */
    public List<TradeEntity> trades() {
        return List.copyOf(trades);
    }

    /** What its trades add up to, derived the same way as {@link HoldingEntity#tradeHistory()}. */
    public HoldingHistory tradeHistory() {
        return HoldingHistory.of(trades.stream().map(TradeEntity::toTradeFact).toList());
    }
}
