package it.unicas.chronogram.domain;

/**
 * How an account was created. {@code LOCAL} accounts were registered with
 * email and password; {@code GOOGLE} accounts were created from a verified
 * Google ID token and may have no local password at all.
 */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
