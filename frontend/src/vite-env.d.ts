/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Base URL of the backend API. Must include the backend context-path
   * `/chronogram` (e.g. `http://localhost:8080/chronogram`), because API
   * calls use paths like `/api/auth/login` without that prefix.
   */
  readonly VITE_API_BASE_URL?: string;
  /**
   * OAuth client ID (WEB) for "Sign in with Google". Empty/absent hides the
   * Google button entirely. The backend must list the same ID in
   * GOOGLE_CLIENT_IDS or every token will be rejected.
   */
  readonly VITE_GOOGLE_CLIENT_ID?: string;
  /**
   * reCAPTCHA v3 SITE key used on the registration form. Empty/absent disables
   * the client-side check; pair it with RECAPTCHA_SECRET_KEY on the backend,
   * which is where the actual enforcement happens.
   */
  readonly VITE_RECAPTCHA_SITE_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
