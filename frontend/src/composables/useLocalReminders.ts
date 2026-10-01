import { Capacitor } from '@capacitor/core';

/**
 * Promemoria periodici sul dispositivo NATIVO (Capacitor/Android).
 *
 * Perché esiste: sul web il promemoria arriva come Web Push, cioè è il backend
 * a decidere quando svegliare il browser. Nella WebView di Capacitor il Web
 * Push non c'è (nessun `PushManager` utilizzabile, nessun service worker con
 * uno scope servibile), quindi l'unico modo di avvisare l'utente con l'app
 * chiusa è schedulare le notifiche sul dispositivo con
 * `@capacitor/local-notifications`.
 *
 * Conseguenza architetturale: la CONFIGURAZIONE resta sul backend (è la fonte
 * di verità, condivisa fra i dispositivi e sopravvive alla reinstallazione),
 * ma la CONSEGNA è locale. Il device nativo non registra alcuna subscription
 * web, quindi il backend non gli manda nulla: non serve nessun flag lato
 * server per evitare il doppio avviso.
 *
 * Il plugin viene importato SOLO dinamicamente e solo nei rami nativi
 * (`await import(...)`): così il bundle web non lo carica e la build web non
 * dipende dal runtime nativo.
 */

/** Configurazione dei promemoria, sottoinsieme delle preferenze del backend. */
export interface ReminderSchedule {
  /** Distanza fra due promemoria, in minuti (15, 30, 60, 120, 240, 480, 1440). */
  intervalMinutes: number;
  /** Ora (0-23) in cui inizia il silenzio, oppure null se non impostato. */
  quietHoursStart: number | null;
  /** Ora (0-23) in cui finisce il silenzio, oppure null se non impostato. */
  quietHoursEnd: number | null;
}

/** Parametri del batch; estratti per poter essere stretti nei test. */
export interface ReminderBatchOptions {
  /** Quanto avanti guardare, in ore. */
  horizonHours?: number;
  /** Tetto al numero di notifiche pending (Android ha un limite di sistema). */
  maxCount?: number;
}

/**
 * Intervallo fra due promemoria dopo il quale ne accettiamo un altro.
 *
 * Perché un orizzonte di 48 ore: le notifiche locali sono istanti concreti
 * (`schedule.at`), non una regola ricorrente, quindi vanno riempite in
 * anticipo. Due giorni coprono comodamente il tempo fra due aperture dell'app
 * (a ogni avvio si ri-sincronizza) senza saturare la coda del sistema.
 */
const DEFAULT_HORIZON_HOURS = 48;

/**
 * Massimo di notifiche schedulate insieme. Android tronca silenziosamente le
 * code molto lunghe (il limite pratico è nell'ordine del centinaio per app):
 * 60 è abbastanza per 15 ore a cadenza di 15 minuti e resta ben sotto la
 * soglia.
 */
const DEFAULT_MAX_COUNT = 60;

/**
 * Primo id del range riservato ai promemoria.
 *
 * Tutti gli id usati da questo modulo stanno in [9000, 9199]: la cancellazione
 * tocca solo questa finestra, così un'eventuale notifica locale futura di
 * un'altra feature non viene spazzata via da un ri-scheduling.
 */
export const REMINDER_ID_BASE = 9000;

/** Ultimo id del range riservato (compreso). */
export const REMINDER_ID_MAX = REMINDER_ID_BASE + 199;

/** Id fisso della notifica di prova: nel range riservato ma fuori dal batch. */
const TEST_NOTIFICATION_ID = REMINDER_ID_BASE + 199;

/** Testi del promemoria; uguali a quelli che il backend manda via Web Push. */
const REMINDER_TITLE = 'Chronogram';
const REMINDER_BODY = 'Time to log your activities';

/** Canale Android dedicato: permette all'utente di silenziare solo questi. */
const CHANNEL_ID = 'chronogram-reminders';

const MINUTE_MS = 60_000;

/* ─────────────────────────── logica pura (testabile) ─────────────────────── */

/**
 * Normalizza un'ora a un intero 0-23, oppure null.
 *
 * Il backend può mandare `null`, ma anche un numero fuori scala se la
 * configurazione è stata scritta a mano: un 24 lasciato passare renderebbe la
 * fascia di silenzio impossibile da valutare.
 */
export function normalizeHour(value: unknown): number | null {
  if (typeof value !== 'number' || !Number.isFinite(value)) {
    return null;
  }
  const hour = Math.trunc(value);
  return hour >= 0 && hour <= 23 ? hour : null;
}

/**
 * Vero se la fascia di silenzio è effettivamente configurata.
 *
 * `start === end` viene considerato "nessun silenzio" e non "silenzio per 24
 * ore": è quasi certamente un errore di configurazione, e interpretarlo alla
 * lettera spegnerebbe la feature senza che l'utente capisca perché.
 */
export function hasQuietHours(schedule: ReminderSchedule): boolean {
  const start = normalizeHour(schedule.quietHoursStart);
  const end = normalizeHour(schedule.quietHoursEnd);
  return start !== null && end !== null && start !== end;
}

