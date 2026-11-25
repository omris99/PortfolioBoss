package portfolioboss.alerts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import portfolioboss.calculation.alerts.AlertSetting;
import portfolioboss.calculation.marketdata.AnalystTargetCalculator;
import portfolioboss.calculation.marketdata.Momentum;
import portfolioboss.calculation.recommendations.HotStockCalculator;
import portfolioboss.calculation.splits.StockSplit;
import portfolioboss.db.alerts.AlertSettingEntity;
import portfolioboss.db.alerts.AlertSettingRepository;
import portfolioboss.db.recommendations.ScanCandidateEntity;
import portfolioboss.db.recommendations.ScanRepository;
import portfolioboss.db.splits.StockSplitEntity;
import portfolioboss.db.splits.StockSplitRepository;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;
import portfolioboss.model.alerts.AlertType;
import portfolioboss.recommendations.CandidateWithItsList;
import portfolioboss.recommendations.ScanListsService;
import portfolioboss.recommendations.ShortList;
import portfolioboss.sync.PortfolioSyncService;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * The check, "seen" and the settings' Save against the real PostgreSQL test database — what they store in the
 * {@code alert} and {@code alert_setting} tables; what the bell reads of them is {@code AlertsReadServiceTest}'s —, with
 * the short list replaced by a stand-in ({@code @MockitoBean}). Two made-up holdings, ABCD and WXYZ, 100 shares each at
 * an average of $100; a P&L of −2,000 is −20%. The "trades" alert is turned off in each test — a holding synced without
 * trades has the "no buy entered" warning —, except in the one about it.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AlertService.class, PortfolioSyncService.class})
class AlertServiceTest {

    private static final String ACCOUNT = "U7654321";
    private static final int ABCD_CON_ID = 8101;
    private static final int WXYZ_CON_ID = 8102;
    private static final Instant FIRST_SYNC = Instant.parse("2026-11-10T21:40:00Z");
    private static final Instant SECOND_SYNC = Instant.parse("2026-11-11T21:40:00Z");
    private static final Instant THIRD_SYNC = Instant.parse("2026-11-12T21:40:00Z");
    private static final AlertSetting TRADES_ALERT_OFF = new AlertSetting(AlertType.TRADES_DONT_MATCH_IB, false, null);

    @Autowired
    private AlertService alertService;

    @Autowired
    private PortfolioSyncService portfolioSyncService;

    @Autowired
    private AlertSettingRepository alertSettingRepository;

    @Autowired
    private ScanRepository scanRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private StockSplitRepository stockSplitRepository;

    @MockitoBean
    private ScanListsService scanListsService;

    @BeforeEach
    void turnOffTheTradesAlertAndEmptyTheShortList() {
        alertSettingRepository.save(new AlertSettingEntity(TRADES_ALERT_OFF));
        given(scanListsService.shortListOfLatestScan()).willReturn(new ShortList(null, 0, 0, List.of()));
    }

    @Test
    void nothingIsCheckedBeforeTheFirstSync() {
        alertService.checkAlerts();

        assertThat(countOf("select count(*) from alert")).isZero();
    }

    /** Nothing is open yet, so what is true now becomes an alert: the starting point. */
    @Test
    void theFirstCheckAlertsWhatIsTrueNow() {
        syncAt(FIRST_SYNC, -2_000, 0);

        alertService.checkAlerts();

        assertThat(jdbc.queryForMap("select alert_type, con_id, symbol, subject, message, ended_at, seen_at from alert"))
                .containsEntry("alert_type", "FAR_BELOW_AVERAGE_COST")
                .containsEntry("con_id", ABCD_CON_ID)
                .containsEntry("symbol", "ABCD")
                .containsEntry("subject", null)
                .containsEntry("message", "Down 20.0% from the average cost of 100.00")
                .containsEntry("ended_at", null)
                .containsEntry("seen_at", null);
    }

    @Test
    void aSecondCheckOfTheSameFiguresAddsNothing() {
        syncAt(FIRST_SYNC, -2_000, 0);
        alertService.checkAlerts();

        syncAt(SECOND_SYNC, -2_000, 0);
        alertService.checkAlerts();

        assertThat(countOf("select count(*) from alert")).isEqualTo(1);
    }

