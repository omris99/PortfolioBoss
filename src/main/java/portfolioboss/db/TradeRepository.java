package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Reads and writes {@link TradeEntity} rows. Spring generates the class at startup, like {@link HoldingRepository};
 * the inherited {@code findById}, {@code save} and {@code delete} are all the write endpoints need.
 */
public interface TradeRepository extends JpaRepository<TradeEntity, Long> {

    /**
     * The date of the earliest trade typed in by hand, of a holding or of a manual position: where the first download of
     * IB's Flex reports starts ({@code tax.FlexDownloadSchedule}). Empty before the first trade.
     */
    @Query("select min(trade.tradeDate) from TradeEntity trade")
    Optional<LocalDate> findEarliestTradeDate();

    /**
     * The days of the trades typed in by hand in this currency — a holding's or a manual position's —, each once: from
     * them {@code tax.SettlementDayRatesService} estimates the settlement days whose exchange rate each investor's tax
     * needs. A trade belongs to a holding or to a manual position, so one of the two joins finds nothing.
     */
    @Query("select distinct trade.tradeDate from TradeEntity trade "
            + "left join trade.holding holding left join trade.manualPosition manualPosition "
            + "where holding.currency = :currency or manualPosition.currency = :currency")
    List<LocalDate> findTradeDatesInCurrency(@Param("currency") String currency);
}
