/**
 * Google reCAPTCHA v3 (invisibile) per la registrazione.
 *
 * v3 non mostra sfide: assegna un punteggio alla richiesta e la decisione la
 * prende il backend (`RecaptchaService`), che possiede la secret key. Qui c'è
 * solo la site key pubblica (`VITE_RECAPTCHA_SITE_KEY`): senza di essa la
 * funzione restituisce `null` e la protezione è spenta — coerente col backend,
 * che senza secret non verifica. Le due chiavi vanno configurate insieme.
 *
 * Lo script di Google è caricato al primo uso e una volta sola: caricarlo in
 * `index.html` lo farebbe pesare su ogni pagina, mentre serve solo alla
 * registrazione.
 */

const SCRIPT_ID = 'recaptcha-v3-script';

const siteKey: string | undefined = import.meta.env.VITE_RECAPTCHA_SITE_KEY;

/** True quando la protezione è configurata lato client. */
export function isRecaptchaEnabled(): boolean {
  return !!siteKey;
}

let loadPromise: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (loadPromise) {
    return loadPromise;
  }
  loadPromise = new Promise<void>((resolve, reject) => {
    if (document.getElementById(SCRIPT_ID)) {
      resolve();
      return;
    }
    const script = document.createElement('script');
    script.id = SCRIPT_ID;
    script.src = `https://www.google.com/recaptcha/api.js?render=${siteKey}`;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      // Lasciare la promise fallita in cache renderebbe il fallimento
      // permanente: al prossimo tentativo si riprova a caricare.
      loadPromise = null;
      reject(new Error('Could not load the anti-bot verification. Please check your connection and retry.'));
    };
    document.head.appendChild(script);
  });
  return loadPromise;
}

/**
 * Ottiene un token v3 per l'azione indicata, o `null` se reCAPTCHA non è
 * configurato. Il token vive pochi minuti e vale per una sola verifica:
 * va richiesto al momento del submit, non al mount della pagina.
 *
 * @throws Error se lo script non si carica o l'esecuzione fallisce: il
 *   chiamante lo mostra come qualunque altro errore di rete.
 */
export async function getRecaptchaToken(action: string): Promise<string | null> {
  if (!siteKey) {
    return null;
  }
  await loadScript();
  const grecaptcha = (window as unknown as {
    grecaptcha?: {
      ready: (cb: () => void) => void;
      execute: (key: string, opts: { action: string }) => Promise<string>;
    };
  }).grecaptcha;
  if (!grecaptcha) {
    throw new Error('Anti-bot verification is unavailable. Please retry.');
  }
  await new Promise<void>((resolve) => grecaptcha.ready(resolve));
  return grecaptcha.execute(siteKey, { action });
}
