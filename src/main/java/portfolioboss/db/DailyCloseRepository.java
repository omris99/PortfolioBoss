package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/** Reads and writes {@link DailyCloseEntity} rows; Spring generates the class at startup, like {@link HoldingRepository}. */
public interface DailyCloseRepository extends JpaRepository<DailyCloseEntity, Long> {

    /** The closes of these contracts, each contract's in date order. */
    List<DailyCloseEntity> findByConIdInOrderByConIdAscBarDateAsc(Collection<Integer> conIds);

    /**
     * One {@code DELETE} statement for all of these contracts' rows. {@code @Query} writes the query by hand (in JPQL,
     * over the entity's names) and {@code @Modifying} says it changes rows: a derived {@code deleteBy…} method would
     * first load every row and then delete them one at a time.
     */
    @Modifying
    @Query("delete from DailyCloseEntity dailyClose where dailyClose.conId in :conIds")
    void deleteForContracts(@Param("conIds") Collection<Integer> conIds);
}
