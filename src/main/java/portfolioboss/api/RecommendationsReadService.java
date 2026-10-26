package portfolioboss.api;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.api.response.CorrelationResponse;
import portfolioboss.api.response.HotStockResponse;
import portfolioboss.api.response.MomentumResponse;
import portfolioboss.api.response.RecommendationsResponse;
import portfolioboss.calculation.HotStockCalculator;
import portfolioboss.calculation.Momentum;
import portfolioboss.calculation.ReturnsCorrelation;
import portfolioboss.db.AccountStateRepository;
import portfolioboss.db.DailyCloseRepository;
import portfolioboss.db.HoldingEntity;
import portfolioboss.db.HoldingRepository;
import portfolioboss.db.InvestorRepository;
import portfolioboss.db.ScanCandidateEntity;
import portfolioboss.db.ScanEntity;
import portfolioboss.db.ScanRepository;
import portfolioboss.ib.Benchmark;
import portfolioboss.ib.DailyClose;
import portfolioboss.model.HoldingStatus;
import portfolioboss.model.HotStockLabel;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reads the latest scan the sync stored and turns it into the Recommendations page's three lists (RECOMMENDATIONS_TODO.md,
 * session 1): each stock's momentum from its stored closes, what the rule of decision 1 says about it, the open holdings
 * it moves with most and who holds it already. Like {@code PortfolioReadService} it reads only the database, and all of
 * it is worked out on this read — a change of rule applies to old scans too.
 */
@Service
public class RecommendationsReadService {

    /** The open holdings a stock moves with most, shown next to it. */
    private static final int MOST_CORRELATIONS_SHOWN = 3;

    private final ScanRepository scanRepository;
    private final AccountStateRepository accountStateRepository;
    private final HoldingRepository holdingRepository;
    private final InvestorRepository investorRepository;
    private final DailyCloseRepository dailyCloseRepository;

    public RecommendationsReadService(ScanRepository scanRepository, AccountStateRepository accountStateRepository,
                                      HoldingRepository holdingRepository, InvestorRepository investorRepository,
                                      DailyCloseRepository dailyCloseRepository) {
        this.scanRepository = scanRepository;
        this.accountStateRepository = accountStateRepository;
        this.holdingRepository = holdingRepository;
        this.investorRepository = investorRepository;
        this.dailyCloseRepository = dailyCloseRepository;
    }

    /**
     * Empty before the first scan. Built inside the transaction: with {@code open-in-view=false} that is the only place
     * where the scan's candidates and the holdings' trades can still be read.
     */
    @Transactional(readOnly = true)
    public Optional<RecommendationsResponse> currentRecommendations() {
        return scanRepository.findFirstByOrderByScannedAtDesc().map(this::toResponse);
    }

    /** A scanned stock that is in one of the lists, with the rule's verdict on it — kept together to be sorted. */
    private record StockInAList(ScanCandidateEntity candidate, HotStockCalculator hotStock) {
    }

    private RecommendationsResponse toResponse(ScanEntity scan) {
        List<HoldingEntity> openHoldings = openHoldings();
        Map<Integer, List<DailyClose>> dailyClosesByConId = dailyClosesOf(scan.candidates(), openHoldings);
        long accountOwnerId = investorRepository.accountOwner().id();
        Map<HotStockLabel, List<HotStockResponse>> stocksByList = scan.candidates().stream()
                .map(candidate -> stockInAListOf(candidate, dailyClosesByConId))
                .flatMap(Optional::stream)
                .sorted((firstStock, secondStock) -> firstStock.hotStock().comparePlaceInList(secondStock.hotStock()))
                .collect(Collectors.groupingBy(
                        stock -> stock.hotStock().label(),
                        () -> new EnumMap<>(HotStockLabel.class),
                        Collectors.mapping(
                                stock -> hotStockResponseOf(stock, openHoldings, dailyClosesByConId, accountOwnerId),
                                Collectors.toList())));
        return new RecommendationsResponse(
                scan.scannedAt(),
                scan.candidates().size(),
                stocksByList.getOrDefault(HotStockLabel.HOT, List.of()),
                stocksByList.getOrDefault(HotStockLabel.TOO_HOT, List.of()),
                stocksByList.getOrDefault(HotStockLabel.CORRECTING, List.of()));
    }

