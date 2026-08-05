package it.unicas.chronogram.security;

import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The server-side password strength rule. Each rejected case isolates exactly one
 * missing requirement, so a future relaxation of the policy cannot pass unnoticed.
 */
class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "Str0ng!pass",      // the canonical happy path
            "Aa1!aaaa",         // exactly the minimum length
            "P4ssw0rd$econd",   // several symbols
            "Ünic0de!x"    // non-ASCII letters still count as letters
    })
    void acceptsPasswordsSatisfyingEveryRequirement(String password) {
        assertThatCode(() -> PasswordPolicy.validate(password)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Aa1!aaa",          // 7 characters: one short
            "str0ng!pass",      // no uppercase
            "STR0NG!PASS",      // no lowercase
            "Strong!pass",      // no digit
            "Str0ngpass"        // no symbol
    })
    void rejectsPasswordsMissingARequirement(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at least 8 characters");
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rejectsNullAndEmpty(String password) {
        assertThatThrownBy(() -> PasswordPolicy.validate(password))
                .isInstanceOf(ValidationException.class);
    }

    /**
     * BCrypt hashes only the first 72 bytes, so accepting anything longer would
     * advertise a strength the stored hash does not have.
     */
    @Test
    void rejectsPasswordsLongerThanWhatBcryptActuallyHashes() {
        String tooLong = "Aa1!" + "x".repeat(PasswordPolicy.MAX_LENGTH);

        assertThatThrownBy(() -> PasswordPolicy.validate(tooLong))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("at most 72 characters");
    }

    @Test
    void whitespaceDoesNotCountAsASymbol() {
        assertThatThrownBy(() -> PasswordPolicy.validate("Str0ng pass"))
                .isInstanceOf(ValidationException.class);
    }
}
