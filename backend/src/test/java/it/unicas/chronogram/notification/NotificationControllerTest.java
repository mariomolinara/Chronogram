package it.unicas.chronogram.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.GlobalExceptionHandler;
import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.domain.Role;
import it.unicas.chronogram.notification.dto.NotificationPreferencesRequest;
import it.unicas.chronogram.notification.dto.NotificationPreferencesResponse;
import it.unicas.chronogram.notification.dto.NotificationSettingsResponse;
import it.unicas.chronogram.notification.dto.PushSubscriptionRequest;
import it.unicas.chronogram.notification.dto.PushTestResultResponse;
import it.unicas.chronogram.repository.UserAuthRepository;
import it.unicas.chronogram.security.AuthPrincipal;
import it.unicas.chronogram.security.JwtService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for the push-notification endpoints: the JSON contract the client
 * binds to, the bounds that must be refused before anything is stored, and the
 * fact that the subject always comes from the authenticated principal.
 */
@WebMvcTest(controllers = NotificationController.class)
@Import(GlobalExceptionHandler.class)
class NotificationControllerTest {

    private static final String PUBLIC_KEY = "BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkTtSL1s0xBdC";
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private NotificationService notificationService;
    // Required by the auto-registered JwtAuthenticationFilter (a @Component).
    @MockBean private JwtService jwtService;
    @MockBean private UserAuthRepository userAuthRepository;

    private Authentication principal() {
        AuthPrincipal p = new AuthPrincipal(55, "ada@unicas.it", Role.USER);
        return new UsernamePasswordAuthenticationToken(p, null, List.of());
    }

    private String json(Object body) throws Exception {
        return objectMapper.writeValueAsString(body);
    }

    private static Map<String, Object> validPreferences() {
        Map<String, Object> body = new HashMap<>();
        body.put("pushEnabled", true);
        body.put("intervalMinutes", 90);
        body.put("quietHoursStart", 22);
        body.put("quietHoursEnd", 8);
        return body;
    }

    private static Map<String, Object> validSubscription() {
        Map<String, Object> body = new HashMap<>();
        body.put("endpoint", ENDPOINT);
        body.put("p256dh", "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA_0QTpQtUbVlUls0VJXg7A8u-Ts1XbjhazAkj7I99e8QcYP7DkM");
        body.put("auth", "tBHItJI5svbpez7KI4CCXg");
        body.put("userAgent", "Mozilla/5.0");
        return body;
    }

    // ---- GET /settings ----

