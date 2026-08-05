package it.unicas.chronogram.domain;

/**
 * Lifecycle state of an account, independent from its {@link Role}.
 *
 * <p>Why this is not the pre-existing {@code is_active} boolean: a flag cannot
 * tell "registered but never approved" apart from "approved and later blocked",
 * and the two need different messages at login and different actions in the back
 * office. {@code is_active} is kept as the derived "may authenticate" flag - see
 * {@link UserAuth#setStatus(AccountStatus)} - so the JWT filter and the
 * password-reset flow keep working unchanged.
 */
public enum AccountStatus {

    /** Registered, waiting for an administrator to approve the request. */
    PENDING,

    /** Approved (or auto-approved by email domain): can sign in normally. */
    ACTIVE,

    /** Disabled by an administrator. Reversible, unlike deletion. */
    BLOCKED;

    /** Whether an account in this state is allowed to authenticate. */
    public boolean canAuthenticate() {
        return this == ACTIVE;
    }
}