/**
 * Vero se l'istante cade nella fascia di silenzio.
 *
 * Gestisce il wrap-around di mezzanotte (22 → 8 significa "dalle 22:00 alle
 * 07:59"), che è il caso d'uso normale e quello che si sbaglia più facilmente.
 */
export function isWithinQuietHours(date: Date, schedule: ReminderSchedule): boolean {
  if (!hasQuietHours(schedule)) {
    return false;
  }
  const start = normalizeHour(schedule.quietHoursStart) as number;
  const end = normalizeHour(schedule.quietHoursEnd) as number;
  const hour = date.getHours();

  return start < end
      ? hour >= start && hour < end   // fascia nello stesso giorno (es. 1 → 6)
      : hour >= start || hour < end;  // fascia a cavallo di mezzanotte (22 → 8)
}

/**
 * Primo istante NON silenzioso a partire da `date` (incluso).
 *
 * Invece di scartare un promemoria che cade di notte lo si sposta alla fine
 * della fascia: con una cadenza giornaliera l'orario dell'utente potrebbe
 * cadere sempre dentro il silenzio e lo scarto secco lo lascerebbe senza
 * alcun promemoria. Chi ha cadenze brevi vede invece un solo avviso al
 * risveglio, perché i successivi vengono filtrati dalla distanza minima
 * (vedi `computeReminderTimes`).
 */
export function firstMomentOutsideQuietHours(date: Date, schedule: ReminderSchedule): Date {
  if (!isWithinQuietHours(date, schedule)) {
    return new Date(date.getTime());
  }
  const end = normalizeHour(schedule.quietHoursEnd) as number;

  const moment = new Date(date.getTime());
  moment.setMinutes(0, 0, 0);
  // Avanza di un'ora alla volta: `setHours` su una data locale attraversa
  // correttamente i cambi di ora legale, che un'aritmetica sui millisecondi
  // sbaglierebbe di 60 minuti due volte l'anno.
  while (moment.getHours() !== end || moment.getTime() <= date.getTime()) {
    moment.setHours(moment.getHours() + 1);
  }
  return moment;
}

/**
 * Istanti concreti dei prossimi promemoria.
 *
 * Il ragionamento: si parte da `from` e si procede a passi di
 * `intervalMinutes`. Ogni candidato dentro la fascia di silenzio viene
 * spostato alla fine della fascia; un candidato che finisce troppo vicino a
 * quello già accettato viene scartato, così lo spostamento non produce dieci
 * notifiche tutte alle 08:00. La distanza minima è l'intervallo stesso: è la
 * cadenza che l'utente ha scelto, quindi è anche la più piccola che si aspetta
 * di vedere.
 *
 * È una funzione pura (nessun accesso a `Date.now()`, nessun plugin) proprio
 * perché è la parte che vale la pena testare: gli errori qui sono promemoria
 * di notte o promemoria che non arrivano mai.
 */
export function computeReminderTimes(
    from: Date,
    schedule: ReminderSchedule,
    options: ReminderBatchOptions = {}
): Date[] {
  const interval = Math.trunc(schedule.intervalMinutes);
  if (!Number.isFinite(interval) || interval <= 0) {
    return [];
  }

  const horizonHours = options.horizonHours ?? DEFAULT_HORIZON_HOURS;
  const maxCount = options.maxCount ?? DEFAULT_MAX_COUNT;
  if (maxCount <= 0 || horizonHours <= 0) {
    return [];
  }

  const deadline = from.getTime() + horizonHours * 60 * MINUTE_MS;
  const minGapMs = interval * MINUTE_MS;

  const times: Date[] = [];
  let candidate = from.getTime() + minGapMs;
  let lastAccepted = Number.NEGATIVE_INFINITY;

  // Limite di sicurezza sui giri: con intervalli piccoli e orizzonti lunghi il
  // numero di candidati è noto in anticipo, ma un intervallo assurdo non deve
  // poter trasformare il calcolo in un ciclo infinito.
  const maxIterations = Math.ceil((horizonHours * 60) / interval) + 2;

  for (let i = 0; i < maxIterations && times.length < maxCount; i += 1) {
    if (candidate > deadline) {
      break;
    }

    const adjusted = firstMomentOutsideQuietHours(new Date(candidate), schedule);
    const adjustedMs = adjusted.getTime();

    if (adjustedMs <= deadline && adjustedMs - lastAccepted >= minGapMs) {
      times.push(adjusted);
      lastAccepted = adjustedMs;
    }

    candidate += minGapMs;
  }

  return times;
}

/* ─────────────────────────── ponte verso il plugin ───────────────────────── */

/**
 * Carica il plugin solo quando serve davvero.
 *
 * L'import dinamico non è un'ottimizzazione: `@capacitor/local-notifications`
 * importato staticamente entrerebbe nel bundle web, dove il bridge nativo non
 * esiste e ogni chiamata fallirebbe a runtime.
 */
async function loadPlugin() {
  const module = await import('@capacitor/local-notifications');
  return module.LocalNotifications;
}

/** Vero solo dentro l'app nativa; sul web ogni funzione qui sotto è un no-op. */
export function isNativeRemindersPlatform(): boolean {
  return Capacitor.isNativePlatform();
}

