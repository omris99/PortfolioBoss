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
import portfolioboss.alerts.request.AlertSettingRequest;
import portfolioboss.alerts.request.AlertSettingsRequest;
import portfolioboss.alerts.response.AlertResponse;
import portfolioboss.alerts.response.AlertsResponse;
import portfolioboss.api.CandidateWithItsList;
import portfolioboss.api.RecommendationsReadService;
import portfolioboss.api.ShortList;
import portfolioboss.calculation.AnalystTargetCalculator;
import portfolioboss.calculation.HotStockCalculator;
import portfolioboss.calculation.Momentum;
import portfolioboss.db.PortfolioSyncService;
import portfolioboss.db.ScanCandidateEntity;
import portfolioboss.db.ScanRepository;
import portfolioboss.ib.AnalystPriceTarget;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.ib.Scan;
import portfolioboss.ib.ScanRow;
import portfolioboss.ib.StockContractDetails;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * The check, the bell's list, "seen" and the settings against the real PostgreSQL test database, with the short list
 * replaced by a stand-in ({@code @MockitoBean}). Two made-up holdings, ABCD and WXYZ, 100 shares each at an average of
 * $100; a P&L of −2,000 is −20%. The "trades" alert is turned off in each test — a holding synced without trades has the
 * "no buy entered" warning —, except in the one about it.
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
    private static final AlertSettingRequest TRADES_ALERT_OFF =
            new AlertSettingRequest(AlertType.TRADES_DONT_MATCH_IB, false, null);

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

    @MockitoBean
    private RecommendationsReadService recommendationsReadService;

    @BeforeEach
    void turnOffTheTradesAlertAndEmptyTheShortList() {
        alertSettingRepository.save(new AlertSettingEntity(TRADES_ALERT_OFF.toAlertSetting()));
        given(recommendationsReadService.shortListOfLatestScan()).willReturn(new ShortList(null, 0, 0, List.of()));
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

    @Test
    void aStockInTheShortListIsAnAlertWithoutAHolding() {
        syncAt(FIRST_SYNC, 0, 0);
        given(recommendationsReadService.shortListOfLatestScan()).willReturn(shortListOfHota());

        alertService.checkAlerts();
        AlertsResponse alerts = alertService.currentAlerts();

        assertThat(alerts.alerts()).singleElement().satisfies(alert -> {
            assertThat(alert.alertType()).isEqualTo(AlertType.ENTERED_SHORT_LIST);
            assertThat(alert.symbol()).isEqualTo("HOTA");
            assertThat(alert.holdingId()).isNull();
            assertThat(alert.message()).isEqualTo("Entered the short list: hot, the analysts' target 30.0% above the price");
        });
    }

    /** Off: not checked and not shown — and an alert open when it was turned off isn't ended, nobody looked. */
    @Test
    void aTypeTurnedOffIsNeitherCheckedNorShown() {
        syncAt(FIRST_SYNC, -2_000, 0);
        alertService.checkAlerts();

        alertService.saveSettings(settingsRequestWith(TRADES_ALERT_OFF,
                new AlertSettingRequest(AlertType.FAR_BELOW_AVERAGE_COST, false, BigDecimal.valueOf(15))));

        assertThat(countOf("select count(*) from alert where ended_at is null")).isEqualTo(1);
        assertThat(alertService.currentAlerts().alerts()).isEmpty();
    }

    /** −12% is not 15% below the average, but it is 10% below: saving the new number checks again, without a sync. */
    @Test
    void savingTheSettingsChecksAgainWithTheNewNumbers() {
        syncAt(FIRST_SYNC, -1_200, 0);
        alertService.checkAlerts();
        assertThat(countOf("select count(*) from alert")).isZero();

        alertService.saveSettings(settingsRequestWith(TRADES_ALERT_OFF,
                new AlertSettingRequest(AlertType.FAR_BELOW_AVERAGE_COST, true, BigDecimal.valueOf(10))));

        assertThat(countOf("select count(*) from alert where alert_type = 'FAR_BELOW_AVERAGE_COST'")).isEqualTo(1);
        assertThat(alertService.currentAlerts().settings())
                .filteredOn(setting -> setting.alertType() == AlertType.FAR_BELOW_AVERAGE_COST)
                .singleElement()
                .satisfies(setting -> {
                    assertThat(setting.threshold()).isEqualByComparingTo("10");
                    assertThat(setting.defaultThreshold()).isEqualByComparingTo("15");
                });
    }

    @Test
    void theBellsListIsTheNewestFirstWithEachHoldingsId() {
        syncAt(FIRST_SYNC, -2_000, -2_000);
        alertService.checkAlerts();

        List<AlertResponse> alerts = alertService.currentAlerts().alerts();

        assertThat(alerts).extracting(AlertResponse::symbol).containsExactly("WXYZ", "ABCD");
        assertThat(alerts).extracting(AlertResponse::holdingId)
                .containsExactly(holdingIdOf(WXYZ_CON_ID), holdingIdOf(ABCD_CON_ID));
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

    /** Every type as it starts, but for the lines given. */
    private AlertSettingsRequest settingsRequestWith(AlertSettingRequest... changedSettings) {
        List<AlertSettingRequest> settings = Arrays.stream(AlertType.values())
                .map(type -> Arrays.stream(changedSettings)
                        .filter(changedSetting -> changedSetting.alertType() == type)
                        .findFirst()
                        .orElse(new AlertSettingRequest(type, true, type.defaultThreshold())))
                .toList();
        return new AlertSettingsRequest(settings);
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

    private long holdingIdOf(int conId) {
        return jdbc.queryForObject("select id from holding where account = ? and con_id = ?", Long.class, ACCOUNT, conId);
    }

    private int countOf(String countQuery) {
        entityManager.flush();
        return jdbc.queryForObject(countQuery, Integer.class);
    }
}
