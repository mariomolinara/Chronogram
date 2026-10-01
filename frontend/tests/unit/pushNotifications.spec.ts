import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'

// `vi.hoisted`: la factory di `vi.mock` viene issata in cima al file e non
// vedrebbe delle const dichiarate qui sotto (stesso schema di adminUsers.spec).
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/composables/useApi', async () => {
  const actual = await vi.importActual<typeof import('@/composables/useApi')>('@/composables/useApi')
  return { ...actual, api: { get, post } }
})

import {
  completeQuietHours,
  DEFAULT_INTERVAL_MINUTES,
  INTERVAL_OPTIONS,
  NOTIFICATION_API,
  normalizePreferences,
  normalizeSettings,
  serviceWorkerScope,
  serviceWorkerUrl,
  urlBase64ToUint8Array,
  usePushNotifications,
  type NotificationSettings
} from '@/composables/usePushNotifications'

/** Chiave VAPID pubblica di esempio: base64url, senza padding, 65 byte. */
const VAPID_KEY = 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U'

/** Risposta axios nell'envelope `ApiResponse` del backend. */
const envelope = <T>(data: T, message = 'ok') => ({ data: { success: true, message, data } })

function settings(overrides: Partial<NotificationSettings> = {}): NotificationSettings {
  return {
    configured: true,
    vapidPublicKey: VAPID_KEY,
    preferences: {
      pushEnabled: false,
      intervalMinutes: 120,
      quietHoursStart: 22,
      quietHoursEnd: 8
    },
    subscriptionCount: 0,
    ...overrides
  }
}

/* ────────────────────── ambiente Web Push simulato ──────────────────────── */

/**
 * jsdom non ha né service worker né `PushManager` né `Notification`: senza
 * questi il composable prende (correttamente) il ramo "browser non
 * supportato", quindi il percorso felice va simulato a mano.
 *
 * Le capacità vengono lette alla creazione del composable, perciò l'ambiente
 * va installato PRIMA di chiamare `usePushNotifications()`.
 */
interface FakeWebPushEnv {
  subscribe: ReturnType<typeof vi.fn>
  register: ReturnType<typeof vi.fn>
  requestPermission: ReturnType<typeof vi.fn>
  setExistingSubscription: (subscription: unknown) => void
}

const originalDescriptors: Array<{ target: object; key: string; descriptor?: PropertyDescriptor }> = []

function stub(target: object, key: string, value: unknown): void {
  originalDescriptors.push({ target, key, descriptor: Object.getOwnPropertyDescriptor(target, key) })
  Object.defineProperty(target, key, { value, configurable: true, writable: true })
}

function restoreStubs(): void {
  while (originalDescriptors.length > 0) {
    const entry = originalDescriptors.pop()!
    if (entry.descriptor) {
      Object.defineProperty(entry.target, entry.key, entry.descriptor)
    } else {
      delete (entry.target as Record<string, unknown>)[entry.key]
    }
  }
}

function fakeSubscription(endpoint: string, key = VAPID_KEY) {
  const unsubscribe = vi.fn().mockResolvedValue(true)
  return {
    endpoint,
    unsubscribe,
    options: { applicationServerKey: urlBase64ToUint8Array(key).buffer },
    toJSON: () => ({ endpoint, keys: { p256dh: 'p256dh-value', auth: 'auth-value' } })
  }
}

function installWebPushEnv(permission: NotificationPermission = 'default'): FakeWebPushEnv {
  let existing: unknown = null

  const subscribe = vi.fn().mockImplementation(async () => fakeSubscription('https://push.example/new'))
  const requestPermission = vi.fn().mockResolvedValue('granted')

  const registration = {
    pushManager: {
      getSubscription: vi.fn().mockImplementation(async () => existing),
      subscribe
    }
  }

  const register = vi.fn().mockResolvedValue(registration)
  const serviceWorker = {
    register,
    ready: Promise.resolve(registration),
    getRegistration: vi.fn().mockResolvedValue(registration)
  }

  stub(navigator, 'serviceWorker', serviceWorker)
  stub(window, 'PushManager', class PushManager {})
  stub(window, 'Notification', { permission, requestPermission })
  // `Notification` è letto anche come globale nudo dal composable.
  stub(globalThis, 'Notification', { permission, requestPermission })

  return {
    subscribe,
    register,
    requestPermission,
    setExistingSubscription: (subscription) => { existing = subscription }
  }
}

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

afterEach(() => {
  restoreStubs()
})

