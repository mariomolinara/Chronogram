package it.unicas.chronogram.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Change of the administrator's own credentials. Both {@code newEmail} and
 * {@code newPassword} are optional (at least one must be present, enforced in the
 * service); {@code currentPassword} is always required so a stolen token alone
 * cannot take over the account.
 */
public record UpdateAdminCredentialsRequest(
        @NotBlank(message = "Current password is required") String currentPassword,
        @Email(message = "Invalid email format") String newEmail,
        @Size(min = 8, message = "Password must be at least 8 characters long") String newPassword
) {
}
