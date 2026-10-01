package it.unicas.chronogram.notification.dto;

import it.unicas.chronogram.domain.NotificationPreference;

/**
 * The reminder settings as the server holds them. Returned both on its own -
 * after a save - and nested inside {@link NotificationSettingsResponse}, so the
 * client binds one shape in both places.
 *
 * <p>{@code quietHours*} are {@code number|null} and are serialised as explicit
 * nulls, not dropped: the form needs to bind the field either way.
 */
public record NotificationPreferencesResponse(
        boolean pushEnabled,
        int intervalMinutes,
        Integer quietHoursStart,
        Integer quietHoursEnd
) {

    /**
     * What a user who has never saved anything is told. Not persisted: the row
     * appears the first time the user actually chooses something.
     */
    public static NotificationPreferencesResponse defaults() {
        return new NotificationPreferencesResponse(
                false,
                NotificationPreference.DEFAULT_INTERVAL_MINUTES,
                NotificationPreference.DEFAULT_QUIET_HOURS_START,
                NotificationPreference.DEFAULT_QUIET_HOURS_END);
    }

    public static NotificationPreferencesResponse from(NotificationPreference preference) {
        return new NotificationPreferencesResponse(
                preference.isPushEnabled(),
                preference.getIntervalMinutes(),
                preference.getQuietHoursStart(),
                preference.getQuietHoursEnd());
    }
}