/* ─────────────────────────── utilità pure ────────────────────────────────── */

describe('urlBase64ToUint8Array', () => {
  /**
   * VAPID viaggia in base64URL senza padding, mentre `atob` accetta solo
   * base64 standard con padding: passare la stringa così com'è fa fallire
   * `pushManager.subscribe` con un errore che non nomina la chiave.
   */
  test('decodifica la chiave VAPID nei 65 byte attesi', () => {
    const bytes = urlBase64ToUint8Array(VAPID_KEY)

    expect(bytes).toBeInstanceOf(Uint8Array)
    expect(bytes).toHaveLength(65)
    // Le chiavi P-256 non compresse iniziano sempre con 0x04.
    expect(bytes[0]).toBe(0x04)
  })

  test('traduce l alfabeto URL-safe (- e _) e aggiunge il padding mancante', () => {
    // "-_-_" in base64url == "+/+/" in base64 standard: 4 caratteri -> 3 byte.
    const bytes = urlBase64ToUint8Array('-_-_')

    expect(Array.from(bytes)).toEqual([0xfb, 0xff, 0xbf])
  })

  test('accetta anche una stringa che già richiede due caratteri di padding', () => {
    // "QQ" -> "QQ==" -> 0x41
    expect(Array.from(urlBase64ToUint8Array('QQ'))).toEqual([0x41])
  })
})

describe('normalizePreferences', () => {
  test('una fascia di silenzio con un solo estremo viene azzerata del tutto', () => {
    // Con un solo estremo la fascia non è valutabile: meglio "nessun silenzio"
    // che una select con un valore e l'altra vuota.
    expect(normalizePreferences({ pushEnabled: true, intervalMinutes: 60, quietHoursStart: 22 }))
        .toEqual({ pushEnabled: true, intervalMinutes: 60, quietHoursStart: null, quietHoursEnd: null })
  })

  test('start uguale a end non spegne i promemoria per 24 ore', () => {
    const result = normalizePreferences({ intervalMinutes: 60, quietHoursStart: 9, quietHoursEnd: 9 })
    expect(result.quietHoursStart).toBeNull()
    expect(result.quietHoursEnd).toBeNull()
  })

  test('valori mancanti o non numerici ripiegano sui default', () => {
    expect(normalizePreferences(null)).toEqual({
      pushEnabled: false,
      intervalMinutes: DEFAULT_INTERVAL_MINUTES,
      quietHoursStart: null,
      quietHoursEnd: null
    })
    expect(normalizePreferences({ intervalMinutes: 'tanti' }).intervalMinutes)
        .toBe(DEFAULT_INTERVAL_MINUTES)
  })
})

describe('normalizeSettings', () => {
  test('accetta sia l envelope ApiResponse sia l oggetto nudo', () => {
    const fromEnvelope = normalizeSettings({ success: true, message: 'ok', data: settings() })
    const bare = normalizeSettings(settings())

    expect(fromEnvelope).toEqual(bare)
    expect(fromEnvelope?.preferences.intervalMinutes).toBe(120)
  })

  /**
   * `configured: true` senza chiave è una configurazione impossibile: lasciarla
   * passare mostrerebbe la UI attiva e un errore incomprensibile al primo tocco.
   */
  test('senza chiave VAPID la configurazione non è considerata pronta', () => {
    expect(normalizeSettings(settings({ vapidPublicKey: null }))?.configured).toBe(false)
    expect(normalizeSettings(settings({ vapidPublicKey: '   ' }))?.configured).toBe(false)
  })

  test('una risposta irriconoscibile restituisce null invece di valori inventati', () => {
    expect(normalizeSettings(null)).toBeNull()
    expect(normalizeSettings({ unexpected: true })).toBeNull()
    expect(normalizeSettings('<html>502 Bad Gateway</html>')).toBeNull()
  })
})

describe('completeQuietHours', () => {
  test('solo l inizio: la fine viene proposta a +8 ore', () => {
    expect(completeQuietHours(22, null)).toEqual({ quietHoursStart: 22, quietHoursEnd: 6 })
  })

  test('nessun inizio: la fine da sola non vuol dire niente', () => {
    expect(completeQuietHours(null, 8)).toEqual({ quietHoursStart: null, quietHoursEnd: null })
  })

  test('estremi coincidenti: la fine viene spostata invece di mutare tutto il giorno', () => {
    expect(completeQuietHours(9, 9)).toEqual({ quietHoursStart: 9, quietHoursEnd: 17 })
  })

  test('una fascia valida resta com è', () => {
    expect(completeQuietHours(23, 7)).toEqual({ quietHoursStart: 23, quietHoursEnd: 7 })
  })
})

