package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import portfolioboss.ib.DailyClose;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Reads and writes {@link DailyCloseEntity} rows; Spring generates the class at startup, like {@link HoldingRepository}. */
public interface DailyCloseRepository extends JpaRepository<DailyCloseEntity, Long> {

    /** The closes of these contracts, each contract's in date order. */
    List<DailyCloseEntity> findByConIdInOrderByConIdAscBarDateAsc(Collection<Integer> conIds);

    /**
     * The same closes by contract id, each contract's in date order — one query for all of them; a contract without any
     * is missing. A {@code default} method is plain Java: Spring makes no query of it, it only calls the one above.
     */
    default Map<Integer, List<DailyClose>> dailyClosesByConId(Collection<Integer> conIds) {
        return findByConIdInOrderByConIdAscBarDateAsc(conIds).stream()
                .collect(Collectors.groupingBy(DailyCloseEntity::conId,
                        Collectors.mapping(DailyCloseEntity::toDailyClose, Collectors.toList())));
    }

    /**
     * One {@code DELETE} statement for all of these contracts' rows. {@code @Query} writes the query by hand (in JPQL,
     * over the entity's names) and {@code @Modifying} says it changes rows: a derived {@code deleteBy…} method would
     * first load every row and then delete them one at a time.
     */
    @Modifying
    @Query("delete from DailyCloseEntity dailyClose where dailyClose.conId in :conIds")
    void deleteForContracts(@Param("conIds") Collection<Integer> conIds);
}
