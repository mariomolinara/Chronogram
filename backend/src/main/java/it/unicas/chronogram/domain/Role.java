package it.unicas.chronogram.domain;

/**
 * Authorisation role of an account. Mapped as a string in {@code user_auth.role}
 * and exposed to Spring Security as the authority {@code ROLE_<name>}.
 */
public enum Role {

    /** Ordinary end user: owns and sees only their own activities. */
    USER,

    /** Back-office account: read-only access to aggregate stats and data export. */
    ADMIN;

    /** The Spring Security authority string for this role. */
    public String authority() {
        return "ROLE_" + name();
    }
}