describe('service worker e base path', () => {
  /**
   * L'app web è servita sotto `/chronogram/` (`npm run build:web`): un
   * `/sw.js` scritto a mano darebbe 404 lì, e nei test `BASE_URL` è `/`.
   */
  test('lo scope e l URL del worker derivano da BASE_URL', () => {
    expect(serviceWorkerScope().endsWith('/')).toBe(true)
    expect(serviceWorkerUrl()).toBe(`${serviceWorkerScope()}sw.js`)
    expect(serviceWorkerUrl()).toMatch(/sw\.js$/)
  })
})

describe('INTERVAL_OPTIONS', () => {
  test('le cadenze proposte sono quelle del contratto, in ordine crescente', () => {
    expect(INTERVAL_OPTIONS.map((option) => option.minutes))
        .toEqual([15, 30, 60, 120, 240, 480, 1440])
  })
})

/* ────────────────────────── composable: caricamento ─────────────────────── */

describe('usePushNotifications - caricamento', () => {
  test('pubblica preferenze, chiave e conteggio dispositivi', async () => {
    get.mockResolvedValue(envelope(settings({ subscriptionCount: 2 })))
    const push = usePushNotifications()

    await push.loadSettings()

    expect(get).toHaveBeenCalledWith(NOTIFICATION_API.settings)
    expect(push.loadError.value).toBeNull()
    expect(push.loading.value).toBe(false)
    expect(push.configured.value).toBe(true)
    expect(push.subscriptionCount.value).toBe(2)
    expect(push.preferences.value.quietHoursStart).toBe(22)
  })

  test('espone il messaggio del server e permette il retry', async () => {
    get.mockRejectedValueOnce({ response: { data: { message: 'Notifications are disabled.' } } })
    const push = usePushNotifications()

    await push.loadSettings()
    expect(push.loadError.value).toBe('Notifications are disabled.')

    get.mockResolvedValueOnce(envelope(settings()))
    await push.loadSettings()
    expect(push.loadError.value).toBeNull()
    expect(push.configured.value).toBe(true)
  })

  /**
   * Senza questo controllo una risposta inattesa darebbe una configurazione
   * tutta ai default dall'aria innocente, e il primo salvataggio la
   * scriverebbe sul server sovrascrivendo quella vera.
   */
  test('una risposta di forma inattesa è un errore, non dei default silenziosi', async () => {
    get.mockResolvedValue({ data: { success: true, message: 'ok' } })
    const push = usePushNotifications()

    await push.loadSettings()

    expect(push.loadError.value).toMatch(/unexpected/i)
    expect(push.configured.value).toBe(false)
  })
})

/* ───────────────────── composable: capacità e rifiuti ───────────────────── */

describe('usePushNotifications - piattaforma non adatta', () => {
  test('in jsdom (nessun PushManager) il web push è dichiarato non supportato', async () => {
    get.mockResolvedValue(envelope(settings()))
    const push = usePushNotifications()
    await push.loadSettings()

    expect(push.webPushSupported).toBe(false)
    expect(push.canUsePush.value).toBe(false)
    expect(push.permission.value).toBe('unsupported')

    const outcome = await push.enablePush()
    expect(outcome.ok).toBe(false)
    expect(outcome.message).toMatch(/does not support/i)
    // Nessuna preferenza salvata: attivare qualcosa che non può arrivare
    // sarebbe una bugia nella UI.
    expect(post).not.toHaveBeenCalled()
  })

  test('senza chiavi sul server il toggle non è utilizzabile e lo spiega', async () => {
    installWebPushEnv()
    get.mockResolvedValue(envelope(settings({ configured: false, vapidPublicKey: null })))
    const push = usePushNotifications()
    await push.loadSettings()

    expect(push.webPushSupported).toBe(true)
    expect(push.canUsePush.value).toBe(false)

    const outcome = await push.enablePush()
    expect(outcome.ok).toBe(false)
    expect(outcome.message).toMatch(/not configured on the server/i)
    expect(post).not.toHaveBeenCalled()
  })

  test('permesso negato dal browser: nessuna sottoscrizione, messaggio azionabile', async () => {
    const env = installWebPushEnv()
    env.requestPermission.mockResolvedValue('denied')
    get.mockResolvedValue(envelope(settings()))
    const push = usePushNotifications()
    await push.loadSettings()

    const outcome = await push.enablePush()

    expect(outcome.ok).toBe(false)
    expect(outcome.message).toMatch(/blocked for this site/i)
    expect(push.permission.value).toBe('denied')
    expect(env.subscribe).not.toHaveBeenCalled()
    expect(post).not.toHaveBeenCalled()
  })
})

