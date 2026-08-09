package it.unicas.chronogram.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.exception.ApiExceptions.AuthenticationFailedException;
import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.UpstreamServiceException;
import it.unicas.chronogram.config.ChronogramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.Set;

/**
 * Validates Google ID tokens by asking Google itself.
 *
 * <p>The {@code tokeninfo} endpoint checks the signature and the expiry
 * server-side, so no JWKS caching or extra crypto dependency is needed here;
 * what it does NOT check is who the token was minted for, which is why the
 * {@code aud} claim is compared against our configured client IDs - a valid
 * Google token issued to some other application must not open a session on
 * Chronogram. Suitable for login-scale traffic; a JWKS-based local check can
 * replace it transparently if volume ever demands it.
 */
@Service
public class GoogleTokenVerifier {

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerifier.class);
    private static final String TOKENINFO_URL = "https://oauth2.googleapis.com/tokeninfo";
    private static final Set<String> VALID_ISSUERS =
            Set.of("accounts.google.com", "https://accounts.google.com");
    /** One message for every rejection: details would only help an attacker probe. */
    private static final String REJECTED_MESSAGE = "Google sign-in failed. Please try again.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ChronogramProperties.Google props;

    public GoogleTokenVerifier(RestClient.Builder restClientBuilder,
                               ObjectMapper objectMapper,
                               ChronogramProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.props = properties.getGoogle();
    }

    /** The identity claims Chronogram consumes out of a verified ID token. */
    public record GoogleIdentity(String subject, String email, String givenName, String familyName) {
    }

    /**
     * @throws FeatureUnavailableException if no client ID is configured
     * @throws AuthenticationFailedException if the token is invalid, expired,
     *         minted for another app, or carries an unverified email
     * @throws UpstreamServiceException if Google cannot be reached
     */
    public GoogleIdentity verify(String idToken) {
        if (!props.isConfigured()) {
            throw new FeatureUnavailableException("Google sign-in is not configured on this server.");
        }
        if (idToken == null || idToken.isBlank()) {
            throw new AuthenticationFailedException(REJECTED_MESSAGE);
        }

        JsonNode claims = callTokeninfo(idToken);

        String issuer = claims.path("iss").asText("");
        String audience = claims.path("aud").asText("");
        String subject = claims.path("sub").asText("");
        String email = claims.path("email").asText("");
        boolean emailVerified = "true".equals(claims.path("email_verified").asText(""));

        if (!VALID_ISSUERS.contains(issuer)) {
            log.warn("Google token rejected: unexpected issuer '{}'", issuer);
            throw new AuthenticationFailedException(REJECTED_MESSAGE);
        }
        if (!props.getClientIds().contains(audience)) {
            log.warn("Google token rejected: audience '{}' is not one of ours", audience);
            throw new AuthenticationFailedException(REJECTED_MESSAGE);
        }
        if (subject.isBlank() || email.isBlank()) {
            log.warn("Google token rejected: missing sub/email claims");
            throw new AuthenticationFailedException(REJECTED_MESSAGE);
        }
        if (!emailVerified) {
            // An unverified address could belong to somebody else: accepting it
            // would hand that person's future account to whoever claimed it first.
            log.warn("Google token rejected: email {} is not verified with Google", email);
            throw new AuthenticationFailedException(REJECTED_MESSAGE);
        }

        return new GoogleIdentity(subject, email,
                claims.path("given_name").asText(null),
                claims.path("family_name").asText(null));
    }

    private JsonNode callTokeninfo(String idToken) {
        try {
            String body = restClient.get()
                    .uri(TOKENINFO_URL + "?id_token={token}", idToken)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientResponseException e) {
            // tokeninfo answers 4xx for a malformed/expired token: that is a bad
            // credential, not an outage.
            log.warn("Google token rejected by tokeninfo: {}", e.getStatusCode());
            throw new AuthenticationFailedException(REJECTED_MESSAGE);
        } catch (RestClientException | com.fasterxml.jackson.core.JacksonException e) {
            log.error("Google tokeninfo call failed", e);
            throw new UpstreamServiceException(
                    "Google sign-in is temporarily unavailable. Please try again in a moment.", e);
        }
    }
}
