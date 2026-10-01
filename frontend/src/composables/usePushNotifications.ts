import { computed, ref } from 'vue';
import { Capacitor } from '@capacitor/core';
import { api, apiErrorMessage } from '@/composables/useApi';
import {
  cancelLocalReminders,
  ensureLocalNotificationPermission,
  scheduleLocalReminders,
  scheduleTestLocalReminder,
  type ReminderSchedule
} from '@/composables/useLocalReminders';

/**
 * Notifiche periodiche configurabili ("Time to log your activities").
 *
 * Due canali di consegna, una sola configurazione:
 *  - WEB: Web Push. Il browser registra `public/sw.js`, si sottoscrive con la
 *    chiave VAPID pubblica del server e il backend manda il promemoria.
 *  - NATIVO (Capacitor): Web Push non è disponibile nella WebView, quindi i
 *    promemoria sono notifiche locali schedulate sul dispositivo
 *    (vedi `useLocalReminders`).
 *
 * In entrambi i casi le preferenze vivono sul backend: è la fonte di verità,
 * condivisa fra i dispositivi. Il dispositivo nativo non registra alcuna
 * subscription web, quindi non riceve push dal server e non c'è rischio di
 * avviso doppio.
 *
 * Contratto del backend (`/chronogram`, envelope `ApiResponse`):
 *  - `GET  /api/notifications/settings`             → NotificationSettings
 *  - `POST /api/notifications/preferences`          → NotificationPreferences
 *  - `POST /api/notifications/subscriptions`        → registra un endpoint push
 *  - `POST /api/notifications/subscriptions/delete` → dimentica un endpoint
 *  - `POST /api/notifications/test`                 → { sent, failed } (503 se
 *    il server non ha chiavi VAPID configurate)
 */

/* ───────────────────────────── contratto API ─────────────────────────────── */

/** Preferenze di notifica dell'utente autenticato. */
export interface NotificationPreferences {
  pushEnabled: boolean;
  /** Distanza fra due promemoria, in minuti. */
  intervalMinutes: number;
  /** Ora 0-23 di inizio del silenzio, oppure null. */
  quietHoursStart: number | null;
  /** Ora 0-23 di fine del silenzio, oppure null. */
  quietHoursEnd: number | null;
}

/** Risposta di `GET /api/notifications/settings`. */
export interface NotificationSettings {
  /** Falso se il server non ha chiavi VAPID: nessuna push è possibile. */
  configured: boolean;
  /** Chiave pubblica VAPID in base64url, o null se non configurata. */
  vapidPublicKey: string | null;
  preferences: NotificationPreferences;
  /** Quanti dispositivi/browser sono registrati per questo utente. */
  subscriptionCount: number;
}

/** Esito di `POST /api/notifications/test`. */
export interface TestDeliveryResult {
  sent: number;
  failed: number;
}

/**
 * Esito di `POST /api/notifications/subscriptions`.
 *
 * Il conteggio arriva già qui proprio per non dover rileggere le settings dopo
 * ogni attivazione: registrare di nuovo lo stesso browser è un upsert, quindi il
 * client non potrebbe dedurlo incrementando di uno.
 */
export interface SubscriptionRegistration {
  subscriptionCount: number;
}

/** Envelope standard delle API (`ApiResponse`). */
interface ApiEnvelope<T> {
  success?: boolean;
  message?: string;
  data?: T;
}

export const NOTIFICATION_API = {
  settings: '/api/notifications/settings',
  preferences: '/api/notifications/preferences',
  subscriptions: '/api/notifications/subscriptions',
  subscriptionsDelete: '/api/notifications/subscriptions/delete',
  test: '/api/notifications/test'
} as const;

/* ───────────────────────────── valori ammessi ────────────────────────────── */

/**
 * Cadenze proposte, in minuti. Sono un insieme chiuso e non un campo libero:
 * il backend le usa per decidere quando mandare, e una cadenza arbitraria
 * (3 minuti) sarebbe indistinguibile da uno spam.
 */
export const INTERVAL_OPTIONS: ReadonlyArray<{ minutes: number; label: string }> = [
  { minutes: 15, label: 'Every 15 minutes' },
  { minutes: 30, label: 'Every 30 minutes' },
  { minutes: 60, label: 'Every hour' },
  { minutes: 120, label: 'Every 2 hours' },
  { minutes: 240, label: 'Every 4 hours' },
  { minutes: 480, label: 'Every 8 hours' },
  { minutes: 1440, label: 'Once a day' }
];

