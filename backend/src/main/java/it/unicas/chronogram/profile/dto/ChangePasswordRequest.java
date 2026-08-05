package it.unicas.chronogram.profile.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Password change performed by the account owner. Only presence is validated
 * here: the strength rules live in {@code PasswordPolicy} and are applied to
 * {@code newPassword} alone, because {@code currentPassword} was chosen under
 * whatever policy was in force at the time and must still be verifiable.
 */
public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required") String currentPassword,
        @NotBlank(message = "New password is required") String newPassword
) {
}
