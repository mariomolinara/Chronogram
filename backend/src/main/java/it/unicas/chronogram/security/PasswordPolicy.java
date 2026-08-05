package it.unicas.chronogram.security;

import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;

/**
 * Server-side strength rules for a password chosen by a user, mirroring what the
 * front-end enforces in the form: at least 8 characters with an uppercase letter,
 * a lowercase letter, a digit and a symbol.
 *
 * <p>Kept as a separate class rather than as Bean Validation annotations on a DTO
 * because the same rule has to hold wherever a password is set, and because the
 * check must never run against a value the caller did not actually choose (the
 * "current password" field, for instance, may legitimately predate this rule).
 *
 * <p>The upper bound is not cosmetic: BCrypt hashes only the first 72 bytes of
 * the input, so anything longer would silently contribute nothing to the hash
 * and give a false sense of strength.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    /** BCrypt ignores every byte past this offset, so a longer secret is an illusion. */
    public static final int MAX_LENGTH = 72;

    private static final String REQUIREMENTS =
            "Password must be at least " + MIN_LENGTH + " characters long and contain an uppercase "
                    + "letter, a lowercase letter, a digit and a symbol.";

    private PasswordPolicy() {
    }

    /**
     * @throws ValidationException with a message meant for the end user (HTTP 400)
     *                             when the password does not satisfy the policy
     */
    public static void validate(String password) {
        if (password == null || password.length() < MIN_LENGTH) {
            throw new ValidationException(REQUIREMENTS);
        }
        if (password.length() > MAX_LENGTH) {
            throw new ValidationException("Password must be at most " + MAX_LENGTH + " characters long.");
        }

        boolean upper = false;
        boolean lower = false;
        boolean digit = false;
        boolean symbol = false;
        for (int i = 0; i < password.length(); i++) {
            char c = password.charAt(i);
            if (Character.isUpperCase(c)) {
                upper = true;
            } else if (Character.isLowerCase(c)) {
                lower = true;
            } else if (Character.isDigit(c)) {
                digit = true;
            } else if (!Character.isWhitespace(c)) {
                // Anything that is neither a letter, a digit nor blank space counts
                // as a symbol, so accented or non-Latin scripts are not penalised.
                symbol = true;
            }
        }

        if (!(upper && lower && digit && symbol)) {
            throw new ValidationException(REQUIREMENTS);
        }
    }
}
