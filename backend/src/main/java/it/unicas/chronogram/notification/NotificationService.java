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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Self-service management of the periodic push reminders: the user's settings and
 * the browsers they want to be reminded on.
 *
 * <p>Like {@code ProfileService}, every method takes the user id the caller
 * resolved from the JWT and uses it as the sole key, so no request parameter can
 * point one account at another's subscriptions.
 *
 * <p>The actual sending lives in {@link PushSender}; what is here is the decision
 * of <em>whether</em> and <em>where</em> to send. The one method that does send -
 * {@link #sendTest} - is deliberately not transactional: it performs network
 * calls whose duration must not be spent holding a database connection.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** What the "send me one now" button delivers. */
    private static final String TEST_TITLE = "Chronogram";
    private static final String TEST_BODY = "Push notifications are working.";

    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSender pushSender;

    public NotificationService(NotificationPreferenceRepository preferenceRepository,
                              PushSubscriptionRepository subscriptionRepository,
                              PushSender pushSender) {
        this.preferenceRepository = preferenceRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.pushSender = pushSender;
    }

    /**
     * Everything the notification screen needs in one call.
     *
     * <p>A user who never saved anything gets the defaults and <em>no row is
     * created</em>: reading a screen must not write to the database, and a row
     * full of defaults would be indistinguishable from a deliberate choice if the
     * defaults ever changed.
     */
    @Transactional(readOnly = true)
    public NotificationSettingsResponse settings(Integer userId) {
        NotificationPreferencesResponse preferences = preferenceRepository.findById(userId)
                .map(NotificationPreferencesResponse::from)
                .orElseGet(NotificationPreferencesResponse::defaults);

        return new NotificationSettingsResponse(
                pushSender.isConfigured(),
                pushSender.publicKey(),
                preferences,
                subscriptionRepository.countByUserId(userId));
    }

    /**
     * Stores the user's settings, creating the row the first time.
     *
     * <p>Switching the reminders on stamps {@code lastSentAt} with the current
     * time. Without it the row would look like "never reminded", the scheduler
     * would consider the user due on its very next pass, and the user would get a
     * notification seconds after ticking the box - which reads as a bug. With it
     * the first reminder arrives one full interval later, as configured. The clock
     * is read in {@link NotificationPreference#ZONE}, the same one the scheduler
     * compares it against.
     */
    @Transactional
    public NotificationPreferencesResponse savePreferences(Integer userId,
                                                           NotificationPreferencesRequest request) {
        LocalDateTime now = LocalDateTime.now(NotificationPreference.ZONE);
        NotificationPreference preference = preferenceRepository.findById(userId)
                .orElseGet(() -> newPreference(userId, now));

        boolean wasEnabled = preference.isPushEnabled();
        boolean nowEnabled = Boolean.TRUE.equals(request.pushEnabled());

        preference.setPushEnabled(nowEnabled);
        preference.setIntervalMinutes(request.intervalMinutes());
        preference.setQuietHoursStart(request.quietHoursStart());
        preference.setQuietHoursEnd(request.quietHoursEnd());
        if (nowEnabled && !wasEnabled) {
            preference.setLastSentAt(now);
        }
        preference.setUpdatedAt(now);
        preferenceRepository.save(preference);

        log.info("Notification preferences saved for user_id={}: enabled={}, every {} min, quiet {}-{}",
                userId, nowEnabled, request.intervalMinutes(),
                request.quietHoursStart(), request.quietHoursEnd());
        return NotificationPreferencesResponse.from(preference);
    }

    /**
     * Registers - or re-registers - one browser.
     *
     * <p>The endpoint is the identity of the subscription, so an endpoint already
     * on file is updated in place rather than inserted again. It may well belong
     * to another user: browsers hand the same endpoint to whoever is signed in on
     * that profile, and two accounts sharing a machine would otherwise collide on
     * the unique index. Reassigning it is also the safe outcome - leaving it with
     * the previous owner would push one user's reminders onto the other's screen.
     */
    @Transactional
    public long saveSubscription(Integer userId, PushSubscriptionRequest request) {
        String endpoint = request.endpoint().trim();
        LocalDateTime now = LocalDateTime.now(NotificationPreference.ZONE);

        PushSubscription subscription = subscriptionRepository.findByEndpoint(endpoint)
                .orElseGet(() -> {
                    PushSubscription created = new PushSubscription();
                    created.setEndpoint(endpoint);
                    created.setCreatedAt(now);
                    return created;
                });

        if (subscription.getId() != null && !userId.equals(subscription.getUserId())) {
            log.info("Push subscription {} reassigned from user_id={} to user_id={}",
                    subscription.getId(), subscription.getUserId(), userId);
        }

        subscription.setUserId(userId);
        subscription.setP256dh(request.p256dh().trim());
        subscription.setAuth(request.auth().trim());
        subscription.setUserAgent(trimToNull(request.userAgent()));
        subscriptionRepository.save(subscription);

        long count = subscriptionRepository.countByUserId(userId);
        log.info("Push subscription stored for user_id={} ({} device(s) registered)", userId, count);
        return count;
    }

    /**
     * Forgets one browser. Idempotent by design: the client calls this when the
     * permission is revoked or the subscription is replaced, and it may well have
     * already been removed here - by the previous call, or by the scheduler after
     * the push service reported it gone. Nothing happens either.
     *
     * <p>A row belonging to somebody else is left alone: the endpoint is not a
     * secret the way a token is, and accepting it would turn this into a way of
     * unsubscribing another account.
     */
    @Transactional
    public void deleteSubscription(Integer userId, String endpoint) {
        subscriptionRepository.findByEndpoint(endpoint.trim()).ifPresent(subscription -> {
            if (!userId.equals(subscription.getUserId())) {
                log.warn("user_id={} tried to delete a push subscription owned by user_id={}; ignored",
                        userId, subscription.getUserId());
                return;
            }
            subscriptionRepository.delete(subscription);
            log.info("Push subscription removed for user_id={}", userId);
        });
    }

    /**
     * Sends one notification to every device of the caller, right now.
     *
     * <p>Neither the interval nor the silent window applies: the user pressed a
     * button and is waiting to see whether notifications reach them at all.
     * {@code lastSentAt} is left untouched for the same reason - a test must not
     * postpone the next real reminder.
     *
     * <p>Not {@code @Transactional}: the sends are network calls of unbounded
     * duration and have no business holding a connection from the pool. Each
     * repository call below runs in its own short transaction.
     *
     * @throws FeatureUnavailableException (503) when the server has no VAPID keys
     */
    public PushTestResultResponse sendTest(Integer userId) {
        requireConfigured();

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(userId);
        if (subscriptions.isEmpty()) {
            log.info("Test notification requested by user_id={} with no registered device", userId);
            return new PushTestResultResponse(0, 0);
        }

        int sent = 0;
        int failed = 0;
        List<PushSubscription> expired = new ArrayList<>();
        for (PushSubscription subscription : subscriptions) {
            switch (pushSender.send(subscription, TEST_TITLE, TEST_BODY)) {
                case DELIVERED -> sent++;
                // Counted as failures: from the user's point of view that device did
                // not get the notification, and it is about to stop existing here.
                case EXPIRED -> {
                    failed++;
                    expired.add(subscription);
                }
                case FAILED -> failed++;
            }
        }
        if (!expired.isEmpty()) {
            subscriptionRepository.deleteAll(expired);
            log.info("Removed {} expired push subscription(s) of user_id={}", expired.size(), userId);
        }

        log.info("Test notification for user_id={}: {} sent, {} failed", userId, sent, failed);
        return new PushTestResultResponse(sent, failed);
    }

    // ---- helpers ----

    private NotificationPreference newPreference(Integer userId, LocalDateTime now) {
        NotificationPreference created = new NotificationPreference();
        created.setUserId(userId);
        created.setCreatedAt(now);
        return created;
    }

    /**
     * Same treatment {@code SupportService} gives a missing support mailbox: the
     * feature is simply not available on this installation, which is a 503 and a
     * line in the server log saying what is missing - not something the caller can
     * fix by changing the request.
     */
    private void requireConfigured() {
        if (!pushSender.isConfigured()) {
            log.error("A push notification was requested but no VAPID key pair is configured "
                    + "(VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY).");
            throw new FeatureUnavailableException(
                    "Push notifications are not available on this server.");
        }
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
