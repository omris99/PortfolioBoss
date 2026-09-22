package portfolioboss.db;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.model.Holding;
import portfolioboss.model.PortfolioSnapshot;

import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * Brings the database up to date with one snapshot from TWS, once per connection. A holding TWS reported is
 * created or refreshed; an open holding TWS no longer reports is marked closed, never deleted, so the
 * sector and trades entered by hand survive.
 *
 * <p>Everything happens in one transaction: either the whole snapshot is stored or nothing is.
 */
@Service
public class PortfolioSyncService {

    private final HoldingRepository holdingRepository;
    private final AccountStateRepository accountStateRepository;

    public PortfolioSyncService(HoldingRepository holdingRepository, AccountStateRepository accountStateRepository) {
        this.holdingRepository = holdingRepository;
        this.accountStateRepository = accountStateRepository;
    }

    @Transactional
    public SyncResult sync(PortfolioSnapshot snapshot) {
        String account = snapshot.account();
        Instant syncedAt = snapshot.asOf();
        Set<Integer> reportedConIds = new HashSet<>();
        int addedCount = 0;
        int updatedCount = 0;

        for (Holding holdingFromIb : snapshot.holdings()) {
            reportedConIds.add(holdingFromIb.conId());
            Optional<HoldingEntity> storedHolding =
                    holdingRepository.findByAccountAndConId(account, holdingFromIb.conId());
            if (storedHolding.isPresent()) {
                storedHolding.get().refreshFromIb(holdingFromIb, syncedAt);   // Hibernate writes the change at commit
                updatedCount++;
            } else {
                holdingRepository.save(new HoldingEntity(account, holdingFromIb, syncedAt));
                addedCount++;
            }
        }

        int closedCount = markMissingAsClosed(account, reportedConIds, syncedAt);
        accountStateRepository.save(new AccountStateEntity(snapshot));
        System.out.printf("[db] synced %d holdings (%d new, %d updated, %d closed)%n",
                reportedConIds.size(), addedCount, updatedCount, closedCount);
        return new SyncResult(addedCount, updatedCount, closedCount);
    }

    /**
     * Closing is reversible: if the holding shows up again it is reopened by {@code refreshFromIb}, so a partial
     * read from IB costs nothing permanent.
     */
    private int markMissingAsClosed(String account, Set<Integer> reportedConIds, Instant syncedAt) {
        int closedCount = 0;
        for (HoldingEntity openHolding : holdingRepository.findByAccountAndStatus(account, HoldingStatus.OPEN)) {
            if (!reportedConIds.contains(openHolding.conId())) {
                openHolding.markClosed(syncedAt);
                closedCount++;
            }
        }
        return closedCount;
    }
}