/** Cadenza di partenza quando il backend non ne ha ancora una. */
export const DEFAULT_INTERVAL_MINUTES = 120;

/** Durata predefinita del silenzio quando l'utente indica solo l'inizio. */
const DEFAULT_QUIET_LENGTH_HOURS = 8;

/** Preferenze neutre, usate prima del caricamento e in caso di errore. */
export const DEFAULT_PREFERENCES: NotificationPreferences = {
  pushEnabled: false,
  intervalMinutes: DEFAULT_INTERVAL_MINUTES,
  quietHoursStart: null,
  quietHoursEnd: null
};

/** Stato del permesso di notifica, unificato fra web e nativo. */
export type PushPermission = 'unsupported' | 'default' | 'granted' | 'denied';

/** Esito di un'azione utente, pronto per un toast (pattern di useAdminUsers). */
export interface PushActionOutcome {
  ok: boolean;
  message: string;
}

/* ─────────────────────── utilità pure (testabili) ────────────────────────── */

/**
 * Converte una chiave VAPID base64url nel `Uint8Array` che
 * `pushManager.subscribe` pretende come `applicationServerKey`.
 *
 * Perché a mano e non con una libreria: `atob` accetta solo base64 standard e
 * con padding, mentre VAPID viaggia in base64url (`-` e `_`) e senza `=`.
 * Passare la stringa così com'è fa fallire `subscribe` con un
 * `InvalidCharacterError` che non nomina la chiave.
 */
export function urlBase64ToUint8Array(base64String: string): Uint8Array {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding).replace(/-/g, '+').replace(/_/g, '/');
  const raw = atob(base64);

  const output = new Uint8Array(raw.length);
  for (let i = 0; i < raw.length; i += 1) {
    output[i] = raw.charCodeAt(i);
  }
  return output;
}

/** Numero finito o `fallback`; usato per i campi che arrivano dalla rete. */
function asFiniteNumber(value: unknown, fallback: number): number {
  return typeof value === 'number' && Number.isFinite(value) ? Math.trunc(value) : fallback;
}

/** Ora 0-23, oppure null: qualunque altro valore è una configurazione rotta. */
function asHour(value: unknown): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  const hour = Math.trunc(value);
  return hour >= 0 && hour <= 23 ? hour : null;
}

/**
 * Normalizza le preferenze che arrivano dal server.
 *
 * Una fascia di silenzio con un solo estremo non è valutabile (vedi
 * `hasQuietHours`): si azzera tutta, così la UI mostra "No quiet hours" invece
 * di una select con un valore e l'altra vuota.
 */
export function normalizePreferences(raw: unknown): NotificationPreferences {
  if (!raw || typeof raw !== 'object') {
    return { ...DEFAULT_PREFERENCES };
  }
  const candidate = raw as Record<string, unknown>;

  const start = asHour(candidate.quietHoursStart);
  const end = asHour(candidate.quietHoursEnd);
  const quietUsable = start !== null && end !== null && start !== end;

  return {
    pushEnabled: candidate.pushEnabled === true,
    intervalMinutes: asFiniteNumber(candidate.intervalMinutes, DEFAULT_INTERVAL_MINUTES),
    quietHoursStart: quietUsable ? start : null,
    quietHoursEnd: quietUsable ? end : null
  };
}

/**
 * Estrae il corpo utile dalla risposta, che sia l'envelope `ApiResponse` o
 * l'oggetto nudo.
 *
 * La tolleranza è la stessa già adottata in `useProfile`: un envelope inatteso
 * produrrebbe qui una configurazione tutta ai valori di default, e il primo
 * salvataggio la scriverebbe sul server sovrascrivendo quella vera.
 */
export function unwrapPayload<T>(payload: unknown, marker: keyof T & string): T | null {
  if (!payload || typeof payload !== 'object') {
    return null;
  }
  const candidate = payload as Record<string, unknown>;
  if (marker in candidate) {
    return candidate as T;
  }
  if (candidate.data && typeof candidate.data === 'object') {
    const inner = candidate.data as Record<string, unknown>;
    return marker in inner ? (inner as T) : null;
  }
  return null;
}