/* ──────────────────── composable: attivazione web push ──────────────────── */

describe('usePushNotifications - attivazione via Web Push', () => {
  test('registra il worker, si sottoscrive e comunica endpoint e chiavi', async () => {
    const env = installWebPushEnv()
    get.mockResolvedValue(envelope(settings({ subscriptionCount: 0 })))
    // Ogni endpoint risponde secondo il proprio contratto: la registrazione
    // riferisce il numero di dispositivi, le preferenze si rieccheggiano.
    post.mockImplementation(async (url: string) => url === NOTIFICATION_API.subscriptions
        ? envelope({ subscriptionCount: 1 })
        : envelope({ pushEnabled: true, intervalMinutes: 120, quietHoursStart: 22, quietHoursEnd: 8 }))

    const push = usePushNotifications()
    await push.loadSettings()
    const outcome = await push.enablePush()

    expect(outcome.ok).toBe(true)
    expect(env.register).toHaveBeenCalledWith(serviceWorkerUrl(), { scope: serviceWorkerScope() })
    expect(env.subscribe).toHaveBeenCalledWith(expect.objectContaining({ userVisibleOnly: true }))

    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.subscriptions, expect.objectContaining({
      endpoint: 'https://push.example/new',
      p256dh: 'p256dh-value',
      auth: 'auth-value'
    }))
    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.preferences, expect.objectContaining({
      pushEnabled: true
    }))
    expect(push.pushEnabled.value).toBe(true)
    // Il conteggio arriva dalla risposta della registrazione, non da una
    // seconda GET: registrare di nuovo lo stesso browser è un upsert, quindi
    // incrementarlo a mano mostrerebbe un dispositivo di troppo.
    expect(push.subscriptionCount.value).toBe(1)
    expect(get).toHaveBeenCalledTimes(1)
  })

  test('riusa la sottoscrizione esistente se è legata alla chiave corrente', async () => {
    const env = installWebPushEnv()
    env.setExistingSubscription(fakeSubscription('https://push.example/existing'))
    get.mockResolvedValue(envelope(settings()))
    post.mockResolvedValue(envelope({
      pushEnabled: true, intervalMinutes: 120, quietHoursStart: 22, quietHoursEnd: 8
    }))

    const push = usePushNotifications()
    await push.loadSettings()
    await push.enablePush()

    expect(env.subscribe).not.toHaveBeenCalled()
    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.subscriptions, expect.objectContaining({
      endpoint: 'https://push.example/existing'
    }))
  })

  /**
   * Se il server ha rigenerato le chiavi VAPID, la vecchia sottoscrizione resta
   * valida per il browser ma il server non può più firmare per essa: le push
   * verrebbero rifiutate dal push service senza alcun segnale nella UI.
   */
  test('una sottoscrizione legata a una chiave vecchia viene sostituita e ripulita', async () => {
    const env = installWebPushEnv()
    const stale = fakeSubscription('https://push.example/stale', 'QUJDRA')
    env.setExistingSubscription(stale)
    get.mockResolvedValue(envelope(settings()))
    post.mockResolvedValue(envelope({
      pushEnabled: true, intervalMinutes: 120, quietHoursStart: 22, quietHoursEnd: 8
    }))

    const push = usePushNotifications()
    await push.loadSettings()
    await push.enablePush()

    expect(stale.unsubscribe).toHaveBeenCalled()
    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.subscriptionsDelete, {
      endpoint: 'https://push.example/stale'
    })
    expect(env.subscribe).toHaveBeenCalled()
    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.subscriptions, expect.objectContaining({
      endpoint: 'https://push.example/new'
    }))
  })

  test('disattivare dimentica l endpoint sul server e nel browser', async () => {
    const env = installWebPushEnv('granted')
    const existing = fakeSubscription('https://push.example/existing')
    env.setExistingSubscription(existing)
    get.mockResolvedValueOnce(envelope(settings({
      subscriptionCount: 1,
      preferences: { pushEnabled: true, intervalMinutes: 60, quietHoursStart: null, quietHoursEnd: null }
    })))
    // Rilettura dopo la disattivazione: il server non conta più questo browser.
    get.mockResolvedValue(envelope(settings({
      subscriptionCount: 0,
      preferences: { pushEnabled: false, intervalMinutes: 60, quietHoursStart: null, quietHoursEnd: null }
    })))
    post.mockResolvedValue(envelope({
      pushEnabled: false, intervalMinutes: 60, quietHoursStart: null, quietHoursEnd: null
    }))

    const push = usePushNotifications()
    await push.loadSettings()
    expect(push.pushEnabled.value).toBe(true)

    const outcome = await push.disablePush()

    expect(outcome.ok).toBe(true)
    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.subscriptionsDelete, {
      endpoint: 'https://push.example/existing'
    })
    expect(existing.unsubscribe).toHaveBeenCalled()
    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.preferences, expect.objectContaining({
      pushEnabled: false
    }))
    expect(push.pushEnabled.value).toBe(false)
    expect(push.subscriptionCount.value).toBe(0)
  })
})

