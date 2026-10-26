package portfolioboss.api;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import portfolioboss.api.response.ScanRunResponse;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.exceptions.TwsBusyException;
import portfolioboss.exceptions.TwsUnavailableException;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanReading;
import portfolioboss.ib.TwsReader;

/**
 * The Scan again button (RECOMMENDATIONS_TODO.md, session 1b): a new scan for hot stocks on demand, beyond
 * {@code ./run.sh}'s once a trading day. It reads TWS through {@link TwsReader} — the one place that connects — and
 * stores the scan with its stocks' daily closes; nothing else. Free: it asks only IB.
 *
 * <p>Not {@code @Transactional}: reading TWS takes seconds, and no database connection should wait on it. The store at
 * the end is a transaction of its own.
 */
@Service
public class ScanService {

    private final TwsReader twsReader;
    private final PortfolioSyncService syncService;

    public ScanService(TwsReader twsReader, PortfolioSyncService syncService) {
        this.twsReader = twsReader;
        this.syncService = syncService;
    }

    /** 409 while another reading from TWS runs, 503 when TWS can't be read or no scan answered — the message says which. */
    public ScanRunResponse scanAgain() throws InterruptedException {
        ScanReading reading;
        try {
            reading = twsReader.readScan();
        } catch (TwsBusyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (TwsUnavailableException e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        }
        Scan scan = reading.scan();
        syncService.syncScan(scan, reading.dailyCloses());
        return new ScanRunResponse(scan.scannedAt(), scan.candidates().size());
    }
}
