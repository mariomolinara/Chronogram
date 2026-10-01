package it.unicas.chronogram.notification;

import it.unicas.chronogram.domain.NotificationPreference;
import it.unicas.chronogram.domain.PushSubscription;
import it.unicas.chronogram.repository.NotificationPreferenceRepository;
import it.unicas.chronogram.repository.PushSubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * Sends the periodic "log your activities" reminders.
 *
 * <p>Wakes up once a minute, which is finer than any interval the API accepts
 * (15 minutes at the shortest) and therefore fine enough to honour every setting
 * without a per-user timer. A pass is a no-op on an installation with no VAPID
 * keys and costs one indexed query on any other.
 *
 * <p>Deliberately not {@code @Transactional}: a pass makes network calls of
 * unbounded duration to third-party push services, and holding a database
 * connection across them would starve the pool. Every repository call below runs
 * in its own short transaction instead, and the reads are finished before the
 * first send starts.
 */
@Component
public class NotificationScheduler {

    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);

    /**
     * The zone both this pass and {@code NotificationService} read the clock in -
     * see {@link NotificationPreference#ZONE} for why it is pinned.
     */
    private static final ZoneId ZONE = NotificationPreference.ZONE;

    private static final String REMINDER_TITLE = "Chronogram";
    private static final String REMINDER_BODY = "Time to log your activities!";

    private final NotificationPreferenceRepository preferenceRepository;
    private final PushSubscriptionRepository subscriptionRepository;
    private final PushSender pushSender;

    public NotificationScheduler(NotificationPreferenceRepository preferenceRepository,
                                 PushSubscriptionRepository subscriptionRepository,
                                 PushSender pushSender) {
        this.preferenceRepository = preferenceRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.pushSender = pushSender;
    }

    /**
     * One pass over the users who are owed a reminder.
     *
     * <p>{@code fixedDelay} rather than {@code fixedRate}: a pass that runs long
     * because a push service is slow must not queue up the ones it overlapped with.
     *
     * <p>Nothing propagates out of this method. An exception escaping a
     * {@code @Scheduled} method is logged by Spring and the schedule survives, but
     * one unreachable push service would then cost every later user in the same
     * pass their reminder - so failures are contained per user instead.
     */
    @Scheduled(fixedDelay = 60_000)
    public void sendDueReminders() {
        runAt(LocalDateTime.now(ZONE));
    }

    /**
     * One pass at a given moment. Package-private so the tests can place "now"
     * inside or outside a silent window instead of waiting for the wall clock to
     * reach 22:00.
     */
    void runAt(LocalDateTime now) {
        if (!pushSender.isConfigured()) {
            // The feature is off on this installation. Not logged: it would be a
            // line a minute, forever, on every deployment that never enables push.
            return;
        }

        List<NotificationPreference> candidates;
        try {
            candidates = preferenceRepository.findByPushEnabledTrue();
        } catch (RuntimeException e) {
            log.error("Could not read the notification preferences; skipping this pass", e);
            return;
        }

        int reminded = 0;
        for (NotificationPreference preference : candidates) {
            try {
                if (remind(preference, now)) {
                    reminded++;
                }
            } catch (RuntimeException e) {
                log.error("Reminder for user_id={} failed; continuing with the others",
                        preference.getUserId(), e);
            }
        }
        if (reminded > 0) {
            log.info("Sent periodic reminders to {} user(s)", reminded);
        }
    }

    /**
     * Handles one user. Returns whether a reminder was actually attempted.
     */
    private boolean remind(NotificationPreference preference, LocalDateTime now) {
        if (!preference.isDueAt(now)) {
            return false;
        }

        if (preference.isQuietAt(now)) {
            // Skipped WITHOUT touching lastSentAt, which means the user stays due
            // and gets their reminder the moment the window closes. That is the
            // intended behaviour rather than an accident: the point of the window
            // is "do not wake me", and the first thing one wants on getting up is
            // the reminder that was withheld overnight. The alternative - stamping
            // lastSentAt on a skip - would silently push the morning reminder a
            // whole interval past the end of the window.
            log.debug("user_id={} is due but inside its quiet hours; postponed",
                    preference.getUserId());
            return false;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(preference.getUserId());
        int sent = 0;
        List<PushSubscription> expired = new ArrayList<>();
        for (PushSubscription subscription : subscriptions) {
            switch (pushSender.send(subscription, REMINDER_TITLE, REMINDER_BODY)) {
                case DELIVERED -> sent++;
                // The push service says this device is gone: drop the row rather
                // than retrying it every interval for the life of the account.
                case EXPIRED -> expired.add(subscription);
                case FAILED -> {
                    // Already logged with its cause by PushSender.
                }
            }
        }
        if (!expired.isEmpty()) {
            subscriptionRepository.deleteAll(expired);
            log.info("Removed {} expired push subscription(s) of user_id={}",
                    expired.size(), preference.getUserId());
        }

        // Stamped once the attempt is over, whatever came of it - including the
        // case of a user with no device left. Retrying a failed send on the next
        // minute instead would turn an unreachable push service into a per-minute
        // hammering, and the next interval is soon enough for a reminder.
        preference.setLastSentAt(now);
        preference.setUpdatedAt(now);
        preferenceRepository.save(preference);

        log.debug("Reminder for user_id={}: {}/{} device(s) reached",
                preference.getUserId(), sent, subscriptions.size());
        return true;
    }
}
