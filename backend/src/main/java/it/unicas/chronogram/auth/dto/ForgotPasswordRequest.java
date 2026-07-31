package it.unicas.chronogram.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ForgotPasswordRequest(
        @NotBlank(message = "Email is required") @Email(message = "A valid email is required") String email
) {
}
