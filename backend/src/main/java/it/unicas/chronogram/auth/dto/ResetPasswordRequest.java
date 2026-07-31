package it.unicas.chronogram.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetPasswordRequest(
        @NotBlank(message = "Reset token cannot be empty") String token,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long") String newPassword
) {
}
