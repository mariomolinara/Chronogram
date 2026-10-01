package it.unicas.chronogram.notification.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * The reminder settings a user saved. The owner is taken from the JWT, never
 * from the body.
 *
 * <p>The bounds are the ones the UI offers: at least a quarter of an hour
 * between two reminders (anything shorter is a nuisance rather than a reminder,
 * and would multiply the push traffic), at most a day.
 */
public record NotificationPreferencesRequest(

        @NotNull(message = "pushEnabled is required") Boolean pushEnabled,

        @NotNull(message = "intervalMinutes is required")
        @Min(value = 15, message = "The interval must be at least 15 minutes")
        @Max(value = 1440, message = "The interval must be at most 1440 minutes")
        Integer intervalMinutes,

        /** Hour the silent window opens, or {@code null} for no silent window. */
        @Min(value = 0, message = "quietHoursStart must be an hour between 0 and 23")
        @Max(value = 23, message = "quietHoursStart must be an hour between 0 and 23")
        Integer quietHoursStart,

        /** Hour the silent window closes, or {@code null} for no silent window. */
        @Min(value = 0, message = "quietHoursEnd must be an hour between 0 and 23")
        @Max(value = 23, message = "quietHoursEnd must be an hour between 0 and 23")
        Integer quietHoursEnd
) {

    /**
     * A silent window needs both ends or neither: one hour on its own describes
     * nothing the scheduler could act on, and guessing the missing half would
     * silence the reminders for a stretch the user never asked for.
     */
    @AssertTrue(message = "Quiet hours need both a start and an end, or neither")
    public boolean isQuietHoursComplete() {
        return (quietHoursStart == null) == (quietHoursEnd == null);
    }
}
