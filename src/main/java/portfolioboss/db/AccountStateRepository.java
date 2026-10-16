package portfolioboss.db;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Reads and writes {@link AccountStateEntity} rows. The id is the IB account, a {@code String}, so that is the
 * second type parameter. Spring generates the class at startup, like {@link HoldingRepository}.
 */
public interface AccountStateRepository extends JpaRepository<AccountStateEntity, String> {

    /** The account figures of the most recent sync. With a single account that is the only row. */
    Optional<AccountStateEntity> findFirstByOrderByAsOfDesc();
}
