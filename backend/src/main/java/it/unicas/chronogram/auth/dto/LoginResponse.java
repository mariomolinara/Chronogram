package it.unicas.chronogram.auth.dto;

import it.unicas.chronogram.domain.Role;

/**
 * Login result. Kept flat (not wrapped in the generic envelope) to preserve the
 * exact shape the front-end auth store consumes: {@code success, message,
 * username, token}, plus {@code role} and {@code mustChangePassword} so the
 * client knows whether to offer the admin section and whether to force a
 * password change before anything else.
 */
public record LoginResponse(boolean success,
                            String message,
                            String username,
                            String token,
                            String role,
                            boolean mustChangePassword) {

    public static LoginResponse success(String username, String token, Role role, boolean mustChangePassword) {
        return new LoginResponse(true, "Login successful!", username, token, role.name(), mustChangePassword);
    }

    public static LoginResponse failure(String message) {
        return new LoginResponse(false, message, null, null, null, false);
    }
}
