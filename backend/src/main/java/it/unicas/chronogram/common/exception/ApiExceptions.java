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

    /**
     * Una dipendenza esterna ha fallito: status non-2xx, errore di rete/timeout
     * o risposta inutilizzabile → HTTP 502.
     * <p>
     * Distinta da {@link ServiceException} (guasto interno, 500) perche' il
     * problema non e' nel nostro processo: il client non ha nulla da correggere
     * nella richiesta e deve solo riprovare piu' tardi. Il messaggio passato al
     * costruttore viene restituito cosi' com'e' al client: deve essere rivolto
     * all'utente finale e non contenere dettagli su provider, chiavi o modelli.
     * Il dettaglio tecnico lo logga chi lancia l'eccezione, dove il contesto c'e'.
     */
    public static class UpstreamServiceException extends RuntimeException {
        public UpstreamServiceException(String message) {
            super(message);
        }

        public UpstreamServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Funzionalita' opzionale non utilizzabile in questo ambiente perche' non
     * configurata (es. credenziali assenti) → HTTP 503.
     * <p>
     * Vale la stessa regola sul messaggio di {@link UpstreamServiceException}:
     * cosa manchi nella configurazione resta nei log del server.
     */
    public static class FeatureUnavailableException extends RuntimeException {
        public FeatureUnavailableException(String message) {
            super(message);
        }
    }
}
