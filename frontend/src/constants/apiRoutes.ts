/**
 * Rotte API pubbliche di Chronogram.
 *
 * Sono le rotte che NON richiedono un token JWT: l'interceptor axios non deve
 * aggiungere l'header `Authorization` per queste richieste.
 *
 * Unica fonte di verità: mantenere questo elenco allineato al backend
 * (context-path `/chronogram`, rotte `/api/...`). Evitare di duplicare le
 * stringhe negli interceptor o nei componenti.
 */
export const PUBLIC_API_PATHS = [
  '/api/auth/login',
  '/api/auth/register',
  '/api/auth/request-reset',
  '/api/auth/reset-password',
] as const;

export type PublicApiPath = (typeof PUBLIC_API_PATHS)[number];

/**
 * Restituisce true se l'URL della richiesta corrisponde a una rotta pubblica
 * (quindi da escludere dall'aggiunta del token).
 */
export function isPublicApiPath(url: string | undefined): boolean {
  if (!url) {
    return false;
  }
  return PUBLIC_API_PATHS.some((publicPath) => url.includes(publicPath));
}