/** Normalizza l'intera risposta di `GET settings`. */
export function normalizeSettings(payload: unknown): NotificationSettings | null {
  const body = unwrapPayload<NotificationSettings>(payload, 'preferences')
      ?? unwrapPayload<NotificationSettings>(payload, 'configured');
  if (!body) {
    return null;
  }
  const key = typeof body.vapidPublicKey === 'string' && body.vapidPublicKey.trim()
      ? body.vapidPublicKey.trim()
      : null;

  return {
    // `configured` false anche quando il flag dice true ma la chiave manca:
    // senza chiave la sottoscrizione non è possibile, e mostrare la UI attiva
    // porterebbe a un errore incomprensibile al primo tocco.
    configured: body.configured === true && key !== null,
    vapidPublicKey: key,
    preferences: normalizePreferences(body.preferences),
    subscriptionCount: Math.max(0, asFiniteNumber(body.subscriptionCount, 0))
  };
}

/** Preferenze → configurazione dei promemoria locali (stesso sottoinsieme). */
export function toReminderSchedule(preferences: NotificationPreferences): ReminderSchedule {
  return {
    intervalMinutes: preferences.intervalMinutes,
    quietHoursStart: preferences.quietHoursStart,
    quietHoursEnd: preferences.quietHoursEnd
  };
}

/**
 * Completa una fascia di silenzio indicata a metà.
 *
 * La UI espone due select indipendenti: chi scegliesse solo l'inizio si
 * ritroverebbe senza silenzio (vedi `normalizePreferences`) senza capire
 * perché. Si propone quindi una durata predefinita, che resta modificabile.
 */
export function completeQuietHours(
    start: number | null,
    end: number | null
): { quietHoursStart: number | null; quietHoursEnd: number | null } {
  const normalizedStart = asHour(start);
  if (normalizedStart === null) {
    // Nessun inizio = nessun silenzio: la fine da sola non vuol dire niente.
    return { quietHoursStart: null, quietHoursEnd: null };
  }
  const normalizedEnd = asHour(end);
  const usableEnd = normalizedEnd !== null && normalizedEnd !== normalizedStart
      ? normalizedEnd
      : (normalizedStart + DEFAULT_QUIET_LENGTH_HOURS) % 24;

  return { quietHoursStart: normalizedStart, quietHoursEnd: usableEnd };
}

/* ─────────────────── rilevamento delle capacità della piattaforma ────────── */

/** Vero dentro l'app Android/iOS impacchettata con Capacitor. */
export function isNativePlatform(): boolean {
  return Capacitor.isNativePlatform();
}

/** Vero se il browser ha tutti i pezzi del Web Push. */
export function isWebPushSupported(): boolean {
  return typeof navigator !== 'undefined'
      && 'serviceWorker' in navigator
      && typeof window !== 'undefined'
      && 'PushManager' in window
      && 'Notification' in window;
}

/** Vero su iPhone/iPad, incluso l'iPad che si dichiara "Macintosh". */
export function isIosDevice(): boolean {
  if (typeof navigator === 'undefined') {
    return false;
  }
  const ua = navigator.userAgent || '';
  const iPadOnDesktopUa = /Macintosh/.test(ua) && (navigator.maxTouchPoints ?? 0) > 1;
  return /iPad|iPhone|iPod/.test(ua) || iPadOnDesktopUa;
}

/** Vero se la pagina gira come app installata (PWA) e non in una scheda. */
export function isStandaloneDisplay(): boolean {
  if (typeof window === 'undefined') {
    return false;
  }
  const iosStandalone = (navigator as unknown as { standalone?: boolean }).standalone === true;
  const displayMode = typeof window.matchMedia === 'function'
      && window.matchMedia('(display-mode: standalone)').matches;
  return iosStandalone || displayMode;
}

/**
 * Vero su Safari iOS aperto come sito e non installato sulla schermata Home.
 *
 * Non è una sfumatura: iOS espone `PushManager` ma rifiuta la sottoscrizione
 * finché il sito non è stato aggiunto alla schermata Home. Senza questo
 * controllo l'utente vedrebbe un toggle che si spegne da solo e un errore
 * generico; con esso la UI può dire l'unica cosa utile ("Add to Home Screen").
 */
