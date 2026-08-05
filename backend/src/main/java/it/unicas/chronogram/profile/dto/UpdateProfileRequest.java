package it.unicas.chronogram.profile.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Profile edit submitted by the owner of the account. The mandatory fields are
 * the same three the registration form requires, so a profile can never be
 * downgraded below what was needed to create it.
 *
 * <p>There is intentionally no {@code email} field: the address identifies the
 * login, and an {@code email} property sent by a client is ignored rather than
 * applied. The {@code @Size} bounds mirror the {@code user} table columns so an
 * oversized value comes back as a readable 400 instead of a database error.
 */
public record UpdateProfileRequest(
        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters long") String name,

        @NotBlank(message = "Surname is required")
        @Size(max = 100, message = "Surname must be at most 100 characters long") String surname,

        @NotBlank(message = "Address is required")
        @Size(max = 255, message = "Address must be at most 255 characters long") String address,

        @Size(max = 30, message = "Phone must be at most 30 characters long") String phone,

        /** ISO {@code YYYY-MM-DD}; {@code null} or blank clears the stored date. */
        String birthday,

        @Size(max = 20, message = "Gender must be at most 20 characters long") String gender
) {
}
