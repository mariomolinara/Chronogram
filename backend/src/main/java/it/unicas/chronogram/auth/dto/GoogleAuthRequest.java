package it.unicas.chronogram.auth.dto;

import jakarta.validation.constraints.NotBlank;

/** Google sign-in: the ID token obtained by the client from Google. */
public record GoogleAuthRequest(
        @NotBlank(message = "Google ID token is required") String idToken
) {
}
