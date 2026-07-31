package it.unicas.chronogram.activity.dto;

import jakarta.validation.constraints.NotNull;

public record DeleteActivityRequest(
        @NotNull(message = "Activity ID is required for deletion") Integer activityId
) {
}
