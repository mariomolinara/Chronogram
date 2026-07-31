package it.unicas.chronogram.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload. Field names match the front-end form exactly.
 * {@code birthday} is expected in {@code dd-MM-yyyy} format (as sent by the app).
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required") String name,
        @NotBlank(message = "Surname is required") String surname,
        String phone,
        @NotBlank(message = "Email is required") @Email(message = "Invalid email format") String email,
        @NotBlank(message = "Password is required")
        @Size(min = 8, message = "Password must be at least 8 characters long") String password,
        String birthday,
        String gender,
        String address
) {
}
