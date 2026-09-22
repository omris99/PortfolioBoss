package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import portfolioboss.model.Holding;
import portfolioboss.utils.Utils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * One row of the {@code holding} table: a position PortfolioBoss has seen at Interactive Brokers.
 *
 * <p>The broker figures are refreshed on every connection. {@code sector} and {@code trades} are entered
 * by the user and never overwritten by the sync. A holding that disappears from TWS is marked
 * {@code CLOSED}, never deleted, so those manual entries survive.
 *
 * <p>Not to be confused with {@code model.Holding} (one IB reading) or {@code HoldingResponse} (what the
 * UI receives). The column names are these field names in snake_case ({@code conId} is {@code con_id}).
 */
@Entity
@Table(name = "holding")
public class HoldingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String account;
    private int conId;
    private String symbol;
    private String secType;
    private String currency;

    private String sector;

    private double position;
    private Double averageCost;
    private Double marketPrice;
    private Double marketValue;
    private Double unrealizedPnl;
    private Double realizedPnl;

    @Enumerated(EnumType.STRING)
    private HoldingStatus status;

    private Instant firstSeenAt;
    private Instant lastSyncedAt;
    private Instant closedDetectedAt;

    @OneToMany(mappedBy = "holding")
    @OrderBy("tradeDate, id")
    private List<TradeEntity> trades = new ArrayList<>();

    /** Required by JPA, which creates entities by reflection. */
    protected HoldingEntity() {
    }

    /** A position seen for the first time: open, with no sector and no trades yet. */
    static HoldingEntity firstSeen(String account, Holding reading, Instant syncedAt) {
        HoldingEntity holding = new HoldingEntity();
        holding.account = account;
        holding.conId = reading.conId();
        holding.secType = reading.secType();      // fixed for a conId, so set only here
        holding.currency = reading.currency();
        holding.firstSeenAt = syncedAt;
        holding.refreshFromIb(reading, syncedAt);
        return holding;
    }

    /**
     * Takes the broker's latest figures and reopens the holding if it was closed. It never touches
     * {@code sector} or {@code trades}: those are entered by hand.
     */
    void refreshFromIb(Holding reading, Instant syncedAt) {
        symbol = reading.symbol();
        position = reading.position();
        averageCost = Utils.finiteOrNull(reading.averageCost());
        marketPrice = Utils.finiteOrNull(reading.marketPrice());
        marketValue = Utils.finiteOrNull(reading.marketValue());
        unrealizedPnl = Utils.finiteOrNull(reading.unrealizedPnl());
        realizedPnl = Utils.finiteOrNull(reading.realizedPnl());
        status = HoldingStatus.OPEN;
        lastSyncedAt = syncedAt;
        closedDetectedAt = null;
    }

    /**
     * The holding is no longer in the portfolio TWS reported. What is held now is zero; the last average cost,
     * price and realized P&amp;L stay as last seen, and {@code lastSyncedAt} stays the last time IB reported it.
     */
    void markClosed(Instant syncedAt) {
        status = HoldingStatus.CLOSED;
        position = 0;
        marketValue = 0.0;
        unrealizedPnl = 0.0;
        closedDetectedAt = syncedAt;
    }

    public Long id() {
        return id;
    }

    public int conId() {
        return conId;
    }

    public String sector() {
        return sector;
    }

    public HoldingStatus status() {
        return status;
    }

    /**
     * The stored figures as an IB reading again ({@code NULL} becomes {@code NaN}), so the derived math on
     * {@code Holding} ({@code costBasis}, {@code unrealizedPnlPercent}) is not written a second time.
     */
    public Holding toIbHolding() {
        return new Holding(symbol, conId, secType, currency, position,
                Utils.nanIfNull(averageCost), Utils.nanIfNull(marketPrice), Utils.nanIfNull(marketValue),
                Utils.nanIfNull(unrealizedPnl), Utils.nanIfNull(realizedPnl), account);
    }
}
