package it.unicas.chronogram.common.exception;

/**
 * Domain exceptions used across the application. Grouped here for convenience;
 * each is mapped to an HTTP status by {@code GlobalExceptionHandler}.
 */
public final class ApiExceptions {

    private ApiExceptions() {
    }

    /** Invalid input / business-rule violation → HTTP 400. */
    public static class ValidationException extends RuntimeException {
        public ValidationException(String message) {
            super(message);
        }
    }

    /** Authentication failure (bad credentials, locked/inactive account) → HTTP 401. */
    public static class AuthenticationFailedException extends RuntimeException {
        public AuthenticationFailedException(String message) {
            super(message);
        }
    }

    /** Attempt to register an already-existing email → HTTP 409. */
    public static class EmailAlreadyExistsException extends RuntimeException {
        public EmailAlreadyExistsException(String message) {
            super(message);
        }
    }

    /** Requested resource not found or not owned by the caller → HTTP 404. */
    public static class ResourceNotFoundException extends RuntimeException {
        public ResourceNotFoundException(String message) {
            super(message);
        }
    }

    /** Unexpected internal failure (DB, mail, external API) → HTTP 500. */
    public static class ServiceException extends RuntimeException {
        public ServiceException(String message) {
            super(message);
        }

        public ServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
