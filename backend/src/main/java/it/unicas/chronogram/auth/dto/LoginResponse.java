package it.unicas.chronogram.auth.dto;

/**
 * Login result. Kept flat (not wrapped in the generic envelope) to preserve the
 * exact shape the front-end auth store consumes: {@code success, message,
 * username, token}.
 */
public record LoginResponse(boolean success, String message, String username, String token) {

    public static LoginResponse success(String username, String token) {
        return new LoginResponse(true, "Login successful!", username, token);
    }

    public static LoginResponse failure(String message) {
        return new LoginResponse(false, message, null, null);
    }
}
