package portfolioboss.db.recommendationlists;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface StockListsDownloadRepository extends JpaRepository<StockListsDownloadEntity, Long> {

    /** The latest download, its stocks read lazily inside the caller's transaction; empty before the first one. */
    Optional<StockListsDownloadEntity> findFirstByOrderByDownloadedAtDesc();
}
