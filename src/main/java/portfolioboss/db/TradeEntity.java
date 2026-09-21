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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One row of the {@code trade} table: a buy or sell the user entered by hand. The buy date, sell date
 * and holding period of a holding are derived from these, so they are never stored twice.
 *
 * <p>{@code created_at} is filled by the database default and is not mapped.
 */
@Entity
@Table(name = "trade")
public class TradeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** LAZY: loading a trade does not also load its holding until something asks for it. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "holding_id")
    private HoldingEntity holding;

    private LocalDate tradeDate;

    @Enumerated(EnumType.STRING)
    private TradeSide side;

    /** BigDecimal, not double: typed in by the user, and the sum of trades must be exact. */
    private BigDecimal quantity;
    private BigDecimal price;
    private String note;

    /** Required by JPA, which creates entities by reflection. */
    protected TradeEntity() {
    }
}