/**
 * Chiede (una volta) il permesso di mostrare notifiche.
 *
 * Su Android 13+ è un permesso runtime come la fotocamera: senza questo la
 * `schedule()` riesce ma nulla compare mai a schermo, che è il modo più
 * silenzioso possibile di rompere la feature.
 */
export async function ensureLocalNotificationPermission(): Promise<boolean> {
  if (!isNativeRemindersPlatform()) {
    return false;
  }
  const plugin = await loadPlugin();

  const current = await plugin.checkPermissions();
  if (current.display === 'granted') {
    return true;
  }
  if (current.display === 'denied') {
    // Una seconda richiesta non riapre il dialogo: solo le impostazioni di
    // sistema possono riabilitarlo, e la UI lo spiega.
    return false;
  }

  const requested = await plugin.requestPermissions();
  return requested.display === 'granted';
}

/**
 * Crea il canale Android dei promemoria, se il sistema lo richiede.
 *
 * Da API 26 la priorità/suono di una notifica si decide sul canale, non sulla
 * singola notifica. Un canale dedicato permette all'utente di silenziare i
 * promemoria dalle impostazioni di sistema senza spegnere tutta l'app.
 * Fallimenti ignorati: su iOS `createChannel` non esiste e non è un errore.
 */
async function ensureChannel(): Promise<void> {
  if (Capacitor.getPlatform() !== 'android') {
    return;
  }
  try {
    const plugin = await loadPlugin();
    await plugin.createChannel({
      id: CHANNEL_ID,
      name: 'Activity reminders',
      description: 'Periodic reminders to log your activities',
      importance: 3,
      visibility: 1
    });
  } catch (error) {
    /* canale non creabile: le notifiche useranno quello di default */
  }
}

/**
 * Cancella i soli promemoria schedulati da noi.
 *
 * Si legge la coda e si filtra sul range riservato invece di chiamare un
 * "cancella tutto": il plugin è condiviso con qualunque altra notifica locale
 * l'app aggiunga in futuro.
 */
export async function cancelLocalReminders(): Promise<void> {
  if (!isNativeRemindersPlatform()) {
    return;
  }
  const plugin = await loadPlugin();
  const pending = await plugin.getPending();

  const ours = (pending.notifications ?? [])
      .filter((item) => item.id >= REMINDER_ID_BASE && item.id <= REMINDER_ID_MAX)
      .map((item) => ({ id: item.id }));

  if (ours.length > 0) {
    await plugin.cancel({ notifications: ours });
  }
}

/**
 * Ri-schedula da zero il batch dei prossimi promemoria.
 *
 * Sempre "cancella e riscrivi" e mai un aggiornamento incrementale: gli istanti
 * dipendono dall'ora in cui si calcola, quindi tenere in vita quelli vecchi
 * significherebbe mescolare due cadenze diverse. Restituisce quante notifiche
 * sono state messe in coda (0 se il permesso manca).
 */
export async function scheduleLocalReminders(
    schedule: ReminderSchedule,
    now: Date = new Date(),
    options: ReminderBatchOptions = {}
): Promise<number> {
  if (!isNativeRemindersPlatform()) {
    return 0;
  }

  const granted = await ensureLocalNotificationPermission();
  if (!granted) {
    return 0;
  }

  await ensureChannel();
  await cancelLocalReminders();

  const times = computeReminderTimes(now, schedule, options);
  if (times.length === 0) {
    return 0;
  }

  const plugin = await loadPlugin();
  await plugin.schedule({
    notifications: times.map((at, index) => ({
      id: REMINDER_ID_BASE + index,
      title: REMINDER_TITLE,
      body: REMINDER_BODY,
      channelId: CHANNEL_ID,
      // Gli allarmi esatti su Android 12+ aprono la schermata di sistema
      // "Sveglie e promemoria" per farsi autorizzare: sproporzionato per un
      // promemoria che tollera qualche minuto di ritardo.
      isExactNotification: false,
      schedule: { at, allowWhileIdle: true }
    }))
  });

  return times.length;
}

/**
 * Notifica di prova sul dispositivo.
 *
 * Non è immediata ma fra qualche secondo, di proposito: una notifica mostrata
 * mentre l'app è in primo piano su Android non compare in tendina, e l'utente
 * concluderebbe che la prova è fallita. Qualche secondo bastano per mettere
 * l'app in background.
 */
export async function scheduleTestLocalReminder(delaySeconds = 5): Promise<boolean> {
  if (!isNativeRemindersPlatform()) {
    return false;
  }

  const granted = await ensureLocalNotificationPermission();
  if (!granted) {
    return false;
  }

  await ensureChannel();
  const plugin = await loadPlugin();
  await plugin.schedule({
    notifications: [{
      id: TEST_NOTIFICATION_ID,
      title: REMINDER_TITLE,
      body: 'This is a test reminder. Notifications are working.',
      channelId: CHANNEL_ID,
      isExactNotification: false,
      schedule: { at: new Date(Date.now() + delaySeconds * 1000), allowWhileIdle: true }
    }]
  });

  return true;
}
