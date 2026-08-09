package it.unicas.chronogram.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import it.unicas.chronogram.common.exception.ApiExceptions.UpstreamServiceException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import it.unicas.chronogram.config.ChronogramProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Server-side verification of Google reCAPTCHA v3 tokens.
 *
 * <p>The front-end obtains a short-lived token with the public SITE key and
 * sends it in the registration payload; only this class, holding the SECRET
 * key, can find out what Google thinks of it. With no secret configured the
 * check is a no-op, so development and tests run without Google credentials -
 * protection exists only when BOTH sides are configured, which the setup guide
 * (docs/GOOGLE_AUTH_RECAPTCHA.md) spells out.
 */
@Service
public class RecaptchaService {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaService.class);
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    /** Message shown for every rejection: a bot gains nothing from details. */
    private static final String REJECTED_MESSAGE =
            "We could not verify that the request comes from a person. Please try again.";
    private static final String OUTAGE_MESSAGE =
            "The anti-bot verification service is temporarily unavailable. Please try again in a moment.";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ChronogramProperties.Recaptcha props;

    public RecaptchaService(RestClient.Builder restClientBuilder,
                            ObjectMapper objectMapper,
                            ChronogramProperties properties) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.props = properties.getRecaptcha();
    }

    /**
     * Validates the token or throws. Failing closed is deliberate: during a
     * Google outage registration is briefly unavailable instead of unprotected.
     *
     * @param token          the token produced by {@code grecaptcha.execute}
     * @param expectedAction the action name the front-end used (e.g. "register");
     *                       v3 echoes it back, and accepting a token minted for a
     *                       different action would let one widget unlock another
     * @throws ValidationException      if the token is missing, invalid or scored as a bot
     * @throws UpstreamServiceException if Google cannot be reached
     */
    public void verify(String token, String expectedAction) {
        if (!props.isEnabled()) {
            return;
        }
        if (token == null || token.isBlank()) {
            log.warn("reCAPTCHA rejected: no token in the request");
            throw new ValidationException(REJECTED_MESSAGE);
        }

        JsonNode result = callSiteverify(token);

        boolean success = result.path("success").asBoolean(false);
        double score = result.path("score").asDouble(0.0);
        String action = result.path("action").asText("");

        if (!success) {
            // error-codes tells apart an expired token from a wrong secret; it
            // belongs in the log, never in the response.
            log.warn("reCAPTCHA rejected: verification failed, error-codes={}", result.path("error-codes"));
            throw new ValidationException(REJECTED_MESSAGE);
        }
        if (!expectedAction.equals(action)) {
            log.warn("reCAPTCHA rejected: action '{}' does not match expected '{}'", action, expectedAction);
            throw new ValidationException(REJECTED_MESSAGE);
        }
        if (score < props.getMinScore()) {
            log.warn("reCAPTCHA rejected: score {} below threshold {}", score, props.getMinScore());
            throw new ValidationException(REJECTED_MESSAGE);
        }
        log.debug("reCAPTCHA accepted with score {}", score);
    }

    private JsonNode callSiteverify(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", props.getSecret());
        form.add("response", token);
        try {
            String body = restClient.post()
                    .uri(VERIFY_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(String.class);
            return objectMapper.readTree(body == null ? "{}" : body);
        } catch (RestClientException | com.fasterxml.jackson.core.JacksonException e) {
            log.error("reCAPTCHA siteverify call failed", e);
            throw new UpstreamServiceException(OUTAGE_MESSAGE, e);
        }
    }
}
