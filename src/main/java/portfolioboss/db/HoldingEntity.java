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
    protected HoldingEntity(String account, Holding holdingFromIb, Instant syncedAt) {
        this.account = account;
        this.conId = holdingFromIb.conId();
        this.secType = holdingFromIb.secType();      // fixed for a conId, so set only here
        this.currency = holdingFromIb.currency();
        this.firstSeenAt = syncedAt;
        refreshFromIb(holdingFromIb, syncedAt);
    }

    /**
     * Takes the broker's latest figures and reopens the holding if it was closed. It never touches
     * {@code sector} or {@code trades}: those are entered by hand.
     */
    protected void refreshFromIb(Holding holdingFromIb, Instant syncedAt) {
        symbol = holdingFromIb.symbol();
        position = holdingFromIb.position();
        averageCost = Utils.finiteOrNull(holdingFromIb.averageCost());
        marketPrice = Utils.finiteOrNull(holdingFromIb.marketPrice());
        marketValue = Utils.finiteOrNull(holdingFromIb.marketValue());
        unrealizedPnl = Utils.finiteOrNull(holdingFromIb.unrealizedPnl());
        realizedPnl = Utils.finiteOrNull(holdingFromIb.realizedPnl());
        status = HoldingStatus.OPEN;
        lastSyncedAt = syncedAt;
        closedDetectedAt = null;
    }

    /**
     * The holding is no longer in the portfolio TWS reported. What is held now is zero; the last average cost,
     * price and realized P&amp;L stay as last seen, and {@code lastSyncedAt} stays the last time IB reported it.
     */
    protected void markClosed(Instant syncedAt) {
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

    /** Ordered by trade date then id ({@code @OrderBy} on the field below). */
    public List<TradeEntity> trades() {
        return List.copyOf(trades);
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
