package it.unicas.chronogram.activity.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import it.unicas.chronogram.domain.ActivityType;

public record ActivityTypeResponse(
        Integer activityTypeId,
        String name,
        String description,
        @JsonProperty("isInstrumental") Boolean isInstrumental,
        @JsonProperty("isRoutinary") Boolean isRoutinary
) {

    public static ActivityTypeResponse from(ActivityType t) {
        return new ActivityTypeResponse(
                t.getId(), t.getName(), t.getDescription(), t.getInstrumental(), t.getRoutinary());
    }
}
