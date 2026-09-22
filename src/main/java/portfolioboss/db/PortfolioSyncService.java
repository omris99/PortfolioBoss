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

    private final HoldingRepository holdings;
    private final AccountStateRepository accountStates;

    public PortfolioSyncService(HoldingRepository holdings, AccountStateRepository accountStates) {
        this.holdings = holdings;
        this.accountStates = accountStates;
    }

    @Transactional
    public SyncResult sync(PortfolioSnapshot snapshot) {
        String account = snapshot.account();
        Instant syncedAt = snapshot.asOf();
        Set<Integer> reportedConIds = new HashSet<>();
        int added = 0;
        int updated = 0;

        for (Holding reading : snapshot.holdings()) {
            reportedConIds.add(reading.conId());
            Optional<HoldingEntity> stored = holdings.findByAccountAndConId(account, reading.conId());
            if (stored.isPresent()) {
                stored.get().refreshFromIb(reading, syncedAt);   // Hibernate writes the change at commit
                updated++;
            } else {
                holdings.save(HoldingEntity.firstSeen(account, reading, syncedAt));
                added++;
            }
        }

        int closed = markMissingAsClosed(account, reportedConIds, syncedAt);
        accountStates.save(AccountStateEntity.of(snapshot));
        System.out.printf("[db] synced %d holdings (%d new, %d updated, %d closed)%n",
                reportedConIds.size(), added, updated, closed);
        return new SyncResult(added, updated, closed);
    }

    /**
     * Closing is reversible: if the holding shows up again it is reopened by {@code refreshFromIb}, so a partial
     * read from IB costs nothing permanent.
     */
    private int markMissingAsClosed(String account, Set<Integer> reportedConIds, Instant syncedAt) {
        int closed = 0;
        for (HoldingEntity stored : holdings.findByAccountAndStatus(account, HoldingStatus.OPEN)) {
            if (!reportedConIds.contains(stored.conId())) {
                stored.markClosed(syncedAt);
                closed++;
            }
        }
        return closed;
    }
}