    @Test
    void settingsReturnsTheWholeScreenPayloadForThePrincipal() throws Exception {
        when(notificationService.settings(55)).thenReturn(new NotificationSettingsResponse(
                true, PUBLIC_KEY,
                new NotificationPreferencesResponse(true, 90, 22, 8),
                2L));

        mockMvc.perform(get("/api/notifications/settings").with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.configured").value(true))
                .andExpect(jsonPath("$.data.vapidPublicKey").value(PUBLIC_KEY))
                .andExpect(jsonPath("$.data.preferences.pushEnabled").value(true))
                .andExpect(jsonPath("$.data.preferences.intervalMinutes").value(90))
                .andExpect(jsonPath("$.data.preferences.quietHoursStart").value(22))
                .andExpect(jsonPath("$.data.preferences.quietHoursEnd").value(8))
                .andExpect(jsonPath("$.data.subscriptionCount").value(2));

        verify(notificationService).settings(55);
    }

    /**
     * A server with no VAPID keys still answers: {@code configured: false} and a
     * null key, so the client hides the switch instead of failing on a 5xx.
     */
    @Test
    void settingsReportsAnUnconfiguredServerWithANullKey() throws Exception {
        when(notificationService.settings(55)).thenReturn(new NotificationSettingsResponse(
                false, null, NotificationPreferencesResponse.defaults(), 0L));

        mockMvc.perform(get("/api/notifications/settings").with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configured").value(false))
                .andExpect(jsonPath("$.data.vapidPublicKey").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.preferences.pushEnabled").value(false))
                .andExpect(jsonPath("$.data.preferences.intervalMinutes").value(60))
                .andExpect(jsonPath("$.data.preferences.quietHoursStart").value(22))
                .andExpect(jsonPath("$.data.preferences.quietHoursEnd").value(8))
                .andExpect(jsonPath("$.data.subscriptionCount").value(0));
    }

    /**
     * The quiet-hours fields are {@code number|null} in the contract: the keys have
     * to be present carrying null, not dropped, so the form can bind them.
     */
    @Test
    void absentQuietHoursAreSerialisedAsExplicitNulls() throws Exception {
        when(notificationService.settings(55)).thenReturn(new NotificationSettingsResponse(
                true, PUBLIC_KEY,
                new NotificationPreferencesResponse(true, 60, null, null),
                1L));

        mockMvc.perform(get("/api/notifications/settings").with(authentication(principal())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferences.quietHoursStart")
                        .value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.preferences.quietHoursEnd")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void settingsRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/notifications/settings"))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).settings(any());
    }

    // ---- POST /preferences ----

    @Test
    void savingPreferencesUsesThePrincipalIdAndEchoesTheStoredValues() throws Exception {
        when(notificationService.savePreferences(eq(55), any()))
                .thenReturn(new NotificationPreferencesResponse(true, 90, 22, 8));

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validPreferences())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.pushEnabled").value(true))
                .andExpect(jsonPath("$.data.intervalMinutes").value(90));

        ArgumentCaptor<NotificationPreferencesRequest> captured =
                ArgumentCaptor.forClass(NotificationPreferencesRequest.class);
        verify(notificationService).savePreferences(eq(55), captured.capture());
        assertThat(captured.getValue().intervalMinutes()).isEqualTo(90);
        assertThat(captured.getValue().quietHoursStart()).isEqualTo(22);
    }

    @Test
    void anIntervalBelowTheMinimumIsRejectedWith400() throws Exception {
        Map<String, Object> body = validPreferences();
        body.put("intervalMinutes", 14);

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("at least 15 minutes")));