    /** The open holdings of the account the last sync read, with their trades — who holds what comes from them. */
    private List<HoldingEntity> openHoldings() {
        return accountStateRepository.findFirstByOrderByAsOfDesc()
                .map(accountState -> holdingRepository.findByAccountOrderById(accountState.account()))
                .orElse(List.of())
                .stream()
                .filter(holding -> holding.status() == HoldingStatus.OPEN)
                .toList();
    }

    /** The stored closes of every scanned stock, every open holding and SPY, by contract id. */
    private Map<Integer, List<DailyClose>> dailyClosesOf(List<ScanCandidateEntity> candidates,
                                                         List<HoldingEntity> openHoldings) {
        Set<Integer> conIds = new HashSet<>();
        candidates.forEach(candidate -> conIds.add(candidate.conId()));
        openHoldings.forEach(holding -> conIds.add(holding.conId()));
        conIds.add(Benchmark.SPY_CON_ID);
        return dailyCloseRepository.dailyClosesByConId(conIds);
    }

    /** Empty when the stock is in no list: no stored closes, or no long upward trend. */
    private Optional<StockInAList> stockInAListOf(ScanCandidateEntity candidate,
                                                  Map<Integer, List<DailyClose>> dailyClosesByConId) {
        Momentum momentum = Momentum.of(dailyClosesByConId.getOrDefault(candidate.conId(), List.of()),
                dailyClosesByConId.getOrDefault(Benchmark.SPY_CON_ID, List.of()));
        if (momentum == null) {
            return Optional.empty();
        }
        HotStockCalculator hotStock = new HotStockCalculator(momentum);
        return hotStock.label() == null ? Optional.empty() : Optional.of(new StockInAList(candidate, hotStock));
    }

    private HotStockResponse hotStockResponseOf(StockInAList stock, List<HoldingEntity> openHoldings,
                                                Map<Integer, List<DailyClose>> dailyClosesByConId,
                                                long accountOwnerId) {
        ScanCandidateEntity candidate = stock.candidate();
        HotStockCalculator hotStock = stock.hotStock();
        return new HotStockResponse(
                candidate.id(),
                candidate.conId(),
                candidate.symbol(),
                candidate.companyName(),
                candidate.exchange(),
                candidate.industry(),
                candidate.category(),
                hotStock.label(),
                new MomentumResponse(hotStock.momentum()),
                hotStock.percentAboveSma50(),
                hotStock.pulledBack(),
                hotStock.recovering(),
                correlationsWithHoldings(candidate, openHoldings, dailyClosesByConId),
                investorsHolding(candidate, openHoldings, accountOwnerId));
    }

    /** The open holdings the stock moves with most, highest first — not the stock itself when it is one of them. */
    private List<CorrelationResponse> correlationsWithHoldings(ScanCandidateEntity candidate,
                                                               List<HoldingEntity> openHoldings,
                                                               Map<Integer, List<DailyClose>> dailyClosesByConId) {
        List<DailyClose> candidateCloses = dailyClosesByConId.getOrDefault(candidate.conId(), List.of());
        Comparator<CorrelationResponse> highestFirst =
                Comparator.comparingDouble(CorrelationResponse::coefficient).reversed();
        return openHoldings.stream()
                .filter(holding -> holding.conId() != candidate.conId())
                .map(holding -> correlationResponseOf(holding, new ReturnsCorrelation(candidateCloses,
                        dailyClosesByConId.getOrDefault(holding.conId(), List.of()))))
                .filter(Objects::nonNull)
                .sorted(highestFirst)
                .limit(MOST_CORRELATIONS_SHOWN)
                .toList();
    }

    /** {@code null} when there is no coefficient: too few shared dates, or a price that never moved. */
    private CorrelationResponse correlationResponseOf(HoldingEntity holding, ReturnsCorrelation correlation) {
        Double coefficient = correlation.coefficient();
        if (coefficient == null) {
            return null;
        }
        return new CorrelationResponse(holding.id(), holding.symbol(), coefficient, correlation.movesTogether());
    }

    /** The investors who hold some of the stock, when it is an open holding already; empty otherwise. */
    private List<Long> investorsHolding(ScanCandidateEntity candidate, List<HoldingEntity> openHoldings,
                                        long accountOwnerId) {
        return openHoldings.stream()
                .filter(holding -> holding.conId() == candidate.conId())
                .flatMap(holding -> holding.toPositionTrades().partsByInvestor(accountOwnerId).keySet().stream())
                .toList();
    }
}
