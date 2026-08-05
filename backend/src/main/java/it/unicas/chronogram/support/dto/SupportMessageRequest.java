package it.unicas.chronogram.support.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A message written on the support screen. The sender is never part of the
 * payload: it is resolved from the JWT, so nobody can open a ticket in someone
 * else's name.
 */
public record SupportMessageRequest(
        @NotBlank(message = "Subject is required")
        @Size(max = 150, message = "Subject must be at most 150 characters long") String subject,

        @NotBlank(message = "Message is required")
        @Size(max = 2000, message = "Message must be at most 2000 characters long") String message
) {
}
