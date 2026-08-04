package it.unicas.chronogram.security;

import io.jsonwebtoken.JwtException;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.Role;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private JwtService serviceWithSecret(String secret) {
        ChronogramProperties props = new ChronogramProperties();
        props.getSecurity().getJwt().setSecret(secret);
        return new JwtService(props);
    }

    @Test
    void generatesAndValidatesToken() {
        JwtService jwt = serviceWithSecret("this-is-a-sufficiently-long-test-secret-key!!");
        String token = jwt.generateToken("user@example.com", Role.USER);

        assertThat(jwt.extractEmail(token)).isEqualTo("user@example.com");
    }

    @Test
    void rejectsTamperedToken() {
        JwtService jwt = serviceWithSecret("this-is-a-sufficiently-long-test-secret-key!!");
        String token = jwt.generateToken("user@example.com", Role.USER);

        assertThatThrownBy(() -> jwt.extractEmail(token + "x"))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        String token = serviceWithSecret("first-secret-key-that-is-long-enough-1234").generateToken("a@b.com", Role.USER);
        JwtService other = serviceWithSecret("second-secret-key-that-is-long-enough-999");

        assertThatThrownBy(() -> other.extractEmail(token))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void refusesShortSecretAtConstruction() {
        assertThatThrownBy(() -> serviceWithSecret("too-short"))
                .isInstanceOf(IllegalStateException.class);
    }
}
