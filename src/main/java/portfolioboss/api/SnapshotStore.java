package portfolioboss.api;

import org.springframework.stereotype.Component;
import portfolioboss.model.PortfolioSnapshot;

import java.util.Optional;

/**
 * Holds the snapshot {@code Main} read from TWS so the controller can serve it. Temporary: it goes
 * away once the snapshot is stored in the database.
 *
 * <p>Spring creates one instance at startup and hands it to whoever asks for it in a constructor.
 * The snapshot is written by the startup thread and read by web threads, hence {@code volatile}.
 */
@Component
public class SnapshotStore {

    private volatile PortfolioSnapshot snapshot;

    public void set(PortfolioSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    /** Empty until the startup runner has read the portfolio from TWS. */
    public Optional<PortfolioSnapshot> current() {
        return Optional.ofNullable(snapshot);
    }
}