        verify(notificationService, never()).savePreferences(any(), any());
    }

    @Test
    void anIntervalAboveADayIsRejectedWith400() throws Exception {
        Map<String, Object> body = validPreferences();
        body.put("intervalMinutes", 1441);

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("at most 1440 minutes")));

        verify(notificationService, never()).savePreferences(any(), any());
    }

    @Test
    void theIntervalBoundsThemselvesAreAccepted() throws Exception {
        when(notificationService.savePreferences(eq(55), any()))
                .thenReturn(new NotificationPreferencesResponse(true, 15, 22, 8));

        for (int interval : new int[]{15, 1440}) {
            Map<String, Object> body = validPreferences();
            body.put("intervalMinutes", interval);

            mockMvc.perform(post("/api/notifications/preferences")
                            .with(authentication(principal())).with(csrf())
                            .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                    .andExpect(status().isOk());
        }
    }

    @Test
    void anHourOutsideTheDayIsRejectedWith400() throws Exception {
        Map<String, Object> body = validPreferences();
        body.put("quietHoursStart", 24);

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("between 0 and 23")));

        verify(notificationService, never()).savePreferences(any(), any());
    }

    @Test
    void aNegativeHourIsRejectedWith400() throws Exception {
        Map<String, Object> body = validPreferences();
        body.put("quietHoursEnd", -1);

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).savePreferences(any(), any());
    }

    /** Half a silent window describes nothing the scheduler could act on. */
    @Test
    void oneHalfOfTheQuietWindowOnItsOwnIsRejectedWith400() throws Exception {
        Map<String, Object> body = validPreferences();
        body.put("quietHoursEnd", null);

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("both a start and an end")));

        verify(notificationService, never()).savePreferences(any(), any());
    }

    /** Both halves absent is the legitimate way of asking for no silent window. */
    @Test
    void bothHalvesAbsentMeansNoQuietWindowAndIsAccepted() throws Exception {
        when(notificationService.savePreferences(eq(55), any()))
                .thenReturn(new NotificationPreferencesResponse(true, 90, null, null));
        Map<String, Object> body = validPreferences();
        body.put("quietHoursStart", null);
        body.put("quietHoursEnd", null);

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.quietHoursStart")
                        .value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void aMissingPushEnabledFlagIsRejectedWith400() throws Exception {
        Map<String, Object> body = validPreferences();
        body.remove("pushEnabled");

        mockMvc.perform(post("/api/notifications/preferences")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("pushEnabled is required")));
    }

    @Test
    void savingPreferencesRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/notifications/preferences").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validPreferences())))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).savePreferences(any(), any());
    }

    // ---- POST /subscriptions ----

    @Test
    void aSubscriptionIsStoredForThePrincipalAndTheCountComesBack() throws Exception {
        when(notificationService.saveSubscription(eq(55), any())).thenReturn(3L);

        mockMvc.perform(post("/api/notifications/subscriptions")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validSubscription())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.subscriptionCount").value(3));

        ArgumentCaptor<PushSubscriptionRequest> captured =
                ArgumentCaptor.forClass(PushSubscriptionRequest.class);
        verify(notificationService).saveSubscription(eq(55), captured.capture());
        assertThat(captured.getValue().endpoint()).isEqualTo(ENDPOINT);
        assertThat(captured.getValue().userAgent()).isEqualTo("Mozilla/5.0");
    }

    @Test
    void aSubscriptionWithoutAnEndpointIsRejectedWith400() throws Exception {
        Map<String, Object> body = validSubscription();
        body.remove("endpoint");

        mockMvc.perform(post("/api/notifications/subscriptions")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("endpoint is required")));

        verify(notificationService, never()).saveSubscription(any(), any());
    }

    @Test
    void anOverlongEndpointIsRejectedWith400() throws Exception {
        Map<String, Object> body = validSubscription();
        body.put("endpoint", "https://push.example.com/" + "x".repeat(500));

        mockMvc.perform(post("/api/notifications/subscriptions")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("at most 500 characters")));

        verify(notificationService, never()).saveSubscription(any(), any());
    }

    @Test
    void aSubscriptionWithoutItsKeysIsRejectedWith400() throws Exception {
        Map<String, Object> body = validSubscription();
        body.remove("p256dh");

        mockMvc.perform(post("/api/notifications/subscriptions")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).saveSubscription(any(), any());
    }

    /** The user agent is informative only and may legitimately be absent. */
    @Test
    void aSubscriptionWithoutAUserAgentIsAccepted() throws Exception {
        when(notificationService.saveSubscription(eq(55), any())).thenReturn(1L);
        Map<String, Object> body = validSubscription();
        body.put("userAgent", null);

        mockMvc.perform(post("/api/notifications/subscriptions")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscriptionCount").value(1));
    }

    @Test
    void subscribingRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/notifications/subscriptions").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json(validSubscription())))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).saveSubscription(any(), any());
    }

    // ---- POST /subscriptions/delete ----

    @Test
    void deletingASubscriptionForwardsTheEndpointAndThePrincipalId() throws Exception {
        mockMvc.perform(post("/api/notifications/subscriptions/delete")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("endpoint", ENDPOINT))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(notificationService).deleteSubscription(55, ENDPOINT);
    }

    @Test
    void deletingWithoutAnEndpointIsRejectedWith400() throws Exception {
        mockMvc.perform(post("/api/notifications/subscriptions/delete")
                        .with(authentication(principal())).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        verify(notificationService, never()).deleteSubscription(any(), any());
    }

    @Test
    void deletingASubscriptionRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/notifications/subscriptions/delete").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("endpoint", ENDPOINT))))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).deleteSubscription(any(), any());
    }

    // ---- POST /test ----

    @Test
    void theTestEndpointReportsWhatWasSentAndWhatFailed() throws Exception {
        when(notificationService.sendTest(55)).thenReturn(new PushTestResultResponse(2, 1));

        mockMvc.perform(post("/api/notifications/test")
                        .with(authentication(principal())).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sent").value(2))
                .andExpect(jsonPath("$.data.failed").value(1));

        verify(notificationService).sendTest(55);
    }

    /** No VAPID keys on this installation: 503, same as the support form. */
    @Test
    void theTestEndpointReportsAnUnconfiguredServerAs503() throws Exception {
        doThrow(new FeatureUnavailableException("Push notifications are not available on this server."))
                .when(notificationService).sendTest(55);

        mockMvc.perform(post("/api/notifications/test")
                        .with(authentication(principal())).with(csrf()))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("not available")));
    }

    @Test
    void theTestEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/notifications/test").with(csrf()))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).sendTest(any());
    }
}
