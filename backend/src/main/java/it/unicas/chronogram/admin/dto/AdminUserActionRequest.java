package it.unicas.chronogram.admin.dto;

import jakarta.validation.constraints.Size;

/**
 * Optional note an administrator attaches to approving, blocking or unblocking
 * an account. When present it is emailed to the user, so they are not left
 * guessing why their access changed.
 *
 * @param message free text, or null/blank to send the standard notice only
 */
public record AdminUserActionRequest(@Size(max = 1000, message = "The message may not exceed 1000 characters")
                                     String message) {
}
