package portfolioboss.db;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import portfolioboss.ib.AnalystPriceTarget;

import java.time.LocalDateTime;

/**
 * One row of the {@code analyst_price_target} table: one firm's price target for a stock, from one Dow Jones headline
 * (RECOMMENDATIONS_TODO.md, decision 14). Written only by the sync, with the scan that read it.
 */
@Entity
@Table(name = "analyst_price_target")
public class AnalystPriceTargetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private int conId;
    private LocalDateTime publishedAt;
    private String firm;
    private double priceTarget;
    private Double previousPriceTarget;
    private String headline;

    /** Required by JPA, which creates entities by reflection. */
    protected AnalystPriceTargetEntity() {
    }

    protected AnalystPriceTargetEntity(int conId, AnalystPriceTarget analystPriceTarget) {
        this.conId = conId;
        this.publishedAt = analystPriceTarget.publishedAt();
        this.firm = analystPriceTarget.firm();
        this.priceTarget = analystPriceTarget.priceTarget();
        this.previousPriceTarget = analystPriceTarget.previousPriceTarget();
        this.headline = analystPriceTarget.headline();
    }

    public int conId() {
        return conId;
    }

    public AnalystPriceTarget toAnalystPriceTarget() {
        return new AnalystPriceTarget(publishedAt, firm, priceTarget, previousPriceTarget, headline);
    }
}
