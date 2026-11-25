package portfolioboss.alerts;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import portfolioboss.calculation.alerts.AlertChangesCalculator;
import portfolioboss.calculation.alerts.AlertCondition;
import portfolioboss.calculation.alerts.AlertKey;
import portfolioboss.calculation.alerts.AlertSetting;
import portfolioboss.calculation.alerts.AlertSettings;
import portfolioboss.calculation.alerts.HoldingAlertsCalculator;
import portfolioboss.calculation.marketdata.DailyClosesWithSpy;
import portfolioboss.calculation.portfolio.PortfolioWeightsCalculator;
import portfolioboss.calculation.splits.StoredStockSplits;
import portfolioboss.db.alerts.AlertEntity;
import portfolioboss.db.alerts.AlertRepository;
import portfolioboss.db.alerts.AlertSettingEntity;
import portfolioboss.db.alerts.AlertSettingRepository;
import portfolioboss.db.marketdata.AnalystPriceTargetRepository;
import portfolioboss.db.marketdata.DailyCloseRepository;
import portfolioboss.db.marketdata.StockEntity;
import portfolioboss.db.marketdata.StockRepository;
import portfolioboss.db.portfolio.AccountStateEntity;
import portfolioboss.db.portfolio.AccountStateRepository;
import portfolioboss.db.portfolio.HoldingEntity;
import portfolioboss.db.portfolio.HoldingRepository;
import portfolioboss.db.recommendations.ScanCandidateEntity;
import portfolioboss.db.splits.StockSplitRepository;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.Holding;
import portfolioboss.model.alerts.AlertType;
import portfolioboss.model.portfolio.HoldingStatus;
import portfolioboss.recommendations.CandidateWithItsList;
import portfolioboss.recommendations.ScanListsService;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The alerts (MY_HOLDINGS_TODO.md, session 5ב, decisions 14–17): the check after every sync and after the settings are
 * saved, the bell's "seen", and the settings drawer's Save — what the bell shows is {@code api.alerts.AlertsReadService}'s.
 * It reads what the screen shows — the holdings as the last sync stored them, their closes, Finnhub's answer, the Dow
 * Jones targets, the latest scan's short list — and writes only the {@code alert} and {@code alert_setting} tables.
 */
@Service
public class AlertService {

    private final AlertRepository alertRepository;
    private final AlertSettingRepository alertSettingRepository;
    private final AccountStateRepository accountStateRepository;
    private final HoldingRepository holdingRepository;
    private final DailyCloseRepository dailyCloseRepository;
    private final StockRepository stockRepository;
    private final AnalystPriceTargetRepository analystPriceTargetRepository;
    private final ScanListsService scanListsService;
    private final StockSplitRepository stockSplitRepository;

    public AlertService(AlertRepository alertRepository, AlertSettingRepository alertSettingRepository,
                        AccountStateRepository accountStateRepository, HoldingRepository holdingRepository,
                        DailyCloseRepository dailyCloseRepository, StockRepository stockRepository,
                        AnalystPriceTargetRepository analystPriceTargetRepository,
                        ScanListsService scanListsService, StockSplitRepository stockSplitRepository) {
        this.alertRepository = alertRepository;
        this.alertSettingRepository = alertSettingRepository;
        this.accountStateRepository = accountStateRepository;
        this.holdingRepository = holdingRepository;
        this.dailyCloseRepository = dailyCloseRepository;
        this.stockRepository = stockRepository;
        this.analystPriceTargetRepository = analystPriceTargetRepository;
        this.scanListsService = scanListsService;
        this.stockSplitRepository = stockSplitRepository;
    }

    /**
     * The check: the conditions true now against the open alerts ({@link AlertChangesCalculator}) — a new alert for each
     * condition with none, an end for each open alert no longer true. A type turned off is left out of both sides, so
     * its open alerts stay as they are. Nothing before the first sync.
     */
    @Transactional
    public void checkAlerts() {
        AccountStateEntity latestSync = accountStateRepository.findFirstByOrderByAsOfDesc().orElse(null);
        if (latestSync == null) {
            return;
        }
        AlertSettings settings = alertSettingRepository.alertSettings();
        List<AlertCondition> conditionsTrueNow = new ArrayList<>(holdingConditionsOf(latestSync, settings));
        conditionsTrueNow.addAll(shortListConditions(settings));

        Map<AlertKey, AlertEntity> openAlertByKey = alertRepository.findByEndedAtIsNull().stream()
                .filter(openAlert -> settings.isEnabled(openAlert.alertType()))
                .collect(Collectors.toMap(AlertEntity::key, Function.identity()));
        AlertChangesCalculator changes = new AlertChangesCalculator(openAlertByKey.keySet(), conditionsTrueNow);
        Instant checkedAt = Instant.now();
        Set<AlertKey> keysNoLongerTrue = changes.keysOfAlertsNoLongerTrue();
        keysNoLongerTrue.forEach(key -> openAlertByKey.get(key).markEnded(checkedAt));   // written at commit
        List<AlertCondition> conditionsToAlert = changes.conditionsToAlert();
        alertRepository.saveAll(conditionsToAlert.stream()
                .map(condition -> new AlertEntity(condition, checkedAt))
                .toList());
        printCheck(conditionsToAlert, keysNoLongerTrue.size());
    }

