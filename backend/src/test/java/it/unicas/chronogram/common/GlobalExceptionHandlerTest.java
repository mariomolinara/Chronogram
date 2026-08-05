package it.unicas.chronogram.common;

import it.unicas.chronogram.common.exception.ApiExceptions.AuthenticationFailedException;
import it.unicas.chronogram.common.exception.ApiExceptions.EmailAlreadyExistsException;
import it.unicas.chronogram.common.exception.ApiExceptions.FeatureUnavailableException;
import it.unicas.chronogram.common.exception.ApiExceptions.UpstreamServiceException;
import it.unicas.chronogram.common.exception.ApiExceptions.ResourceNotFoundException;
import it.unicas.chronogram.common.exception.ApiExceptions.ServiceException;
import it.unicas.chronogram.common.exception.ApiExceptions.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link GlobalExceptionHandler}: each domain exception must be
 * translated into the correct HTTP status and a failing {@link ApiResponse}
 * envelope. Covers the mappings and messages not already asserted through the
 * controller slice tests (notably the generic {@code Exception} -> 500 and the
 * {@code ServiceException} message-masking behaviour).
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private void assertFail(ResponseEntity<ApiResponse<Void>> response, HttpStatus status, String message) {
        assertThat(response.getStatusCode()).isEqualTo(status);
        ApiResponse<Void> body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.success()).isFalse();
        assertThat(body.data()).isNull();
        assertThat(body.message()).isEqualTo(message);
    }

    @Test
    void validationMapsTo400WithMessage() {
        assertFail(handler.handleValidation(new ValidationException("bad input")),
                HttpStatus.BAD_REQUEST, "bad input");
    }

    @Test
    void authenticationFailedMapsTo401WithMessage() {
        assertFail(handler.handleAuth(new AuthenticationFailedException("Invalid credentials.")),
                HttpStatus.UNAUTHORIZED, "Invalid credentials.");
    }

    @Test
    void emailAlreadyExistsMapsTo409WithMessage() {
        assertFail(handler.handleConflict(new EmailAlreadyExistsException("Email already registered.")),
                HttpStatus.CONFLICT, "Email already registered.");
    }

    @Test
    void resourceNotFoundMapsTo404WithMessage() {
        assertFail(handler.handleNotFound(new ResourceNotFoundException("Activity not found.")),
                HttpStatus.NOT_FOUND, "Activity not found.");
    }

    @Test
    void serviceExceptionMapsTo500AndMasksInternalMessage() {
        // The original message must NOT leak to the client.
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleService(new ServiceException("DB connection refused at 10.0.0.5"));
        assertFail(response, HttpStatus.INTERNAL_SERVER_ERROR,
                "An internal server error occurred. Please try again later.");
        assertThat(response.getBody().message()).doesNotContain("10.0.0.5");
    }

    @Test
    void upstreamServiceExceptionMapsTo502AndKeepsTheUserFacingMessage() {
        // 502 e non 500: il processo sta bene, e' una dipendenza esterna ad avere
        // fallito. Il messaggio e' gia' scritto per l'utente da chi lancia.
        String userMessage = "The AI assistant is temporarily unavailable. "
                + "Please fill the form manually and try again later.";
        ResponseEntity<ApiResponse<Void>> response = handler.handleUpstream(
                new UpstreamServiceException(userMessage,
                        new IllegalStateException("401 Unauthorized from api.provider.example")));

        assertFail(response, HttpStatus.BAD_GATEWAY, userMessage);
        assertThat(response.getBody().message())
                .doesNotContain("401")
                .doesNotContain("api.provider.example");
    }

    @Test
    void featureUnavailableMapsTo503() {
        String userMessage = "The AI assistant is temporarily unavailable. "
                + "Please fill the form manually and try again later.";
        assertFail(handler.handleFeatureUnavailable(new FeatureUnavailableException(userMessage)),
                HttpStatus.SERVICE_UNAVAILABLE, userMessage);
    }

    @Test
    void unexpectedExceptionMapsTo500WithGenericMessage() {
        ResponseEntity<ApiResponse<Void>> response =
                handler.handleUnexpected(new NullPointerException("boom"));
        assertFail(response, HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.");
        assertThat(response.getBody().message()).doesNotContain("boom");
    }

    @Test
    void beanValidationJoinsFieldErrorsIntoBadRequest() throws Exception {
        BindingResult binding = new BeanPropertyBindingResult(new Object(), "createActivityRequest");
        binding.addError(new FieldError("createActivityRequest", "activityTypeId", "must not be null"));
        binding.addError(new FieldError("createActivityRequest", "pleasantness", "must be between 1 and 3"));

        // A throwaway MethodParameter is enough to construct the exception.
        MethodParameter parameter = new MethodParameter(
                GlobalExceptionHandlerTest.class.getDeclaredMethod("dummy", String.class), 0);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, binding);

        ResponseEntity<ApiResponse<Void>> response = handler.handleBeanValidation(ex);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String message = response.getBody().message();
        assertThat(message)
                .contains("activityTypeId: must not be null")
                .contains("pleasantness: must be between 1 and 3")
                .contains("; ");
        assertThat(response.getBody().success()).isFalse();
    }

    @SuppressWarnings("unused")
    private void dummy(String value) {
        // Only used to obtain a MethodParameter for MethodArgumentNotValidException.
    }
}
