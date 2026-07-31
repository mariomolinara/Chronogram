package it.unicas.chronogram.llm.dto;

/**
 * Structured activity fields extracted from a natural-language prompt.
 * Field names match the legacy {@code llmDTO} for front-end compatibility.
 */
public record LlmResponse(
        String name,
        Integer durationMins,
        String details,
        Integer pleasantness,
        Integer activityTypeId,
        String recurrence,
        String costEuro,
        String location
) {
    public static LlmResponse empty() {
        return new LlmResponse(null, null, null, null, null, null, null, null);
    }
}
