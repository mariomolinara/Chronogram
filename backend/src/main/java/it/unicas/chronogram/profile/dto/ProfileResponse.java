package it.unicas.chronogram.profile.dto;

import java.time.LocalDate;

/**
 * The authenticated user's own profile, as returned by both {@code GET /api/profile/me}
 * and {@code POST /api/profile/update} (inside the standard {@code ApiResponse.data}).
 *
 * <p>{@code birthday} is a {@link LocalDate} and therefore serialised as ISO
 * {@code YYYY-MM-DD}, which is what the client sends back on update. Note this
 * differs from the {@code dd-MM-yyyy} the registration form posts; the profile
 * endpoints deliberately speak one unambiguous format.
 *
 * <p>{@code email} is included because the screen shows it, but it is read-only:
 * it identifies the login and cannot be changed through this API.
 */
public record ProfileResponse(
        String name,
        String surname,
        String address,
        String phone,
        String email,
        LocalDate birthday,
        String gender
) {
}
