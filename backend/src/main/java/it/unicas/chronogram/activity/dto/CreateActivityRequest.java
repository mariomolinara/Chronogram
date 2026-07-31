package it.unicas.chronogram.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload to create an activity. The owning user is taken from the JWT, never
 * from the request body.
 */
public record CreateActivityRequest(
        @NotNull(message = "Activity type ID is required") Integer activityTypeId,
        @Min(value = 0, message = "Duration must be non-negative") Integer durationMins,
        @Min(value = -3, message = "Pleasantness must be between -3 and 3")
        @Max(value = 3, message = "Pleasantness must be between -3 and 3") Integer pleasantness,
        String location,
        String costEuro
) {
}
