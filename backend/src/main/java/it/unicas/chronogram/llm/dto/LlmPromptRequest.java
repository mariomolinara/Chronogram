package it.unicas.chronogram.llm.dto;

import jakarta.validation.constraints.NotBlank;

public record LlmPromptRequest(
        @NotBlank(message = "Prompt is required") String prompt,
        String model
) {
}
