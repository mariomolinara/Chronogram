package it.unicas.chronogram.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Deletion of a participant account, with the message the person receives.
 *
 * <p>The message is mandatory, unlike the one on the reversible actions:
 * deletion destroys every activity the user recorded and cannot be undone, so
 * they are owed an explanation, and requiring the administrator to write one is
 * also a deliberate pause before an irreversible click.
 *
 * @param message explanation emailed to the user before the account is removed
 */
public record DeleteUserRequest(@NotBlank(message = "A message for the user is required")
                                @Size(max = 1000, message = "The message may not exceed 1000 characters")
                                String message) {
}