    /** No longer true: the alert ends. True again: a new alert, and the ended one stays in the history. */
    @Test
    void aConditionNoLongerTrueEndsAndComingBackIsANewAlert() {
        syncAt(FIRST_SYNC, -2_000, 0);
        alertService.checkAlerts();

        syncAt(SECOND_SYNC, -500, 0);
        alertService.checkAlerts();
        assertThat(countOf("select count(*) from alert where ended_at is not null")).isEqualTo(1);

        syncAt(THIRD_SYNC, -2_000, 0);
        alertService.checkAlerts();
        assertThat(countOf("select count(*) from alert")).isEqualTo(2);
        assertThat(countOf("select count(*) from alert where ended_at is null")).isEqualTo(1);
    }

    @Test
    void aHoldingsWarningIsAnAlertWhenTheTradesAlertIsOn() {
        alertSettingRepository.deleteAll();   // every type on, as it starts
        syncAt(FIRST_SYNC, 0, 0);

        alertService.checkAlerts();

        assertThat(jdbc.queryForList("select subject from alert where alert_type = 'TRADES_DONT_MATCH_IB'",
                String.class)).containsExactly("NO_TRADES_LOGGED", "NO_TRADES_LOGGED");
    }

    /** STOCK_SPLITS_TODO.md: 25 ABCD typed in before a split of 4 for 1 are IB's 100 — no warning, so no alert. */
    @Test
    void tradesHeldThroughASplitMatchIbAndMakeNoAlert() {
        alertSettingRepository.deleteAll();   // every type on, as it starts
        syncAt(FIRST_SYNC, 0, 0);
        typeInABuy(ABCD_CON_ID, LocalDate.of(2026, 10, 1), "25", "400");
        typeInABuy(WXYZ_CON_ID, LocalDate.of(2026, 10, 1), "100", "100");
        stockSplitRepository.save(new StockSplitEntity(new StockSplit("A1", "ABCD", ABCD_CON_ID, ABCD_CON_ID,
                LocalDateTime.of(2026, 10, 15, 20, 25), 1, 4), FIRST_SYNC));
        entityManager.flush();
        entityManager.clear();

        alertService.checkAlerts();

        assertThat(countOf("select count(*) from alert where alert_type = 'TRADES_DONT_MATCH_IB'")).isZero();
    }

    @Test
    void aStockInTheShortListIsAnAlertWithoutAHolding() {
        syncAt(FIRST_SYNC, 0, 0);
        given(scanListsService.shortListOfLatestScan()).willReturn(shortListOfHota());

        alertService.checkAlerts();

        assertThat(jdbc.queryForMap("select alert_type, con_id, symbol, subject, message from alert"))
                .containsEntry("alert_type", "ENTERED_SHORT_LIST")
                .containsEntry("con_id", 9001)
                .containsEntry("symbol", "HOTA")
                .containsEntry("subject", null)
                .containsEntry("message", "Entered the short list: hot, target 30.0% above the price");
    }

    /**
     * Off: not checked — WXYZ falling as far makes no alert — and an alert open when it was turned off isn't ended,
     * nobody looked. That the bell doesn't show it is {@code AlertsReadServiceTest}'s.
     */
    @Test
    void aTypeTurnedOffIsNotCheckedAndItsOpenAlertStaysOpen() {
        syncAt(FIRST_SYNC, -2_000, 0);
        alertService.checkAlerts();

        alertService.saveSettings(settingsWith(TRADES_ALERT_OFF,
                new AlertSetting(AlertType.FAR_BELOW_AVERAGE_COST, false, BigDecimal.valueOf(15))));
        syncAt(SECOND_SYNC, -2_000, -2_000);
        alertService.checkAlerts();

        assertThat(jdbc.queryForList("select symbol from alert where ended_at is null", String.class))
                .containsExactly("ABCD");
    }

