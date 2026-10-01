/* eslint-env serviceworker */
/*
 * Service worker di Chronogram — SOLO notifiche push.
 *
 * Nessuna logica di cache/offline di proposito: un service worker che
 * intercetta `fetch` cambia il modo in cui l'app si aggiorna (bundle vecchi
 * serviti dalla cache dopo un deploy) e non serve a nulla per le notifiche.
 * Qui si gestiscono solo i due eventi che il browser consegna al worker anche
 * a scheda chiusa: 'push' e 'notificationclick'.
 *
 * Il file sta in `public/` e Vite lo copia *as-is* nella dist: resta JS
 * vanilla, senza import, senza sintassi che richieda una build.
 *
 * ATTENZIONE AL BASE PATH: l'app web è servita sotto un sotto-percorso
 * (`npm run build:web` usa `--base=/chronogram/`), mentre la build per la
 * WebView Capacitor usa `/`. Nessun path assoluto è quindi scrivibile a mano:
 * si risolve tutto contro `self.registration.scope`, che è l'URL assoluto
 * dello scope con cui il worker è stato registrato (es.
 * `https://host/chronogram/`) e vale per entrambe le build.
 */

/** Testi di riserva se il payload manca o non è JSON (push "vuoto"). */
const FALLBACK_TITLE = 'Chronogram';
const FALLBACK_BODY = 'Time to log your activities';

/** Pagina da aprire quando il payload non indica una destinazione. */
const FALLBACK_PATH = '/home';

/**
 * Icone: devono esistere davvero in `public/`, altrimenti Android mostra un
 * quadrato vuoto senza alcun errore visibile. `chronogram_web_logo.png` è il
 * logo già usato come favicon in index.html.
 */
const ICON_PATH = 'chronogram_web_logo.png';
const BADGE_PATH = 'favicon.png';

/**
 * Risolve un percorso relativo allo scope del worker.
 *
 * Un `/home` che arriva dal server è pensato come rotta dell'app, non come
 * path assoluto dell'origine: sotto `/chronogram/` aprirebbe `https://host/home`,
 * cioè fuori dall'app. Si toglie quindi lo slash iniziale e si risolve contro
 * lo scope. Un URL già assoluto (http/https) viene rispettato così com'è.
 */
function resolveInScope(value, fallback) {
  const candidate = typeof value === 'string' && value.trim() ? value.trim() : fallback;
  try {
    if (/^https?:\/\//i.test(candidate)) {
      return candidate;
    }
    return new URL(candidate.replace(/^\/+/, ''), self.registration.scope).href;
  } catch (error) {
    return self.registration.scope;
  }
}

/**
 * Attivazione immediata: senza `skipWaiting`/`clients.claim` un worker
 * aggiornato resta in "waiting" finché tutte le schede dell'app non vengono
 * chiuse, e nel frattempo le push continuano a essere gestite dalla versione
 * precedente.
 */
self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  event.waitUntil(self.clients.claim());
});

/**
 * Arrivo di una push dal backend.
 *
 * Il payload atteso è `{ title, body, tag, url }`. Il parsing è difensivo
 * perché la notifica DEVE essere mostrata comunque: su Chrome, una push
 * ricevuta con `userVisibleOnly: true` che non produce alcuna notifica fa
 * comparire il messaggio di sistema "Questo sito è stato aggiornato in
 * background" e, se si ripete, il browser revoca la sottoscrizione.
 */
self.addEventListener('push', (event) => {
  let payload = {};
  if (event.data) {
    try {
      payload = event.data.json() || {};
    } catch (error) {
      // Non-JSON (o testo semplice): si usa il corpo come testo, se c'è.
      try {
        payload = { body: event.data.text() };
      } catch (innerError) {
        payload = {};
      }
    }
  }

  const title = typeof payload.title === 'string' && payload.title.trim()
      ? payload.title.trim()
      : FALLBACK_TITLE;
  const body = typeof payload.body === 'string' && payload.body.trim()
      ? payload.body.trim()
      : FALLBACK_BODY;
  // `tag` fa collassare i promemoria: chi apre l'app dopo qualche ora trova un
  // solo avviso e non dieci identici in coda.
  const tag = typeof payload.tag === 'string' && payload.tag.trim()
      ? payload.tag.trim()
      : 'chronogram-reminder';

  event.waitUntil(self.registration.showNotification(title, {
    body,
    tag,
    icon: resolveInScope(ICON_PATH, ICON_PATH),
    badge: resolveInScope(BADGE_PATH, BADGE_PATH),
    // L'URL viaggia in `data`: è l'unico modo per ritrovarlo in
    // 'notificationclick', dove il payload della push non esiste più.
    data: { url: resolveInScope(payload.url, FALLBACK_PATH) }
  }));
});

/**
 * Tocco sulla notifica.
 *
 * Si riusa una finestra dell'app già aperta invece di aprirne una nuova: su
 * desktop l'utente ha spesso Chronogram in una scheda, e una seconda copia
 * significherebbe rifare login-state e perdere il contesto. Il confronto usa
 * il prefisso dello scope, non l'uguaglianza dell'URL, perché la scheda
 * aperta può essere su una rotta qualsiasi dell'app.
 */
self.addEventListener('notificationclick', (event) => {
  event.notification.close();

  const targetUrl = (event.notification.data && event.notification.data.url)
      || resolveInScope(FALLBACK_PATH, FALLBACK_PATH);
  const scope = self.registration.scope;

  event.waitUntil((async () => {
    const clientList = await self.clients.matchAll({
      type: 'window',
      includeUncontrolled: true
    });

    for (const client of clientList) {
      if (client.url.startsWith(scope)) {
        // `navigate` può non essere disponibile (o fallire per policy): il
        // focus è la parte che conta, la navigazione è un miglioramento.
        if (client.url !== targetUrl && typeof client.navigate === 'function') {
          try {
            await client.navigate(targetUrl);
          } catch (error) {
            /* si resta dov'era: meglio l'app in primo piano che nulla */
          }
        }
        return client.focus();
      }
    }

    return self.clients.openWindow(targetUrl);
  })());
});
