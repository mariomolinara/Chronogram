package it.unicas.chronogram.notification;

import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.domain.NotificationPreference;
import it.unicas.chronogram.domain.PushSubscription;
import it.unicas.chronogram.notification.dto.NotificationPreferencesRequest;
import it.unicas.chronogram.notification.dto.NotificationPreferencesResponse;
import it.unicas.chronogram.notification.dto.NotificationSettingsResponse;
import it.unicas.chronogram.notification.dto.PushSubscriptionRequest;
import it.unicas.chronogram.notification.dto.PushTestResultResponse;
import it.unicas.chronogram.repository.NotificationPreferenceRepository;
import it.unicas.chronogram.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the self-service side of the reminders: what is returned to a
 * user who has never configured anything, what gets stored for a given request,
 * the {@code lastSentAt} stamp that keeps the first reminder one interval away,
 * the reassignment of an endpoint a browser handed to a second account, and the
 * behaviour of the "send one now" button.
 */
@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    private static final int USER_ID = 42;
    private static final int OTHER_USER_ID = 77;
    private static final String ENDPOINT = "https://fcm.googleapis.com/fcm/send/abc123";
    private static final String P256DH = "BNcRdreALRFXTkOOUHK1EtK2wtaz5Ry4YfYCA";
    private static final String AUTH = "tBHItJI5svbpez7KI4CCXg";
    private static final String PUBLIC_KEY = "BEl62iUYgUivxIkv69yViEuiBIa";

    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private PushSubscriptionRepository subscriptionRepository;
    @Mock private PushSender pushSender;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(
                preferenceRepository, subscriptionRepository, pushSender);
    }

    private static NotificationPreference stored(boolean enabled, LocalDateTime lastSentAt) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(USER_ID);
        preference.setPushEnabled(enabled);
        preference.setIntervalMinutes(120);
        preference.setQuietHoursStart(23);
        preference.setQuietHoursEnd(7);
        preference.setLastSentAt(lastSentAt);
        preference.setCreatedAt(LocalDateTime.of(2026, 1, 1, 10, 0));
        return preference;
    }

    private static PushSubscription subscription(Integer id, Integer userId, String endpoint) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(id);
        subscription.setUserId(userId);
        subscription.setEndpoint(endpoint);
        subscription.setP256dh("old-key");
        subscription.setAuth("old-auth");
        return subscription;
    }

    private static NotificationPreferencesRequest request(boolean enabled,
                                                          int interval,
                                                          Integer quietStart,
                                                          Integer quietEnd) {
        return new NotificationPreferencesRequest(enabled, interval, quietStart, quietEnd);
    }

    private NotificationPreference captureSaved() {
        ArgumentCaptor<NotificationPreference> captured =
                ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceRepository).save(captured.capture());
        return captured.getValue();
    }

    // ---- settings ----

    /**
     * A user who never opened the screen gets the documented defaults, and - the
     * point of the test - no row is written just because a page was read.
     */
    @Test
    void settingsFallBackToTheDefaultsWithoutCreatingARow() {
        when(pushSender.isConfigured()).thenReturn(true);
        when(pushSender.publicKey()).thenReturn(PUBLIC_KEY);
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(subscriptionRepository.countByUserId(USER_ID)).thenReturn(0L);

        NotificationSettingsResponse settings = notificationService.settings(USER_ID);

        assertThat(settings.configured()).isTrue();
        assertThat(settings.vapidPublicKey()).isEqualTo(PUBLIC_KEY);
        assertThat(settings.subscriptionCount()).isZero();
        assertThat(settings.preferences()).isEqualTo(NotificationPreferencesResponse.defaults());
        assertThat(settings.preferences().pushEnabled()).isFalse();
        assertThat(settings.preferences().intervalMinutes()).isEqualTo(60);
        assertThat(settings.preferences().quietHoursStart()).isEqualTo(22);
        assertThat(settings.preferences().quietHoursEnd()).isEqualTo(8);

        verify(preferenceRepository, never()).save(any());
    }

    @Test
    void settingsReturnTheStoredRowWhenThereIsOne() {
        when(pushSender.isConfigured()).thenReturn(true);
        when(pushSender.publicKey()).thenReturn(PUBLIC_KEY);
        when(preferenceRepository.findById(USER_ID))
                .thenReturn(Optional.of(stored(true, LocalDateTime.of(2026, 3, 1, 9, 0))));
        when(subscriptionRepository.countByUserId(USER_ID)).thenReturn(2L);

        NotificationSettingsResponse settings = notificationService.settings(USER_ID);

        assertThat(settings.preferences().pushEnabled()).isTrue();
        assertThat(settings.preferences().intervalMinutes()).isEqualTo(120);
        assertThat(settings.preferences().quietHoursStart()).isEqualTo(23);
        assertThat(settings.preferences().quietHoursEnd()).isEqualTo(7);
        assertThat(settings.subscriptionCount()).isEqualTo(2L);
    }

    /** An unconfigured server hands out no key at all, not a stale one. */
    @Test
    void settingsReportAnUnconfiguredServer() {
        when(pushSender.isConfigured()).thenReturn(false);
        when(pushSender.publicKey()).thenReturn(null);
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());
        when(subscriptionRepository.countByUserId(USER_ID)).thenReturn(0L);

        NotificationSettingsResponse settings = notificationService.settings(USER_ID);

        assertThat(settings.configured()).isFalse();
        assertThat(settings.vapidPublicKey()).isNull();
    }

    // ---- savePreferences ----

    @Test
    void theFirstSaveCreatesTheRowWithTheUserAsItsKey() {
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());

        NotificationPreferencesResponse response =
                notificationService.savePreferences(USER_ID, request(false, 45, 1, 6));

        NotificationPreference saved = captureSaved();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.isPushEnabled()).isFalse();
        assertThat(saved.getIntervalMinutes()).isEqualTo(45);
        assertThat(saved.getQuietHoursStart()).isEqualTo(1);
        assertThat(saved.getQuietHoursEnd()).isEqualTo(6);
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        // Still switched off, so there is nothing to postpone.
        assertThat(saved.getLastSentAt()).isNull();

        assertThat(response).isEqualTo(new NotificationPreferencesResponse(false, 45, 1, 6));
    }

    /**
     * The behaviour the whole feature hinges on: turning the reminders on must not
     * produce a notification within the minute. Stamping {@code lastSentAt} is what
     * pushes the first one a full interval away.
     */
    @Test
    void switchingTheRemindersOnStampsLastSentAtSoTheFirstOneIsAnIntervalAway() {
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.of(stored(false, null)));
        LocalDateTime before = LocalDateTime.now(NotificationPreference.ZONE).minusSeconds(1);

        notificationService.savePreferences(USER_ID, request(true, 60, 22, 8));

        NotificationPreference saved = captureSaved();
        assertThat(saved.isPushEnabled()).isTrue();
        assertThat(saved.getLastSentAt()).isNotNull();
        assertThat(saved.getLastSentAt()).isAfterOrEqualTo(before);
        // ... and the direct consequence: not due until the interval has elapsed.
        assertThat(saved.isDueAt(saved.getLastSentAt().plusMinutes(59))).isFalse();
        assertThat(saved.isDueAt(saved.getLastSentAt().plusMinutes(60))).isTrue();
    }

    /** A brand-new row created already enabled is the same transition. */
    @Test
    void creatingTheRowAlreadyEnabledAlsoStampsLastSentAt() {
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.empty());

        notificationService.savePreferences(USER_ID, request(true, 30, null, null));

        assertThat(captureSaved().getLastSentAt()).isNotNull();
    }

    /**
     * Editing the interval while the reminders are already on must not restart the
     * countdown, or a user fiddling with the slider would never receive anything.
     */
    @Test
    void anEditWithTheRemindersAlreadyOnLeavesLastSentAtAlone() {
        LocalDateTime lastSent = LocalDateTime.of(2026, 3, 1, 9, 0);
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.of(stored(true, lastSent)));

        notificationService.savePreferences(USER_ID, request(true, 240, 22, 8));

        NotificationPreference saved = captureSaved();
        assertThat(saved.getLastSentAt()).isEqualTo(lastSent);
        assertThat(saved.getIntervalMinutes()).isEqualTo(240);
    }

    @Test
    void switchingTheRemindersOffLeavesLastSentAtAlone() {
        LocalDateTime lastSent = LocalDateTime.of(2026, 3, 1, 9, 0);
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.of(stored(true, lastSent)));

        notificationService.savePreferences(USER_ID, request(false, 60, 22, 8));

        NotificationPreference saved = captureSaved();
        assertThat(saved.isPushEnabled()).isFalse();
        assertThat(saved.getLastSentAt()).isEqualTo(lastSent);
    }

    @Test
    void clearingTheQuietWindowStoresTwoNulls() {
        when(preferenceRepository.findById(USER_ID)).thenReturn(Optional.of(stored(true, null)));

        NotificationPreferencesResponse response =
                notificationService.savePreferences(USER_ID, request(true, 60, null, null));

        NotificationPreference saved = captureSaved();
        assertThat(saved.getQuietHoursStart()).isNull();
        assertThat(saved.getQuietHoursEnd()).isNull();
        assertThat(response.quietHoursStart()).isNull();
        assertThat(response.quietHoursEnd()).isNull();
    }

    // ---- saveSubscription ----

    @Test
    void anUnknownEndpointIsInsertedForTheCaller() {
        when(subscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());
        when(subscriptionRepository.countByUserId(USER_ID)).thenReturn(1L);

        long count = notificationService.saveSubscription(USER_ID,
                new PushSubscriptionRequest(ENDPOINT, P256DH, AUTH, "Firefox"));

        ArgumentCaptor<PushSubscription> captured = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(captured.capture());
        PushSubscription saved = captured.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getEndpoint()).isEqualTo(ENDPOINT);
        assertThat(saved.getP256dh()).isEqualTo(P256DH);
        assertThat(saved.getAuth()).isEqualTo(AUTH);
        assertThat(saved.getUserAgent()).isEqualTo("Firefox");
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(count).isEqualTo(1L);
    }

    /**
     * The case the unique index would otherwise turn into a 500: a browser handed
     * the same endpoint to a second account on the same machine. The row is
     * reassigned - leaving it with the previous owner would deliver one user's
     * reminders onto the other's screen.
     */
    @Test
    void anEndpointAlreadyRegisteredToAnotherUserIsReassignedNotDuplicated() {
        PushSubscription existing = subscription(7, OTHER_USER_ID, ENDPOINT);
        when(subscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.countByUserId(USER_ID)).thenReturn(1L);

        notificationService.saveSubscription(USER_ID,
                new PushSubscriptionRequest(ENDPOINT, P256DH, AUTH, "Chrome"));

        ArgumentCaptor<PushSubscription> captured = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(captured.capture());
        PushSubscription saved = captured.getValue();
        // Same row, new owner and fresh keys.
        assertThat(saved.getId()).isEqualTo(7);
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getP256dh()).isEqualTo(P256DH);
        assertThat(saved.getAuth()).isEqualTo(AUTH);
    }

    /** Re-subscribing the same browser refreshes the keys it rotated. */
    @Test
    void resubscribingTheSameBrowserUpdatesItsKeysInPlace() {
        PushSubscription existing = subscription(9, USER_ID, ENDPOINT);
        when(subscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(existing));
        when(subscriptionRepository.countByUserId(USER_ID)).thenReturn(1L);

        notificationService.saveSubscription(USER_ID,
                new PushSubscriptionRequest(ENDPOINT, "new-key", "new-auth", null));

        ArgumentCaptor<PushSubscription> captured = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(captured.capture());
        assertThat(captured.getValue().getId()).isEqualTo(9);
        assertThat(captured.getValue().getP256dh()).isEqualTo("new-key");
        assertThat(captured.getValue().getUserAgent()).isNull();
    }

    /** Surrounding whitespace must not create a second row for the same device. */
    @Test
    void theEndpointIsTrimmedBeforeItIsLookedUp() {
        when(subscriptionRepository.findByEndpoint(ENDPOINT))
                .thenReturn(Optional.of(subscription(3, USER_ID, ENDPOINT)));
        when(subscriptionRepository.countByUserId(USER_ID)).thenReturn(1L);

        notificationService.saveSubscription(USER_ID,
                new PushSubscriptionRequest("  " + ENDPOINT + "  ", P256DH, AUTH, "   "));

        verify(subscriptionRepository).findByEndpoint(ENDPOINT);
        ArgumentCaptor<PushSubscription> captured = ArgumentCaptor.forClass(PushSubscription.class);
        verify(subscriptionRepository).save(captured.capture());
        // A blank user agent is stored as absent rather than as an empty string.
        assertThat(captured.getValue().getUserAgent()).isNull();
    }

    // ---- deleteSubscription ----

    @Test
    void theCallersOwnSubscriptionIsDeleted() {
        PushSubscription own = subscription(4, USER_ID, ENDPOINT);
        when(subscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.of(own));

        notificationService.deleteSubscription(USER_ID, ENDPOINT);

        verify(subscriptionRepository).delete(own);
    }

    /** Idempotent: the client calls this exactly when it cannot be sure. */
    @Test
    void deletingAnUnknownEndpointIsANoOp() {
        when(subscriptionRepository.findByEndpoint(ENDPOINT)).thenReturn(Optional.empty());

        notificationService.deleteSubscription(USER_ID, ENDPOINT);

        verify(subscriptionRepository, never()).delete(any());
    }

    /** Otherwise the endpoint would be a way of unsubscribing somebody else. */
    @Test
    void anotherUsersSubscriptionIsNotDeleted() {
        when(subscriptionRepository.findByEndpoint(ENDPOINT))
                .thenReturn(Optional.of(subscription(5, OTHER_USER_ID, ENDPOINT)));

        notificationService.deleteSubscription(USER_ID, ENDPOINT);

        verify(subscriptionRepository, never()).delete(any());
    }

    // ---- sendTest ----

    @Test
    void theTestRefusesToRunOnAServerWithoutVapidKeys() {
        when(pushSender.isConfigured()).thenReturn(false);

        assertThatThrownBy(() -> notificationService.sendTest(USER_ID))
                .isInstanceOf(FeatureUnavailableException.class)
                .hasMessageContaining("not available");

        verifyNoInteractions(subscriptionRepository);
        verify(pushSender, never()).send(any(), anyString(), anyString());
    }

    @Test
    void theTestWithNoRegisteredDeviceReportsZeroAndSendsNothing() {
        when(pushSender.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of());

        PushTestResultResponse result = notificationService.sendTest(USER_ID);

        assertThat(result).isEqualTo(new PushTestResultResponse(0, 0));
        verify(pushSender, never()).send(any(), anyString(), anyString());
    }

    /**
     * A partial result is reported as a partial result, and the device the push
     * service declared gone is dropped on the way out - the test doubles as the
     * cheapest opportunity to prune a stale row.
     */
    @Test
    void theTestCountsEachDeviceAndDropsTheExpiredOnes() {
        PushSubscription ok = subscription(1, USER_ID, ENDPOINT + "/1");
        PushSubscription gone = subscription(2, USER_ID, ENDPOINT + "/2");
        PushSubscription broken = subscription(3, USER_ID, ENDPOINT + "/3");
        when(pushSender.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(ok, gone, broken));
        when(pushSender.send(ok, "Chronogram", "Push notifications are working."))
                .thenReturn(PushSender.Result.DELIVERED);
        when(pushSender.send(gone, "Chronogram", "Push notifications are working."))
                .thenReturn(PushSender.Result.EXPIRED);
        when(pushSender.send(broken, "Chronogram", "Push notifications are working."))
                .thenReturn(PushSender.Result.FAILED);

        PushTestResultResponse result = notificationService.sendTest(USER_ID);

        assertThat(result).isEqualTo(new PushTestResultResponse(1, 2));
        verify(subscriptionRepository).deleteAll(List.of(gone));
    }

    /** Nothing to prune: no pointless delete statement either. */
    @Test
    void theTestDeletesNothingWhenEveryDeviceIsReachable() {
        PushSubscription ok = subscription(1, USER_ID, ENDPOINT);
        when(pushSender.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(ok));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        assertThat(notificationService.sendTest(USER_ID)).isEqualTo(new PushTestResultResponse(1, 0));

        verify(subscriptionRepository, never()).deleteAll(any());
    }

    /** A test must never postpone the next real reminder. */
    @Test
    void theTestNeverTouchesThePreferencesRow() {
        when(pushSender.isConfigured()).thenReturn(true);
        when(subscriptionRepository.findByUserId(USER_ID))
                .thenReturn(List.of(subscription(1, USER_ID, ENDPOINT)));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        notificationService.sendTest(USER_ID);

        verifyNoInteractions(preferenceRepository);
    }
}