    /** −12% is not 15% below the average, but it is 10% below: saving the new number checks again, without a sync. */
    @Test
    void savingTheSettingsChecksAgainWithTheNewNumbers() {
        syncAt(FIRST_SYNC, -1_200, 0);
        alertService.checkAlerts();
        assertThat(countOf("select count(*) from alert")).isZero();

        alertService.saveSettings(settingsWith(TRADES_ALERT_OFF,
                new AlertSetting(AlertType.FAR_BELOW_AVERAGE_COST, true, BigDecimal.valueOf(10))));

        assertThat(countOf("select count(*) from alert where alert_type = 'FAR_BELOW_AVERAGE_COST'")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select threshold from alert_setting where alert_type = 'FAR_BELOW_AVERAGE_COST'",
                BigDecimal.class)).isEqualByComparingTo("10");
    }

    /** Opening the bell sees everything there is; an alert that comes later is new again. */
    @Test
    void markingAsSeenSeesWhatThereIsAndALaterAlertIsUnseen() {
        syncAt(FIRST_SYNC, -2_000, 0);
        alertService.checkAlerts();

        alertService.markAllAsSeen();
        syncAt(SECOND_SYNC, -2_000, -2_000);
        alertService.checkAlerts();

        assertThat(jdbc.queryForList("select symbol from alert where seen_at is null", String.class))
                .containsExactly("WXYZ");
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    /** ABCD and WXYZ, 100 shares each at an average of $100, with these unrealized P&Ls; $50,000 of cash. */
    private void syncAt(Instant syncedAt, double abcdUnrealizedPnl, double wxyzUnrealizedPnl) {
        portfolioSyncService.sync(new PortfolioSnapshot(ACCOUNT, syncedAt, 70_000.0, 50_000.0, List.of(
                holdingOf("ABCD", ABCD_CON_ID, abcdUnrealizedPnl),
                holdingOf("WXYZ", WXYZ_CON_ID, wxyzUnrealizedPnl))));
    }

    private Holding holdingOf(String symbol, int conId, double unrealizedPnl) {
        double marketValue = 10_000 + unrealizedPnl;
        return new Holding(symbol, conId, "STK", "USD", 100, 100.0, marketValue / 100, marketValue, unrealizedPnl,
                0.0, ACCOUNT);
    }

    /** Every type as it starts, but for the lines given — the whole drawer, as its Save sends it. */
    private List<AlertSetting> settingsWith(AlertSetting... changedSettings) {
        return Arrays.stream(AlertType.values())
                .map(type -> Arrays.stream(changedSettings)
                        .filter(changedSetting -> changedSetting.type() == type)
                        .findFirst()
                        .orElse(new AlertSetting(type, true, type.defaultThreshold())))
                .toList();
    }

    /**
     * A stored scan's one stock, HOTA, as the short list hands it over: three firms' targets of $130 against a last close
     * of $100, so 30% above the price.
     */
    private ShortList shortListOfHota() {
        int hotaConId = 9001;
        Instant scannedAt = Instant.parse("2026-11-10T21:05:00Z");
        portfolioSyncService.syncScan(new Scan(scannedAt,
                List.of(new ScanRow("SCAN_histPerfRatio3m_DESC", 0, hotaConId, "HOTA")),
                Map.of(hotaConId, new StockContractDetails(hotaConId, "NASDAQ", "HOTA INC", "Technology", "Software"))),
                Map.of());
        ScanCandidateEntity hota = scanRepository.findFirstByOrderByScannedAtDesc().orElseThrow().candidates().getFirst();
        LocalDate scanDate = LocalDate.of(2026, 11, 10);
        List<AnalystPriceTarget> targets = List.of("Firm A", "Firm B", "Firm C").stream()
                .map(firm -> new AnalystPriceTarget(scanDate.minusDays(10).atTime(9, 0), firm, 130.0, 120.0,
                        "HOTA Price Target Raised to $130.00/Share From $120.00 by " + firm))
                .toList();
        Momentum momentum = new Momentum(scanDate, 100.0, 98.0, 95.0, 80.0, 102.0, 6.0, 2.0, scanDate.minusYears(1),
                104.0, 40.0, 10.0);
        CandidateWithItsList hotaInTheShortList = new CandidateWithItsList(hota, new HotStockCalculator(momentum),
                new AnalystTargetCalculator(targets, 100.0, scanDate));
        return new ShortList(1L, 1, 1, List.of(hotaInTheShortList));
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }

    /** The account owner's buy, without a commission. */
    private void typeInABuy(int conId, LocalDate tradeDate, String quantity, String price) {
        entityManager.flush();
        long holdingId = jdbc.queryForObject("select id from holding where con_id = ?", Long.class, conId);
        long accountOwnerId = jdbc.queryForObject("select id from investor where is_account_owner", Long.class);
        jdbc.update("insert into trade (holding_id, investor_id, trade_date, side, quantity, price, commission) "
                        + "values (?, ?, ?, 'BUY', ?, ?, 0)",
                holdingId, accountOwnerId, tradeDate, new BigDecimal(quantity), new BigDecimal(price));
    }
}
