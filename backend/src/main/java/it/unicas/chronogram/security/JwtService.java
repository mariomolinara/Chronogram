package it.unicas.chronogram.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import it.unicas.chronogram.config.ChronogramProperties;
import it.unicas.chronogram.domain.Role;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Issues and validates HMAC-SHA256 JWTs. The signing key is derived from
 * {@code chronogram.security.jwt.secret}, which must be at least 32 bytes.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMs;
    private final String issuer;

    public JwtService(ChronogramProperties props) {
        ChronogramProperties.Jwt jwt = props.getSecurity().getJwt();
        String secret = jwt.getSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "chronogram.security.jwt.secret (env JWT_SECRET_KEY) must be set and at least 32 bytes long.");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = jwt.getExpirationMs();
        this.issuer = jwt.getIssuer();
    }

    /**
     * Creates a signed token whose subject is the user's email, carrying the role
     * as a {@code role} claim.
     *
     * <p>The claim is a convenience for the client (it decides whether to show the
     * admin section); it is never the source of truth for authorization. The
     * filter re-reads the role from the database on every request, so revoking an
     * admin takes effect immediately instead of waiting for the token to expire.
     */
    public String generateToken(String email, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(email)
                .claim("role", role.name())
                .issuer(issuer)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(expirationMs)))
                .signWith(key)
                .compact();
    }

    /**
     * Validates the signature/expiry and returns the subject (email).
     *
     * @throws JwtException if the token is invalid or expired.
     */
    public String extractEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
