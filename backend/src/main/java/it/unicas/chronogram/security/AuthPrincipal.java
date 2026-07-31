package it.unicas.chronogram.security;

/**
 * Authenticated caller, exposed to controllers via {@code @AuthenticationPrincipal}.
 * Replaces the legacy {@code request.getAttribute("authenticatedUserId")} pattern.
 */
public record AuthPrincipal(Integer userId, String email) {
}