    /** The bell was opened: every alert it shows is seen. */
    @Transactional
    public void markAllAsSeen() {
        List<AlertType> enabledTypes = alertSettingRepository.alertSettings().enabledTypes();
        if (!enabledTypes.isEmpty()) {
            alertRepository.markAsSeen(enabledTypes, Instant.now());
        }
    }

    /**
     * The settings drawer's Save: every type's line, then a check with the new numbers — no sync needed.
     *
     * @param settings every type once — the request was checked for it
     */
    @Transactional
    public void saveSettings(List<AlertSetting> settings) {
        alertSettingRepository.saveAll(settings.stream().map(AlertSettingEntity::new).toList());
        checkAlerts();
    }

    /** א to ו of every holding of the account, open or closed, as the last sync left them. */
    private List<AlertCondition> holdingConditionsOf(AccountStateEntity latestSync, AlertSettings settings) {
        List<HoldingEntity> holdings = holdingRepository.findByAccountOrderById(latestSync.account());
        List<Integer> conIds = holdings.stream().map(HoldingEntity::conId).toList();
        DailyClosesWithSpy dailyCloses = dailyCloseRepository.dailyClosesWithSpy(conIds);
        Map<Integer, StockEntity> stockByConId = stockRepository.stocksByConId(conIds);
        Map<Integer, List<AnalystPriceTarget>> analystPriceTargetsByConId =
                analystPriceTargetRepository.analystPriceTargetsByConId(conIds);
        StoredStockSplits stockSplits = stockSplitRepository.storedStockSplits();
        List<Holding> openHoldings = holdings.stream()
                .filter(holding -> holding.status() == HoldingStatus.OPEN)
                .map(HoldingEntity::toIbHolding)
                .toList();
        PortfolioWeightsCalculator portfolioWeights =
                new PortfolioWeightsCalculator(openHoldings, latestSync.totalCashValue());
        // the day the report and the cuts are counted from: the sync's, as the timing note on the screen counts it
        LocalDate syncDate = latestSync.asOf().atZone(ZoneId.systemDefault()).toLocalDate();

        List<AlertCondition> conditions = new ArrayList<>();
        for (HoldingEntity holdingEntity : holdings) {
            Holding holding = holdingEntity.toIbHolding();
            StockEntity stock = stockByConId.get(holding.conId());
            HoldingAlertsCalculator holdingAlerts = new HoldingAlertsCalculator(
                    holding,
                    holdingEntity.status(),
                    dailyCloses.momentumOf(holding.conId()),
                    stock == null ? null : stock.finnhubData(),
                    analystPriceTargetsByConId.getOrDefault(holding.conId(), List.of()),
                    holdingEntity.tradeHistory(stockSplits).warnings(holdingEntity.status(), holding.position()),
                    portfolioWeights.percentOfAccountOf(holding),
                    settings,
                    syncDate);
            conditions.addAll(holdingAlerts.conditionsTrueNow());
        }
        return conditions;
    }

    /**
     * ז: every stock of the latest scan's short list. The rule is the short list itself
     * ({@link ScanListsService#shortListOfLatestScan}), so there is nothing to compute here but the words.
     */
    private List<AlertCondition> shortListConditions(AlertSettings settings) {
        if (!settings.isEnabled(AlertType.ENTERED_SHORT_LIST)) {
            return List.of();
        }
        return scanListsService.shortListOfLatestScan().candidates().stream()
                .map(this::shortListConditionOf)
                .toList();
    }

    private AlertCondition shortListConditionOf(CandidateWithItsList candidateWithItsList) {
        ScanCandidateEntity candidate = candidateWithItsList.candidate();
        // Short enough for one line of the bell's panel (round 6's AlertsPanel); the target is the analysts' median.
        String message = String.format(Locale.ROOT,
                "Entered the short list: hot, target %.1f%% above the price",
                candidateWithItsList.analystTarget().upsidePercent());
        return new AlertCondition(new AlertKey(AlertType.ENTERED_SHORT_LIST, candidate.conId(), null),
                candidate.symbol(), message);
    }

    /** "[alerts] checked: 2 new (ABCD, WXYZ), 1 no longer true". */
    private void printCheck(List<AlertCondition> conditionsToAlert, int noLongerTrueCount) {
        String newSymbols = conditionsToAlert.stream()
                .map(AlertCondition::symbol)
                .distinct()
                .collect(Collectors.joining(", "));
        String newPart = conditionsToAlert.isEmpty() ? "nothing new"
                : conditionsToAlert.size() + " new (" + newSymbols + ")";
        System.out.println("[alerts] checked: " + newPart + ", " + noLongerTrueCount + " no longer true");
    }
}
