package portfolioboss.api.alerts;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import portfolioboss.api.alerts.response.AlertResponse;
import portfolioboss.api.alerts.response.AlertSettingResponse;
import portfolioboss.calculation.alerts.AlertCondition;
import portfolioboss.calculation.alerts.AlertKey;
import portfolioboss.calculation.alerts.AlertSetting;
import portfolioboss.db.alerts.AlertEntity;
import portfolioboss.db.alerts.AlertRepository;
import portfolioboss.db.alerts.AlertSettingEntity;
import portfolioboss.db.alerts.AlertSettingRepository;
import portfolioboss.ib.Holding;
import portfolioboss.ib.PortfolioSnapshot;
import portfolioboss.model.alerts.AlertGroup;
import portfolioboss.model.alerts.AlertType;
import portfolioboss.sync.PortfolioSyncService;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * What the bell reads ({@code GET /api/alerts}) against the real PostgreSQL test database: the alerts are stored here
 * as a check would store them — which alerts a check makes is {@code alerts.AlertServiceTest}'s. Two made-up holdings,
 * ABCD and WXYZ, and a scanned stock not held, HOTA.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({AlertsReadService.class, PortfolioSyncService.class})
class AlertsReadServiceTest {

    private static final String ACCOUNT = "U7654321";
    private static final int ABCD_CON_ID = 8101;
    private static final int WXYZ_CON_ID = 8102;
    private static final int HOTA_CON_ID = 9001;
    private static final Instant SYNCED_AT = Instant.parse("2026-11-10T21:40:00Z");

    @Autowired
    private AlertsReadService alertsReadService;

    @Autowired
    private PortfolioSyncService portfolioSyncService;

    @Autowired
    private AlertRepository alertRepository;

    @Autowired
    private AlertSettingRepository alertSettingRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void syncTheTwoHoldings() {
        portfolioSyncService.sync(new PortfolioSnapshot(ACCOUNT, SYNCED_AT, 70_000.0, 50_000.0, List.of(
                holdingOf("ABCD", ABCD_CON_ID), holdingOf("WXYZ", WXYZ_CON_ID))));
    }

    @Test
    void theBellsListIsTheNewestFirstWithEachHoldingsId() {
        storeAlert(AlertType.FAR_BELOW_AVERAGE_COST, ABCD_CON_ID, "ABCD", SYNCED_AT);
        storeAlert(AlertType.FAR_BELOW_AVERAGE_COST, WXYZ_CON_ID, "WXYZ", SYNCED_AT.plusSeconds(60));

        List<AlertResponse> alerts = alertsReadService.currentAlerts().alerts();

        assertThat(alerts).extracting(AlertResponse::symbol).containsExactly("WXYZ", "ABCD");
        assertThat(alerts).extracting(AlertResponse::holdingId)
                .containsExactly(holdingIdOf(WXYZ_CON_ID), holdingIdOf(ABCD_CON_ID));
    }

    /** A stock of the short list that isn't held has no drawer to open: the bell links it to the page. */
    @Test
    void aStockNotHeldHasNoHoldingId() {
        storeAlert(AlertType.ENTERED_SHORT_LIST, HOTA_CON_ID, "HOTA", SYNCED_AT);

        assertThat(alertsReadService.currentAlerts().alerts()).singleElement().satisfies(alert -> {
            assertThat(alert.symbol()).isEqualTo("HOTA");
            assertThat(alert.holdingId()).isNull();
        });
    }

    /**
     * The panel's two groups (the user's rule: a stock held is first of all a holding). WXYZ is sold before the alerts are
     * read: its short-list alert is then a stock not held, while its warning stays a holding's own.
     */
    @Test
    void onlyAShortListAlertOfAStockNotHeldNowIsInTheShortListGroup() {
        portfolioSyncService.sync(new PortfolioSnapshot(ACCOUNT, SYNCED_AT.plusSeconds(3600), 70_000.0, 50_000.0,
                List.of(holdingOf("ABCD", ABCD_CON_ID))));
        storeAlert(AlertType.ENTERED_SHORT_LIST, HOTA_CON_ID, "HOTA", SYNCED_AT);
        storeAlert(AlertType.ENTERED_SHORT_LIST, ABCD_CON_ID, "ABCD", SYNCED_AT.plusSeconds(60));
        storeAlert(AlertType.ENTERED_SHORT_LIST, WXYZ_CON_ID, "WXYZ", SYNCED_AT.plusSeconds(120));
        storeAlert(AlertType.TRADES_DONT_MATCH_IB, WXYZ_CON_ID, "WXYZ", SYNCED_AT.plusSeconds(180));

        List<AlertResponse> alerts = alertsReadService.currentAlerts().alerts();

        assertThat(alerts).extracting(alert -> alert.alertType() + " " + alert.symbol(), AlertResponse::group)
                .containsExactly(
                        tuple("TRADES_DONT_MATCH_IB WXYZ", AlertGroup.YOUR_HOLDINGS),
                        tuple("ENTERED_SHORT_LIST WXYZ", AlertGroup.SHORT_LIST),
                        tuple("ENTERED_SHORT_LIST ABCD", AlertGroup.YOUR_HOLDINGS),
                        tuple("ENTERED_SHORT_LIST HOTA", AlertGroup.SHORT_LIST));
    }

    @Test
    void aTypeTurnedOffIsNotShown() {
        storeAlert(AlertType.FAR_BELOW_AVERAGE_COST, ABCD_CON_ID, "ABCD", SYNCED_AT);
        alertSettingRepository.save(new AlertSettingEntity(
                new AlertSetting(AlertType.FAR_BELOW_AVERAGE_COST, false, BigDecimal.valueOf(15))));

        assertThat(alertsReadService.currentAlerts().alerts()).isEmpty();
    }

    /** Every type, in the drawer's order: the number saved, or the type's own when none was. */
    @Test
    void eachTypesSettingIsTheSavedOneOrItsDefault() {
        alertSettingRepository.save(new AlertSettingEntity(
                new AlertSetting(AlertType.FAR_BELOW_AVERAGE_COST, true, BigDecimal.valueOf(10))));

        List<AlertSettingResponse> settings = alertsReadService.currentAlerts().settings();

        assertThat(settings).extracting(AlertSettingResponse::alertType).containsExactly(AlertType.values());
        assertThat(settings).filteredOn(setting -> setting.alertType() == AlertType.FAR_BELOW_AVERAGE_COST)
                .singleElement()
                .satisfies(setting -> {
                    assertThat(setting.threshold()).isEqualByComparingTo("10");
                    assertThat(setting.defaultThreshold()).isEqualByComparingTo("15");
                });
        assertThat(settings).filteredOn(setting -> setting.alertType() == AlertType.EARNINGS_SOON)
                .singleElement()
                .satisfies(setting -> {
                    assertThat(setting.enabled()).isTrue();
                    assertThat(setting.threshold()).isEqualByComparingTo("7");
                });
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────────────

    private void storeAlert(AlertType type, int conId, String symbol, Instant createdAt) {
        alertRepository.save(new AlertEntity(
                new AlertCondition(new AlertKey(type, conId, null), symbol, "A made-up alert of " + symbol), createdAt));
    }

    private Holding holdingOf(String symbol, int conId) {
        return new Holding(symbol, conId, "STK", "USD", 100, 100.0, 100.0, 10_000.0, 0.0, 0.0, ACCOUNT);
    }

    private long holdingIdOf(int conId) {
        return jdbc.queryForObject("select id from holding where account = ? and con_id = ?", Long.class, ACCOUNT, conId);
    }
}
