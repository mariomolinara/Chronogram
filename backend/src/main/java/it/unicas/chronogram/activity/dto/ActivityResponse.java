package it.unicas.chronogram.activity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.unicas.chronogram.domain.Activity;
import it.unicas.chronogram.domain.ActivityType;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Activity representation returned to clients. Field names mirror the legacy
 * {@code ActivityDTO} (including the joined activity-type fields) so the
 * front-end keeps working unchanged. {@code costEuro} is a string as before.
 */
public record ActivityResponse(
        Integer activityId,
        LocalDate activityDate,
        Integer durationMins,
        Integer pleasantness,
        String location,
        String costEuro,
        Integer userId,
        Integer activityTypeId,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String activityTypeName,
        String activityTypeDescription,
        @JsonProperty("isInstrumental") Boolean isInstrumental,
        @JsonProperty("isRoutinary") Boolean isRoutinary
) {

    public static ActivityResponse from(Activity a) {
        ActivityType type = a.getActivityType();
        return new ActivityResponse(
                a.getId(),
                a.getActivityDate(),
                a.getDurationMins(),
                a.getPleasantness(),
                a.getLocation(),
                a.getCostEuro() != null ? a.getCostEuro().toPlainString() : null,
                a.getUserId(),
                type != null ? type.getId() : null,
                a.getCreatedAt(),
                a.getUpdatedAt(),
                type != null ? type.getName() : null,
                type != null ? type.getDescription() : null,
                type != null ? type.getInstrumental() : null,
                type != null ? type.getRoutinary() : null
        );
    }
}
