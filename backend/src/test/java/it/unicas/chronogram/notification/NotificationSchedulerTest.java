package it.unicas.chronogram.notification;

import it.unicas.chronogram.domain.NotificationPreference;
import it.unicas.chronogram.domain.PushSubscription;
import it.unicas.chronogram.repository.NotificationPreferenceRepository;
import it.unicas.chronogram.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the periodic pass: who is due, the silent window (wrap-around
 * included), the pruning of subscriptions the push service declared gone, and the
 * containment of one user's failure so it does not cost the rest of the pass.
 *
 * <p>"Now" is handed to {@link NotificationScheduler#runAt} explicitly, so the
 * window boundaries are exercised at 22:00 and 08:00 rather than whenever the
 * build happens to run.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationSchedulerTest {

    private static final int USER_ID = 42;
    private static final String TITLE = "Chronogram";
    private static final String BODY = "Time to log your activities!";

    @Mock private NotificationPreferenceRepository preferenceRepository;
    @Mock private PushSubscriptionRepository subscriptionRepository;
    @Mock private PushSender pushSender;

    private NotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new NotificationScheduler(preferenceRepository, subscriptionRepository, pushSender);
    }

    /** A user who wants an hourly reminder inside the default 22-to-8 silence. */
    private static NotificationPreference preference(LocalDateTime lastSentAt,
                                                     Integer quietStart,
                                                     Integer quietEnd) {
        NotificationPreference preference = new NotificationPreference();
        preference.setUserId(USER_ID);
        preference.setPushEnabled(true);
        preference.setIntervalMinutes(60);
        preference.setQuietHoursStart(quietStart);
        preference.setQuietHoursEnd(quietEnd);
        preference.setLastSentAt(lastSentAt);
        return preference;
    }

    private static PushSubscription subscription(int id) {
        PushSubscription subscription = new PushSubscription();
        subscription.setId(id);
        subscription.setUserId(USER_ID);
        subscription.setEndpoint("https://fcm.googleapis.com/fcm/send/device-" + id);
        subscription.setP256dh("key-" + id);
        subscription.setAuth("auth-" + id);
        return subscription;
    }

    private static LocalDateTime at(int hour) {
        return LocalDateTime.of(2026, 3, 10, hour, 30);
    }

    /** Due by construction: the last reminder is older than the interval. */
    private static LocalDateTime dueSince(LocalDateTime now) {
        return now.minusHours(3);
    }

    private void configured() {
        when(pushSender.isConfigured()).thenReturn(true);
    }

    // ---- the feature switch ----

    /**
     * An installation with no VAPID keys must not even query the database: the pass
     * runs every minute for the life of the process.
     */
    @Test
    void anUnconfiguredServerDoesNothingAtAll() {
        when(pushSender.isConfigured()).thenReturn(false);

        scheduler.runAt(at(10));

        verifyNoInteractions(preferenceRepository, subscriptionRepository);
        verify(pushSender, never()).send(any(), anyString(), anyString());
    }

    // ---- who is due ----

    @Test
    void aDueUserOutsideTheQuietWindowIsRemindedAndStamped() {
        configured();
        LocalDateTime now = at(10);
        NotificationPreference preference = preference(dueSince(now), 22, 8);
        PushSubscription phone = subscription(1);
        when(preferenceRepository.findByPushEnabledTrue()).thenReturn(List.of(preference));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(phone));
        when(pushSender.send(phone, TITLE, BODY)).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(phone, TITLE, BODY);
        ArgumentCaptor<NotificationPreference> saved =
                ArgumentCaptor.forClass(NotificationPreference.class);
        verify(preferenceRepository).save(saved.capture());
        assertThat(saved.getValue().getLastSentAt()).isEqualTo(now);
    }

    /** Every device of the user, from one decision. */
    @Test
    void everyDeviceOfTheUserGetsTheSameReminder() {
        configured();
        LocalDateTime now = at(10);
        PushSubscription phone = subscription(1);
        PushSubscription laptop = subscription(2);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(phone, laptop));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(phone, TITLE, BODY);
        verify(pushSender).send(laptop, TITLE, BODY);
    }

    /** Half an interval in: nothing to send, and nothing to write either. */
    @Test
    void aUserWhoseIntervalHasNotElapsedIsLeftAlone() {
        configured();
        LocalDateTime now = at(10);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(now.minusMinutes(30), 22, 8)));

        scheduler.runAt(now);

        verify(pushSender, never()).send(any(), anyString(), anyString());
        verify(preferenceRepository, never()).save(any());
    }

    /** Exactly one interval is enough: the comparison is inclusive. */
    @Test
    void aUserWhoseIntervalHasJustElapsedIsDue() {
        configured();
        LocalDateTime now = at(10);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(now.minusMinutes(60), 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscription(1)));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(any(), anyString(), anyString());
    }

    /**
     * A row that was never stamped is due at once. In practice the API stamps it
     * when the reminders are switched on, so this is the fallback for a row written
     * some other way - it must not be silently skipped forever.
     */
    @Test
    void aUserNeverRemindedBeforeIsDue() {
        configured();
        LocalDateTime now = at(10);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(null, 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscription(1)));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(any(), anyString(), anyString());
    }

    // ---- the silent window ----

    /**
     * The default window wraps around midnight, so it has to cover both the hours
     * before it and the hours after it. 22 is inside, 8 is not: the window is
     * half-open, which is what makes 08:00 the moment the withheld reminder lands.
     */
    @ParameterizedTest
    @ValueSource(ints = {22, 23, 0, 3, 7})
    void insideTheWrappingWindowNothingIsSentAndNothingIsStamped(int hour) {
        configured();
        LocalDateTime now = at(hour);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 22, 8)));

        scheduler.runAt(now);

        verify(pushSender, never()).send(any(), anyString(), anyString());
        // The crucial half: lastSentAt is NOT advanced, so the user stays due and is
        // reminded the moment the window closes rather than an interval later.
        verify(preferenceRepository, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 9, 14, 21})
    void outsideTheWrappingWindowTheReminderGoesOut(int hour) {
        configured();
        LocalDateTime now = at(hour);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscription(1)));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(any(), anyString(), anyString());
    }

    /** A window that does not wrap - a siesta, say - behaves the plain way. */
    @ParameterizedTest
    @ValueSource(ints = {13, 14, 15})
    void aWindowInsideTheSameDayIsHonoured(int hour) {
        configured();
        LocalDateTime now = at(hour);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 13, 16)));

        scheduler.runAt(now);

        verify(pushSender, never()).send(any(), anyString(), anyString());
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 16, 17})
    void theHoursAroundASameDayWindowStillGetReminders(int hour) {
        configured();
        LocalDateTime now = at(hour);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 13, 16)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscription(1)));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(any(), anyString(), anyString());
    }

    /** No window configured: a reminder at three in the morning is what was asked for. */
    @Test
    void withoutAQuietWindowEvenTheSmallHoursGetAReminder() {
        configured();
        LocalDateTime now = at(3);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), null, null)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscription(1)));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(any(), anyString(), anyString());
    }

    /**
     * A zero-length window is read as no window rather than as a permanent
     * silence, which would switch the feature off by accident.
     */
    @Test
    void aWindowWhoseEndsCoincideSilencesNothing() {
        configured();
        LocalDateTime now = at(9);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 9, 9)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(subscription(1)));
        when(pushSender.send(any(), anyString(), anyString())).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(any(), anyString(), anyString());
    }

    // ---- stale subscriptions ----

    /**
     * 410 Gone is the push service saying the browser threw the subscription away.
     * Keeping the row would mean retrying it every interval for the life of the
     * account, so it is deleted.
     */
    @Test
    void aSubscriptionThePushServiceDeclaredGoneIsDeleted() {
        configured();
        LocalDateTime now = at(10);
        PushSubscription gone = subscription(1);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(gone));
        when(pushSender.send(gone, TITLE, BODY)).thenReturn(PushSender.Result.EXPIRED);

        scheduler.runAt(now);

        verify(subscriptionRepository).deleteAll(List.of(gone));
        // The attempt still counts: the user is not due again until the next interval.
        verify(preferenceRepository).save(any());
    }

    /** A transient failure costs the round, not the subscription. */
    @Test
    void aTransientFailureDoesNotDeleteTheSubscription() {
        configured();
        LocalDateTime now = at(10);
        PushSubscription unreachable = subscription(1);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(unreachable));
        when(pushSender.send(unreachable, TITLE, BODY)).thenReturn(PushSender.Result.FAILED);

        scheduler.runAt(now);

        verify(subscriptionRepository, never()).deleteAll(any());
        // Still stamped: retrying every minute would hammer a struggling push service.
        verify(preferenceRepository).save(any());
    }

    /** Only the dead ones go. */
    @Test
    void onlyTheExpiredDeviceOfAUserIsDeleted() {
        configured();
        LocalDateTime now = at(10);
        PushSubscription alive = subscription(1);
        PushSubscription gone = subscription(2);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of(alive, gone));
        when(pushSender.send(alive, TITLE, BODY)).thenReturn(PushSender.Result.DELIVERED);
        when(pushSender.send(gone, TITLE, BODY)).thenReturn(PushSender.Result.EXPIRED);

        scheduler.runAt(now);

        verify(subscriptionRepository).deleteAll(List.of(gone));
    }

    /** A user who revoked every device still stops being due. */
    @Test
    void aDueUserWithNoDeviceLeftIsStampedAnyway() {
        configured();
        LocalDateTime now = at(10);
        when(preferenceRepository.findByPushEnabledTrue())
                .thenReturn(List.of(preference(dueSince(now), 22, 8)));
        when(subscriptionRepository.findByUserId(USER_ID)).thenReturn(List.of());

        scheduler.runAt(now);

        verify(pushSender, never()).send(any(), anyString(), anyString());
        verify(preferenceRepository).save(any());
    }

    // ---- containment ----

    /**
     * One user's failure must not cost the rest of the pass their reminder, which is
     * exactly what would happen if the exception escaped the loop.
     */
    @Test
    void aFailureOnOneUserDoesNotStopTheOthers() {
        configured();
        LocalDateTime now = at(10);
        NotificationPreference broken = preference(dueSince(now), 22, 8);
        broken.setUserId(1);
        NotificationPreference healthy = preference(dueSince(now), 22, 8);
        healthy.setUserId(2);
        PushSubscription device = subscription(1);
        when(preferenceRepository.findByPushEnabledTrue()).thenReturn(List.of(broken, healthy));
        when(subscriptionRepository.findByUserId(1)).thenThrow(new IllegalStateException("boom"));
        when(subscriptionRepository.findByUserId(2)).thenReturn(List.of(device));
        when(pushSender.send(device, TITLE, BODY)).thenReturn(PushSender.Result.DELIVERED);

        scheduler.runAt(now);

        verify(pushSender).send(device, TITLE, BODY);
    }

    /** A database that is down costs the pass, not the schedule. */
    @Test
    void aFailingQuerySkipsThePassWithoutThrowing() {
        configured();
        when(preferenceRepository.findByPushEnabledTrue())
                .thenThrow(new IllegalStateException("connection refused"));

        scheduler.runAt(at(10));

        verify(pushSender, never()).send(any(), anyString(), anyString());
    }

    /** Nobody opted in: one query and out. */
    @Test
    void noCandidatesMeansNothingHappens() {
        configured();
        when(preferenceRepository.findByPushEnabledTrue()).thenReturn(List.of());

        scheduler.runAt(at(10));

        verify(pushSender, never()).send(any(), anyString(), anyString());
        verify(preferenceRepository, never()).save(any());
    }
}
