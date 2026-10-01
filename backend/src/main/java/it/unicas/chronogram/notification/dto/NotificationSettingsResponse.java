package it.unicas.chronogram.notification.dto;

/**
 * Everything the notification screen needs in one call: whether this
 * installation can send push at all, the key the browser must subscribe with,
 * the user's own settings and how many devices are currently registered.
 *
 * @param configured       whether a VAPID key pair is configured on the server;
 *                         {@code false} means the screen should explain the
 *                         feature is unavailable rather than offer the switch
 * @param vapidPublicKey   the public VAPID key (base64url), or {@code null} when
 *                         not configured
 * @param preferences      the user's settings, or the defaults if they never
 *                         saved any
 * @param subscriptionCount how many devices of this user are registered
 */
public record NotificationSettingsResponse(
        boolean configured,
        String vapidPublicKey,
        NotificationPreferencesResponse preferences,
        long subscriptionCount
) {
}