/* ─────────────────── composable: preferenze e notifica di prova ─────────── */

describe('usePushNotifications - salvataggio preferenze', () => {
  test('conserva pushEnabled e completa una fascia indicata a metà', async () => {
    get.mockResolvedValue(envelope(settings({ preferences: {
      pushEnabled: true, intervalMinutes: 120, quietHoursStart: null, quietHoursEnd: null
    } })))
    post.mockResolvedValue(envelope({
      pushEnabled: true, intervalMinutes: 30, quietHoursStart: 23, quietHoursEnd: 7
    }))

    const push = usePushNotifications()
    await push.loadSettings()
    const outcome = await push.savePreferences(30, 23, null)

    expect(outcome.ok).toBe(true)
    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.preferences, {
      pushEnabled: true,
      intervalMinutes: 30,
      quietHoursStart: 23,
      quietHoursEnd: 7
    })
    expect(push.preferences.value.intervalMinutes).toBe(30)
  })

  test('riporta il messaggio del server quando il salvataggio viene rifiutato', async () => {
    get.mockResolvedValue(envelope(settings()))
    post.mockRejectedValue({ response: { data: { message: 'Unsupported interval: 7' } } })

    const push = usePushNotifications()
    await push.loadSettings()
    const outcome = await push.savePreferences(7, null, null)

    expect(outcome.ok).toBe(false)
    expect(outcome.message).toBe('Unsupported interval: 7')
    // Le preferenze in memoria restano quelle salvate: la vista si riallinea a
    // queste, così la select non mostra una scelta che il server ha rifiutato.
    expect(push.preferences.value.intervalMinutes).toBe(120)
    expect(push.busy.value).toBe(false)
  })

  /** Se la risposta non riporta le preferenze si tiene quanto inviato. */
  test('una risposta senza corpo non riporta la UI ai default', async () => {
    get.mockResolvedValue(envelope(settings()))
    post.mockResolvedValue({ data: { success: true, message: 'Saved.' } })

    const push = usePushNotifications()
    await push.loadSettings()
    await push.savePreferences(480, null, null)

    expect(push.preferences.value.intervalMinutes).toBe(480)
  })
})

describe('usePushNotifications - notifica di prova', () => {
  test('riferisce quanti dispositivi sono stati raggiunti', async () => {
    get.mockResolvedValue(envelope(settings()))
    post.mockResolvedValue(envelope({ sent: 2, failed: 0 }))

    const push = usePushNotifications()
    await push.loadSettings()
    const outcome = await push.sendTest()

    expect(post).toHaveBeenCalledWith(NOTIFICATION_API.test)
    expect(outcome.ok).toBe(true)
    expect(outcome.message).toMatch(/2 device/)
  })

  test('nessun dispositivo raggiunto non è un successo', async () => {
    get.mockResolvedValue(envelope(settings()))
    post.mockResolvedValue(envelope({ sent: 0, failed: 1 }))

    const push = usePushNotifications()
    await push.loadSettings()
    const outcome = await push.sendTest()

    expect(outcome.ok).toBe(false)
    expect(outcome.message).toMatch(/No device could be reached/i)
  })

  /** Il contratto prevede 503 quando il server non ha chiavi VAPID. */
  test('il 503 del server arriva all utente con il suo messaggio', async () => {
    get.mockResolvedValue(envelope(settings()))
    post.mockRejectedValue({
      response: { status: 503, data: { message: 'Push notifications are not configured.' } }
    })

    const push = usePushNotifications()
    await push.loadSettings()
    const outcome = await push.sendTest()

    expect(outcome.ok).toBe(false)
    expect(outcome.message).toBe('Push notifications are not configured.')
  })
})
