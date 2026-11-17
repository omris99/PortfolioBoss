package portfolioboss.recommendations;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.calculation.marketdata.AnalystTargetCalculator;
import portfolioboss.calculation.marketdata.DailyClosesWithSpy;
import portfolioboss.calculation.marketdata.Momentum;
import portfolioboss.calculation.recommendations.HotStockCalculator;
import portfolioboss.db.marketdata.AnalystPriceTargetRepository;
import portfolioboss.db.marketdata.DailyCloseRepository;
import portfolioboss.db.recommendations.ScanCandidateEntity;
import portfolioboss.db.recommendations.ScanEntity;
import portfolioboss.db.recommendations.ScanRepository;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.model.recommendations.HotStockLabel;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The lists of the latest scan (RECOMMENDATIONS_TODO.md, decisions 1 and 14): the stocks in each of hot, too hot and
 * correcting, in the page's order, and the short list the paid run analyzes — the first 10 HOT stocks whose analysts'
 * target is at least 15% above the last close. Worked out on every call from the stored scan, closes and Dow Jones
 * targets, the same for its three readers: the Recommendations page, its Analyze buttons, and the short-list alert.
 */
@Service
public class ScanListsService {

    /** The first HOT stocks with enough upside, in the page's order, are the short list the full run analyzes. */
    private static final int SHORT_LIST_SIZE = 10;

    private final ScanRepository scanRepository;
    private final DailyCloseRepository dailyCloseRepository;
    private final AnalystPriceTargetRepository analystPriceTargetRepository;

    public ScanListsService(ScanRepository scanRepository, DailyCloseRepository dailyCloseRepository,
                            AnalystPriceTargetRepository analystPriceTargetRepository) {
        this.scanRepository = scanRepository;
        this.dailyCloseRepository = dailyCloseRepository;
        this.analystPriceTargetRepository = analystPriceTargetRepository;
    }

    /**
     * The stocks of the latest scan that are in one of the lists, in the page's order — HOT first —, worked out exactly
     * as for the page: what the Recommendations page's Analyze works on. Empty before the first scan.
     */
    @Transactional(readOnly = true)
    public List<CandidateWithItsList> candidatesWithTheirListsOfLatestScan() {
        return scanRepository.findFirstByOrderByScannedAtDesc()
                .map(scan -> candidatesWithTheirLists(scan, dailyClosesWithSpyOf(scan.candidates())))
                .orElse(List.of());
    }

    /**
     * The short list the full run analyzes (decision 14): the first 10 HOT stocks of the latest scan whose analysts'
     * target is at least 15% above the last close — the same the page marks. Without a scan, no scan id and no stock.
     */
    @Transactional(readOnly = true)
    public ShortList shortListOfLatestScan() {
        return scanRepository.findFirstByOrderByScannedAtDesc()
                .map(scan -> shortListOf(scan, candidatesWithTheirLists(scan, dailyClosesWithSpyOf(scan.candidates()))))
                .orElse(new ShortList(null, 0, 0, List.of()));
    }

    /**
     * The scan's candidates in one of the lists, in the page's order, each with its analysts' target from the stored
     * Dow Jones targets — the half year counted back from the scan's day; a candidate in no list is left out.
     *
     * @param dailyCloses the stored closes of the scan's stocks and SPY, at least — the page passes the ones it read
     *                    for the correlations too
     */
    public List<CandidateWithItsList> candidatesWithTheirLists(ScanEntity scan, DailyClosesWithSpy dailyCloses) {
        Set<Integer> conIds = scan.candidates().stream().map(ScanCandidateEntity::conId).collect(Collectors.toSet());
        Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsByConId =
                analystPriceTargetRepository.analystPriceTargetsByConId(conIds);
        LocalDate scanDate = dateOf(scan.scannedAt());
        return scan.candidates().stream()
                .map(candidate -> candidateWithItsListOf(candidate, dailyCloses,
                        analystPriceTargetsByConId.getOrDefault(candidate.conId(), List.of()), scanDate))
                .flatMap(Optional::stream)
                .sorted((firstCandidate, secondCandidate) ->
                        firstCandidate.hotStock().comparePlaceInList(secondCandidate.hotStock()))
                .toList();
    }

    /** @param candidatesWithTheirLists the scan's, as {@link #candidatesWithTheirLists} gives them */
    public ShortList shortListOf(ScanEntity scan, List<CandidateWithItsList> candidatesWithTheirLists) {
        List<CandidateWithItsList> hotStocks = candidatesWithTheirLists.stream()
                .filter(candidateWithItsList -> candidateWithItsList.hotStock().label() == HotStockLabel.HOT)
                .toList();
        List<CandidateWithItsList> withEnoughUpside = hotStocks.stream()
                .filter(candidateWithItsList -> candidateWithItsList.analystTarget().hasEnoughUpside())
                .toList();
        return new ShortList(scan.id(), hotStocks.size(), withEnoughUpside.size(),
                withEnoughUpside.stream().limit(SHORT_LIST_SIZE).toList());
    }

    /** The stored closes of the scan's stocks and SPY. */
    private DailyClosesWithSpy dailyClosesWithSpyOf(List<ScanCandidateEntity> candidates) {
        return dailyCloseRepository.dailyClosesWithSpy(
                candidates.stream().map(ScanCandidateEntity::conId).collect(Collectors.toSet()));
    }

    /** Empty when the stock is in no list: no stored closes, or no long upward trend. */
    private Optional<CandidateWithItsList> candidateWithItsListOf(ScanCandidateEntity candidate,
                                                                  DailyClosesWithSpy dailyCloses,
                                                                  List<AnalystPriceTarget> analystPriceTargets,
                                                                  LocalDate scanDate) {
        Momentum momentum = dailyCloses.momentumOf(candidate.conId());
        if (momentum == null) {
            return Optional.empty();
        }
        HotStockCalculator hotStock = new HotStockCalculator(momentum);
        if (hotStock.label() == null) {
            return Optional.empty();
        }
        AnalystTargetCalculator analystTarget =
                new AnalystTargetCalculator(analystPriceTargets, momentum.lastClose(), scanDate);
        return Optional.of(new CandidateWithItsList(candidate, hotStock, analystTarget));
    }

    private LocalDate dateOf(Instant instant) {
        return instant.atZone(ZoneId.systemDefault()).toLocalDate();
    }
}
