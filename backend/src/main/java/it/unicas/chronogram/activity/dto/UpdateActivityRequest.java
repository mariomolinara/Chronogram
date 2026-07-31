package it.unicas.chronogram.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload to update an activity. Only non-null fields are applied (partial update).
 */
public record UpdateActivityRequest(
        @NotNull(message = "Activity ID is required for update") Integer activityId,
        Integer activityTypeId,
        @Min(value = 0, message = "Duration must be non-negative") Integer durationMins,
        @Min(value = -3, message = "Pleasantness must be between -3 and 3")
        @Max(value = 3, message = "Pleasantness must be between -3 and 3") Integer pleasantness,
        String location,
        String costEuro
) {
}