export function isIosBrowserNotInstalled(): boolean {
  return !isNativePlatform() && isIosDevice() && !isStandaloneDisplay();
}

/**
 * URL del service worker, coerente col base path della build.
 *
 * `import.meta.env.BASE_URL` è `/` per la build della WebView e `/chronogram/`
 * per `npm run build:web`: un `/sw.js` scritto a mano darebbe 404 (o, peggio,
 * l'index.html della SPA con content-type sbagliato) sulla versione web.
 */
export function serviceWorkerScope(): string {
  const base = import.meta.env.BASE_URL || '/';
  return base.endsWith('/') ? base : `${base}/`;
}

/** Percorso del file del worker dentro lo scope. */
export function serviceWorkerUrl(): string {
  return `${serviceWorkerScope()}sw.js`;
}

/* ──────────────────────────────── composable ─────────────────────────────── */

export function usePushNotifications() {
  const loading = ref(false);
  const loadError = ref<string | null>(null);
  /** Un'operazione (attivazione, salvataggio, prova) è in corso. */
  const busy = ref(false);

  const configured = ref(false);
  const vapidPublicKey = ref<string | null>(null);
  const preferences = ref<NotificationPreferences>({ ...DEFAULT_PREFERENCES });
  const subscriptionCount = ref(0);
  const permission = ref<PushPermission>('unsupported');

  const nativePlatform = isNativePlatform();
  const webPushSupported = isWebPushSupported();
  const iosNotInstalled = isIosBrowserNotInstalled();

  /**
   * Vero se su questa piattaforma i promemoria possono funzionare.
   *
   * Sul nativo basta il plugin (nessuna chiave VAPID in gioco: la consegna è
   * locale); sul web servono sia il supporto del browser sia le chiavi sul
   * server. Da qui dipende se il toggle è utilizzabile o solo spiegato.
   */
  const canUsePush = computed(() => nativePlatform
      ? true
      : webPushSupported && !iosNotInstalled && configured.value);

  const pushEnabled = computed(() => preferences.value.pushEnabled);

  /** Legge lo stato del permesso senza chiederlo all'utente. */
  async function refreshPermission(): Promise<void> {
    if (nativePlatform) {
      try {
        const { LocalNotifications } = await import('@capacitor/local-notifications');
        const status = await LocalNotifications.checkPermissions();
        permission.value = status.display === 'granted'
            ? 'granted'
            : status.display === 'denied' ? 'denied' : 'default';
      } catch (error) {
        permission.value = 'unsupported';
      }
      return;
    }

    if (!webPushSupported) {
      permission.value = 'unsupported';
      return;
    }
    const current = Notification.permission;
    permission.value = current === 'granted' || current === 'denied' ? current : 'default';
  }

  function applySettings(settings: NotificationSettings): void {
    configured.value = settings.configured;
    vapidPublicKey.value = settings.vapidPublicKey;
    preferences.value = settings.preferences;
    subscriptionCount.value = settings.subscriptionCount;
  }

  async function fetchSettings(): Promise<NotificationSettings> {
    const { data } = await api.get<ApiEnvelope<NotificationSettings>>(NOTIFICATION_API.settings);
    const settings = normalizeSettings(data);
    if (!settings) {
      throw new Error('The server returned an unexpected notification settings format.');
    }
    return settings;
  }

  /** Carica la configurazione. Gli errori restano in `loadError` (con retry). */
  async function loadSettings(): Promise<void> {
    loading.value = true;
    loadError.value = null;
    try {
      applySettings(await fetchSettings());
      await refreshPermission();
    } catch (error) {
      loadError.value = apiErrorMessage(error, 'Could not load your notification settings.');
    } finally {
      loading.value = false;
    }
  }

  /**
   * Riallinea lo stato al server senza mostrare il caricamento.
   *
   * Serve dopo la disattivazione per il solo `subscriptionCount`:
   * `POST subscriptions/delete` è idempotente e non riferisce il nuovo totale,
   * e decrementare a mano mostrerebbe un dispositivo in meno di quelli veri
   * quando il server aveva già dimenticato l'endpoint (potato dopo un 410).
   * Un errore qui non ha conseguenze: il conteggio resta quello di prima.
   */
  async function refreshSilently(): Promise<void> {
    try {
      applySettings(await fetchSettings());
    } catch (error) {
      /* la parte che contava (le preferenze) è già stata salvata */
    }
  }

  /** Invia le preferenze e riparte dalla risposta del server. */
  async function postPreferences(next: NotificationPreferences): Promise<NotificationPreferences> {
    const { data } = await api.post<ApiEnvelope<NotificationPreferences>>(
        NOTIFICATION_API.preferences,
        next
    );
    const returned = unwrapPayload<NotificationPreferences>(data, 'pushEnabled');
    // Se la risposta non riporta le preferenze si tiene quanto inviato: la UI
    // deve riflettere ciò che è stato accettato, non tornare ai default.
    preferences.value = returned ? normalizePreferences(returned) : { ...next };
    return preferences.value;
  }

  /* ------------------------------ web push ------------------------------- */

  /**
   * Registra il worker e attende che sia attivo.
   *
   * L'attesa su `navigator.serviceWorker.ready` non è pleonastica: appena dopo
   * `register()` il worker può essere ancora in "installing", e
   * `registration.pushManager` su una registrazione non attiva fallisce.
   */
  async function registerServiceWorker(): Promise<ServiceWorkerRegistration> {
    await navigator.serviceWorker.register(serviceWorkerUrl(), { scope: serviceWorkerScope() });
    return navigator.serviceWorker.ready;
  }

  /**
   * Vero se la sottoscrizione esistente è legata alla chiave VAPID corrente.
   *
   * Se il server ha rigenerato le chiavi, la vecchia sottoscrizione resta
   * valida per il browser ma il server non riuscirà più a firmare per essa: le
   * push verrebbero rifiutate dal push service senza alcun segnale nella UI.
   */
  function matchesCurrentKey(subscription: PushSubscription, key: string): boolean {
    const applied = subscription.options?.applicationServerKey;
    if (!applied) {
      return false;
    }
    const expected = urlBase64ToUint8Array(key);
    const actual = new Uint8Array(applied as ArrayBuffer);
    if (actual.length !== expected.length) {
      return false;
    }
    return actual.every((byte, index) => byte === expected[index]);
  }

  /**
   * Sottoscrive (o riusa) il browser e comunica l'endpoint al backend.
   *
   * Restituisce il numero di dispositivi registrati riferito dal server, o null
   * se la risposta non lo porta.
   */
  async function subscribeBrowser(key: string): Promise<number | null> {
    const registration = await registerServiceWorker();

    let subscription = await registration.pushManager.getSubscription();
    if (subscription && !matchesCurrentKey(subscription, key)) {
      // Chiave del server cambiata: la vecchia sottoscrizione va dismessa, sia
      // localmente sia lato server, altrimenti resta una riga morta.
      const staleEndpoint = subscription.endpoint;
      await subscription.unsubscribe().catch(() => undefined);
      await api.post(NOTIFICATION_API.subscriptionsDelete, { endpoint: staleEndpoint })
          .catch(() => undefined);
      subscription = null;
    }

    if (!subscription) {
      subscription = await registration.pushManager.subscribe({
        // Obbligatorio su Chrome: ogni push deve produrre una notifica visibile.
        userVisibleOnly: true,
        applicationServerKey: urlBase64ToUint8Array(key) as BufferSource
      });
    }

    const json = subscription.toJSON();
    const keys = json.keys ?? {};
    if (!json.endpoint || !keys.p256dh || !keys.auth) {
      throw new Error('The browser returned an incomplete push subscription.');
    }

    const { data } = await api.post<ApiEnvelope<SubscriptionRegistration>>(
        NOTIFICATION_API.subscriptions,
        {
          endpoint: json.endpoint,
          p256dh: keys.p256dh,
          auth: keys.auth,
          // Serve solo a far riconoscere il dispositivo nella lista lato server.
          userAgent: typeof navigator !== 'undefined' ? navigator.userAgent : ''
        }
    );
    const registered = unwrapPayload<SubscriptionRegistration>(data, 'subscriptionCount');
    return registered ? Math.max(0, asFiniteNumber(registered.subscriptionCount, 0)) : null;
  }

  /** Dimentica la sottoscrizione di questo browser, localmente e sul server. */
  async function unsubscribeBrowser(): Promise<void> {
    if (!webPushSupported) {
      return;
    }
    const registration = await navigator.serviceWorker.getRegistration(serviceWorkerScope());
    const subscription = await registration?.pushManager.getSubscription();
    if (!subscription) {
      return;
    }

    const endpoint = subscription.endpoint;
    // Prima il server: l'endpoint serve per identificare la riga, e una
    // `unsubscribe()` riuscita renderebbe impossibile ripulirla più tardi.
    // L'errore non blocca la disattivazione: `pushEnabled=false` ferma già
    // l'invio, la riga orfana verrà scartata dal server al primo 410.
    await api.post(NOTIFICATION_API.subscriptionsDelete, { endpoint }).catch(() => undefined);
    await subscription.unsubscribe().catch(() => undefined);
  }

  /* ------------------------------- azioni -------------------------------- */

  /**
   * Attiva i promemoria.
   *
   * Ogni motivo di rifiuto ha un messaggio suo: "non funziona" senza spiegazione
   * su una feature che dipende da un permesso di sistema è un vicolo cieco.
   */
  async function enablePush(): Promise<PushActionOutcome> {
    if (busy.value) {
      return { ok: false, message: 'Please wait for the current change to finish.' };
    }
    busy.value = true;
    try {
      if (nativePlatform) {
        const granted = await ensureLocalNotificationPermission();
        await refreshPermission();
        if (!granted) {
          return {
            ok: false,
            message: 'Notifications are blocked for Chronogram. Enable them in the system settings.'
          };
        }

        const next = await postPreferences({ ...preferences.value, pushEnabled: true });
        const scheduled = await scheduleLocalReminders(toReminderSchedule(next));
        return scheduled > 0
            ? { ok: true, message: 'Reminders are on.' }
            : { ok: false, message: 'Reminders saved, but nothing could be scheduled on this device.' };
      }

      if (!webPushSupported) {
        return { ok: false, message: 'This browser does not support push notifications.' };
      }
      if (iosNotInstalled) {
        return {
          ok: false,
          message: 'On iPhone and iPad, add Chronogram to the Home Screen first.'
        };
      }
      if (!configured.value || !vapidPublicKey.value) {
        return { ok: false, message: 'Push notifications are not configured on the server yet.' };
      }

      const result = await Notification.requestPermission();
      permission.value = result === 'granted' || result === 'denied' ? result : 'default';
      if (result !== 'granted') {
        return {
          ok: false,
          message: result === 'denied'
              ? 'Notifications are blocked for this site. Allow them in your browser settings.'
              : 'Notification permission was not granted.'
        };
      }

      const registeredDevices = await subscribeBrowser(vapidPublicKey.value);
      await postPreferences({ ...preferences.value, pushEnabled: true });
      // Il conteggio viene dalla risposta della registrazione: il backend lo
      // include proprio per evitare una rilettura delle settings sul percorso
      // che l'utente sta aspettando.
      if (registeredDevices !== null) {
        subscriptionCount.value = registeredDevices;
      }
      return { ok: true, message: 'Reminders are on.' };
    } catch (error) {
      return { ok: false, message: apiErrorMessage(error, 'Could not enable reminders.') };
    } finally {
      busy.value = false;
    }
  }

  /** Disattiva i promemoria su questo dispositivo e lato server. */
  async function disablePush(): Promise<PushActionOutcome> {
    if (busy.value) {
      return { ok: false, message: 'Please wait for the current change to finish.' };
    }
    busy.value = true;
    try {
      if (nativePlatform) {
        await cancelLocalReminders();
      } else {
        await unsubscribeBrowser();
      }

      await postPreferences({ ...preferences.value, pushEnabled: false });
      if (!nativePlatform) {
        await refreshSilently();
      }
      return { ok: true, message: 'Reminders are off.' };
    } catch (error) {
      return { ok: false, message: apiErrorMessage(error, 'Could not turn reminders off.') };
    } finally {
      busy.value = false;
    }
  }

  /** Scorciatoia per il toggle della vista. */
  async function setPushEnabled(enabled: boolean): Promise<PushActionOutcome> {
    return enabled ? enablePush() : disablePush();
  }

  /**
   * Salva cadenza e fascia di silenzio.
   *
   * Sul nativo il salvataggio comporta un ri-scheduling completo: gli istanti
   * già in coda seguono la cadenza vecchia e mescolarli con la nuova
   * produrrebbe promemoria a intervalli casuali.
   */
  async function savePreferences(
      intervalMinutes: number,
      quietHoursStart: number | null,
      quietHoursEnd: number | null
  ): Promise<PushActionOutcome> {
    if (busy.value) {
      return { ok: false, message: 'Please wait for the current change to finish.' };
    }
    busy.value = true;
    try {
      const quiet = completeQuietHours(quietHoursStart, quietHoursEnd);
      const next = await postPreferences({
        pushEnabled: preferences.value.pushEnabled,
        intervalMinutes,
        ...quiet
      });

      if (nativePlatform && next.pushEnabled) {
        await scheduleLocalReminders(toReminderSchedule(next));
      }
      return { ok: true, message: 'Reminder settings saved.' };
    } catch (error) {
      return { ok: false, message: apiErrorMessage(error, 'Could not save your reminder settings.') };
    } finally {
      busy.value = false;
    }
  }

  /** Manda un promemoria di prova, per verificare che arrivi davvero. */
  async function sendTest(): Promise<PushActionOutcome> {
    if (busy.value) {
      return { ok: false, message: 'Please wait for the current change to finish.' };
    }
    busy.value = true;
    try {
      if (nativePlatform) {
        const scheduled = await scheduleTestLocalReminder();
        return scheduled
            ? { ok: true, message: 'A test reminder will appear in a few seconds.' }
            : { ok: false, message: 'Notifications are blocked for Chronogram on this device.' };
      }

      const { data } = await api.post<ApiEnvelope<TestDeliveryResult>>(NOTIFICATION_API.test);
      const result = unwrapPayload<TestDeliveryResult>(data, 'sent');
      const sent = result ? asFiniteNumber(result.sent, 0) : 0;
      const failed = result ? asFiniteNumber(result.failed, 0) : 0;

      if (sent === 0) {
        return {
          ok: false,
          message: failed > 0
              ? 'No device could be reached. Try turning reminders off and on again.'
              : 'No registered device to notify yet.'
        };
      }
      return {
        ok: true,
        message: failed > 0
            ? `Test sent to ${sent} device(s); ${failed} could not be reached.`
            : `Test sent to ${sent} device(s).`
      };
    } catch (error) {
      return { ok: false, message: apiErrorMessage(error, 'Could not send the test notification.') };
    } finally {
      busy.value = false;
    }
  }

  return {
    // stato
    loading,
    loadError,
    busy,
    configured,
    vapidPublicKey,
    preferences,
    subscriptionCount,
    permission,
    // capacità della piattaforma (costanti per la durata della sessione)
    nativePlatform,
    webPushSupported,
    iosNotInstalled,
    canUsePush,
    pushEnabled,
    // azioni
    loadSettings,
    refreshPermission,
    enablePush,
    disablePush,
    setPushEnabled,
    savePreferences,
    sendTest
  };
}

