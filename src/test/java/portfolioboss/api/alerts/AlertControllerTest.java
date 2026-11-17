package portfolioboss.api.alerts;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import portfolioboss.alerts.AlertService;
import portfolioboss.api.alerts.response.AlertResponse;
import portfolioboss.api.alerts.response.AlertSettingResponse;
import portfolioboss.api.alerts.response.AlertsResponse;
import portfolioboss.model.alerts.AlertType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The HTTP side of the bell: {@code GET /api/alerts}, {@code POST /api/alerts/seen} and {@code PUT /api/alerts/settings}
 * — the JSON, the status codes, the settings' validation, and the JSON-only rule of the two writes. {@link AlertService}
 * is replaced by a stand-in; what it stores is tested in {@code AlertServiceTest}.
 */
@WebMvcTest(AlertController.class)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AlertService alertService;

    @Test
    void answersTheAlertsAndTheSettingsNotToBeKept() throws Exception {
        given(alertService.currentAlerts()).willReturn(new AlertsResponse(
                List.of(new AlertResponse(2, AlertType.ANALYST_CUT_TARGET, 8101, "ABCD", 12L,
                        "Morgan Stanley cut its target to 410.00 from 450.00", Instant.parse("2026-11-11T21:40:00Z"),
                        null)),
                List.of(new AlertSettingResponse(AlertType.ABOVE_WEIGHT_LIMIT, true, BigDecimal.valueOf(20),
                        BigDecimal.valueOf(15)))));

        mockMvc.perform(get("/api/alerts"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.alerts[0].alertType").value("ANALYST_CUT_TARGET"))
                .andExpect(jsonPath("$.alerts[0].holdingId").value(12))
                .andExpect(jsonPath("$.alerts[0].createdAt").value("2026-11-11T21:40:00Z"))
                .andExpect(jsonPath("$.alerts[0].seenAt").isEmpty())
                .andExpect(jsonPath("$.settings[0].threshold").value(20))
                .andExpect(jsonPath("$.settings[0].defaultThreshold").value(15));
    }

    @Test
    void marksAllAsSeenWith204() throws Exception {
        mockMvc.perform(post("/api/alerts/seen").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNoContent());
        verify(alertService).markAllAsSeen();
    }

    @Test
    void savesTheSettingsWith204() throws Exception {
        putSettings(settingsJsonWith("ABOVE_WEIGHT_LIMIT", "true", "20"))
                .andExpect(status().isNoContent());
        verify(alertService).saveSettings(any());
    }

    @Test
    void refusesANumberOutsideItsTypesRange() throws Exception {
        putSettings(settingsJsonWith("ABOVE_WEIGHT_LIMIT", "true", "150"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("settings[6].thresholdRightForItsType: a type with a number "
                        + "needs one within its range (1–60 whole days, 1–100 percent), and a type without one takes "
                        + "none"));
        verifyNoInteractions(alertService);
    }

    @Test
    void refusesDaysThatArentWholeAndANumberForATypeWithout() throws Exception {
        putSettings(settingsJsonWith("EARNINGS_SOON", "true", "7.5")).andExpect(status().isBadRequest());
        putSettings(settingsJsonWith("CLOSED_BELOW_SMA50", "true", "3")).andExpect(status().isBadRequest());
        verifyNoInteractions(alertService);
    }

    @Test
    void refusesATypeMissing() throws Exception {
        String everyTypeButTheLast = settingLinesOf(Arrays.copyOf(AlertType.values(), AlertType.values().length - 1));

        putSettings("{\"settings\": [" + everyTypeButTheLast + "]}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("everyTypeGivenOnce: every alert type must be given exactly once"));
    }

    /** A plain form, or text, is what a page on another site can send without asking. */
    @Test
    void refusesAFormOrTextWith415() throws Exception {
        mockMvc.perform(post("/api/alerts/seen").contentType(MediaType.APPLICATION_FORM_URLENCODED).content("seen=1"))
                .andExpect(status().isUnsupportedMediaType());
        mockMvc.perform(put("/api/alerts/settings").contentType(MediaType.TEXT_PLAIN)
                        .content(settingsJsonWith("ABOVE_WEIGHT_LIMIT", "true", "20")))
                .andExpect(status().isUnsupportedMediaType());
        verifyNoInteractions(alertService);
    }

    @Test
    void theWritesAcceptOnlyTheirMethod() throws Exception {
        mockMvc.perform(get("/api/alerts/seen")).andExpect(status().isMethodNotAllowed());
        mockMvc.perform(get("/api/alerts/settings")).andExpect(status().isMethodNotAllowed());
    }

    private ResultActions putSettings(String body) throws Exception {
        return mockMvc.perform(put("/api/alerts/settings").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    /** Every type with its default, and one line in place of the default. */
    private String settingsJsonWith(String changedType, String enabled, String threshold) {
        String lines = Arrays.stream(AlertType.values())
                .map(type -> type.name().equals(changedType)
                        ? lineOf(changedType, enabled, threshold)
                        : defaultLineOf(type))
                .collect(Collectors.joining(", "));
        return "{\"settings\": [" + lines + "]}";
    }

    private String settingLinesOf(AlertType[] types) {
        return Arrays.stream(types).map(this::defaultLineOf).collect(Collectors.joining(", "));
    }

    private String defaultLineOf(AlertType type) {
        String threshold = type.hasThreshold() ? type.defaultThreshold().toPlainString() : "null";
        return lineOf(type.name(), "true", threshold);
    }

    private String lineOf(String type, String enabled, String threshold) {
        return "{\"alertType\": \"" + type + "\", \"enabled\": " + enabled + ", \"threshold\": " + threshold + "}";
    }
}
