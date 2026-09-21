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
}