/**
 * Ri-sincronizza i promemoria locali all'avvio dell'app nativa.
 *
 * Serve perché le notifiche locali sono istanti concreti in un orizzonte di
 * poche ore (vedi `useLocalReminders`): senza un rifornimento periodico la coda
 * si esaurisce e i promemoria smettono semplicemente di arrivare. L'avvio
 * dell'app è il momento naturale per rifornirla — non serve nessun servizio in
 * background.
 *
 * È volutamente silenziosa e non blocca il bootstrap: se la rete non c'è, i
 * promemoria già in coda restano validi e si riprova al prossimo avvio.
 * Sul web è un no-op immediato (lì consegna il backend).
 */
export async function syncLocalRemindersFromBackend(): Promise<void> {
  if (!isNativePlatform()) {
    return;
  }
  try {
    const { data } = await api.get<ApiEnvelope<NotificationSettings>>(NOTIFICATION_API.settings);
    const settings = normalizeSettings(data);
    if (!settings) {
      return;
    }

    if (!settings.preferences.pushEnabled) {
      // L'utente può aver disattivato i promemoria da un altro dispositivo: la
      // coda locale di questo va comunque svuotata.
      await cancelLocalReminders();
      return;
    }
    await scheduleLocalReminders(toReminderSchedule(settings.preferences));
  } catch (error) {
    /* avvio a freddo senza rete o endpoint non ancora disponibile: si riprova
       al prossimo lancio, la coda esistente resta valida */
  }
}
