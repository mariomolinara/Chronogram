/// <reference types="vite/client" />

interface ImportMetaEnv {
  /**
   * Base URL of the backend API. Must include the backend context-path
   * `/chronogram` (e.g. `http://localhost:8080/chronogram`), because API
   * calls use paths like `/api/auth/login` without that prefix.
   */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
