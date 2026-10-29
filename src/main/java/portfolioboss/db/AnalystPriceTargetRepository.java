package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import portfolioboss.ib.AnalystPriceTarget;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes {@link AnalystPriceTargetEntity} rows, like {@link DailyCloseRepository} the daily closes. */
public interface AnalystPriceTargetRepository extends JpaRepository<AnalystPriceTargetEntity, Long> {

    List<AnalystPriceTargetEntity> findByConIdIn(Collection<Integer> conIds);

    /** The same targets by contract id, in one query; a stock without any is missing. */
    default Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsByConId(Collection<Integer> conIds) {
        if (conIds.isEmpty()) {
            return Map.of();
        }
        return findByConIdIn(conIds).stream()
                .collect(Collectors.groupingBy(AnalystPriceTargetEntity::conId,
                        Collectors.mapping(AnalystPriceTargetEntity::toAnalystPriceTarget, Collectors.toList())));
    }

    /** One {@code DELETE} statement for all of these stocks' rows, like {@code DailyCloseRepository}'s. */
    @Modifying
    @Query("delete from AnalystPriceTargetEntity analystPriceTarget where analystPriceTarget.conId in :conIds")
    void deleteForContracts(@Param("conIds") Collection<Integer> conIds);
}
