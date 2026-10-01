/**
 * Notifiche periodiche configurabili (pagina Notifications).
 *
 * Le API sono stubbate con `cy.intercept`: si verifica il comportamento del
 * frontend (stati, banner, salvataggio al cambio) e il contratto con cui chiama
 * il backend (path e corpo), non il backend.
 *
 * Il Web Push va stubbato a sua volta: in Chrome headless
 * `Notification.requestPermission()` non apre nulla e
 * `pushManager.subscribe()` non ha un push service a cui parlare. Si sostituisce
 * quindi la catena `Notification` / `serviceWorker` in `onBeforeLoad`, cioè
 * prima che il bundle venga eseguito e legga le capacità della piattaforma.
 */

interface StubPreferences {
  pushEnabled: boolean
  intervalMinutes: number
  quietHoursStart: number | null
  quietHoursEnd: number | null
}

interface StubSettings {
  configured: boolean
  vapidPublicKey: string | null
  preferences: StubPreferences
  subscriptionCount: number
}

/** Chiave VAPID pubblica di esempio (base64url, 65 byte decodificati). */
const VAPID_KEY = 'BEl62iUYgUivxIkv69yViEuiBIa-Ib9-SkvMeAtA3LFgDzkrxZJjSgSnfckjBJuBkr3qBUYIHBQFLXYp5Nksh8U'

const PUSH_ENDPOINT = 'https://push.example/subscription/abc'

const settings = (overrides: Partial<StubSettings> = {}): StubSettings => ({
  configured: true,
  vapidPublicKey: VAPID_KEY,
  preferences: {
    pushEnabled: false,
    intervalMinutes: 120,
    quietHoursStart: null,
    quietHoursEnd: null
  },
  subscriptionCount: 0,
  ...overrides
})

const envelope = (data: unknown, message = 'ok') => ({ success: true, message, data })

function stubLogin(): void {
  cy.intercept('POST', '**/api/auth/login', {
    statusCode: 200,
    body: {
      success: true,
      message: 'Login successful!',
      username: 'mario.rossi@unicas.it',
      token: 'stub-token',
      role: 'USER',
      mustChangePassword: false
    }
  }).as('login')
}

/** GET settings, con la possibilità di cambiare risposta fra due richieste. */
function stubSettings(first: StubSettings, later: StubSettings = first): void {
  let calls = 0
  cy.intercept('GET', '**/api/notifications/settings', (req) => {
    calls += 1
    req.reply(envelope(calls === 1 ? first : later))
  }).as('settings')
}

/**
 * Sostituisce la catena Web Push del browser con una che risponde sempre "sì".
 *
 * Va applicata in `onBeforeLoad`: il composable legge `PushManager` e
 * `Notification` alla sua creazione, quindi uno stub installato dopo il
 * caricamento arriverebbe troppo tardi.
 */
function stubWebPush(win: Cypress.AUTWindow, permission: NotificationPermission = 'default'): void {
  const subscription = {
    endpoint: PUSH_ENDPOINT,
    options: { applicationServerKey: null },
    unsubscribe: () => Promise.resolve(true),
    toJSON: () => ({ endpoint: PUSH_ENDPOINT, keys: { p256dh: 'p256dh-stub', auth: 'auth-stub' } })
  }

  const registration = {
    pushManager: {
      getSubscription: () => Promise.resolve(null),
      subscribe: () => Promise.resolve(subscription)
    }
  }

  Object.defineProperty(win.navigator, 'serviceWorker', {
    configurable: true,
    value: {
      register: () => Promise.resolve(registration),
      ready: Promise.resolve(registration),
      getRegistration: () => Promise.resolve(registration)
    }
  })

  const notification = {
    permission,
    requestPermission: () => Promise.resolve('granted' as NotificationPermission)
  }
  Object.defineProperty(win, 'Notification', { configurable: true, value: notification })
  Object.defineProperty(win, 'PushManager', { configurable: true, value: class PushManager {} })
}

/**
 * Chiave con cui `@capacitor/preferences` salva su localStorage nel web.
 *
 * Serve a spegnere il popup di avvio dell'About (`AboutStartupModal`): è una
 * `ion-modal` con `backdrop-dismiss="false"` presentata a ogni caricamento
 * della pagina, e finché è aperta copre l'intera app — il form di login
 * risulta coperto o disabilitato e nessun test riesce nemmeno ad autenticarsi.
 */
const ABOUT_POPUP_KEY = 'CapacitorStorage.aboutPopupHidden'

/** Preparazione comune a ogni caricamento di pagina. */
function beforeLoad(win: Cypress.AUTWindow, permission: NotificationPermission = 'default'): void {
  win.localStorage.setItem(ABOUT_POPUP_KEY, 'true')
  stubWebPush(win, permission)
}

/** Entra come utente normale passando dal form di login vero. */
function signIn(permission: NotificationPermission = 'default'): void {
  cy.visit('/login', { onBeforeLoad: (win) => beforeLoad(win, permission) })
  cy.get('ion-input').eq(0).find('input').type('mario.rossi@unicas.it')
  cy.get('ion-input').eq(1).find('input').type('Password1!')
  cy.contains('ion-button', 'Login').click()
  cy.wait('@login')
  cy.location('pathname').should('eq', '/home')
}

/** Apre la pagina delle notifiche con il Web Push stubbato. */
function openNotifications(permission: NotificationPermission = 'default'): void {
  cy.visit('/notifications', { onBeforeLoad: (win) => beforeLoad(win, permission) })
  cy.wait('@settings')
}

/*
 * Le etichette delle `ion-select` vivono nello shadow DOM del componente, dove
 * `cy.contains` non arriva: i controlli si raggiungono per posizione. L'ordine
 * è quello della pagina (cadenza, inizio e fine della fascia di silenzio) e la
 * terza esiste solo quando la fascia è attiva.
 */
const SELECT_INTERVAL = 0
const SELECT_QUIET_FROM = 1

/** Il testo del toast Ionic vive nello shadow DOM: si legge dalla proprietà. */
function expectToast(text: string): void {
  cy.get('ion-toast').should(($toast) => {
    const message = ($toast[0] as HTMLElement & { message?: string }).message ?? ''
    expect(message).to.contain(text)
  })
}

describe('Notifications - promemoria periodici', () => {
  beforeEach(() => {
    stubLogin()
  })

  it('con i promemoria spenti mostra solo il toggle, senza cadenza né prova', () => {
    stubSettings(settings())
    signIn()
    openNotifications()

    cy.contains('h2', 'Push notifications').should('be.visible')
    cy.get('ion-toggle').should('have.prop', 'checked', false)
    // Niente toggle finti Email/SMS: il backend non li espone.
    cy.contains('Email Notifications').should('not.exist')
    cy.contains('SMS Notifications').should('not.exist')
    // I controlli di dettaglio compaiono solo quando i promemoria sono attivi.
    cy.contains('ion-button', 'Send test notification').should('not.exist')
    cy.get('ion-select').should('not.exist')
  })

  it('se il server non ha le chiavi VAPID lo spiega e disabilita il toggle', () => {
    stubSettings(settings({ configured: false, vapidPublicKey: null }))
    signIn()
    openNotifications()

    cy.contains('.notice-title', 'Not available yet').should('be.visible')
    cy.get('ion-toggle').should('have.prop', 'disabled', true)
  })

  it('un errore di caricamento è spiegato e riprovabile', () => {
    let calls = 0
    cy.intercept('GET', '**/api/notifications/settings', (req) => {
      calls += 1
      if (calls === 1) {
        req.reply({ statusCode: 500, body: { success: false, message: 'Notification service is down.' } })
      } else {
        req.reply(envelope(settings()))
      }
    }).as('settings')

    signIn()
    openNotifications()

    cy.contains('.state-block--error', 'Notification service is down.').should('be.visible')
    cy.contains('ion-button', 'Retry').click()
    cy.wait('@settings')
    cy.contains('h2', 'Push notifications').should('be.visible')
  })

  it('attivare registra la sottoscrizione e poi salva le preferenze', () => {
    stubSettings(
        settings(),
        settings({ subscriptionCount: 1, preferences: {
          pushEnabled: true, intervalMinutes: 120, quietHoursStart: null, quietHoursEnd: null
        } })
    )
    // Il contratto fa riferire al server il nuovo numero di dispositivi, così
    // la schermata lo aggiorna senza rileggere le settings.
    cy.intercept('POST', '**/api/notifications/subscriptions', {
      statusCode: 200,
      body: envelope({ subscriptionCount: 1 }, 'Push subscription registered successfully')
    }).as('subscribe')
    cy.intercept('POST', '**/api/notifications/preferences', (req) => {
      req.reply(envelope(req.body))
    }).as('savePrefs')

    signIn()
    openNotifications()

    cy.get('ion-toggle').click()

    cy.wait('@subscribe').its('request.body').should('deep.include', {
      endpoint: PUSH_ENDPOINT,
      p256dh: 'p256dh-stub',
      auth: 'auth-stub'
    })
    cy.wait('@savePrefs').its('request.body').should('deep.include', { pushEnabled: true })
    expectToast('Reminders are on.')

    // Ora la configurazione di dettaglio è a disposizione.
    cy.contains('ion-button', 'Send test notification').should('be.visible')
    cy.contains('.device-count', '1 registered device').should('be.visible')
  })

  it('cambiare cadenza salva subito, senza bottone Save', () => {
    stubSettings(settings({ subscriptionCount: 1, preferences: {
      pushEnabled: true, intervalMinutes: 120, quietHoursStart: null, quietHoursEnd: null
    } }))
    cy.intercept('POST', '**/api/notifications/preferences', (req) => {
      req.reply(envelope(req.body))
    }).as('savePrefs')

    signIn('granted')
    openNotifications('granted')

    cy.contains('.summary-note', 'Reminders every 2 hours, at any time of day.').should('exist')

    cy.get('ion-select').eq(SELECT_INTERVAL).click()
    cy.get('ion-popover').contains('Every hour').click()

    cy.wait('@savePrefs').its('request.body').should('deep.equal', {
      pushEnabled: true,
      intervalMinutes: 60,
      quietHoursStart: null,
      quietHoursEnd: null
    })
    expectToast('Reminder settings saved.')
    cy.contains('.summary-note', 'Reminders every hour').should('exist')
  })

  it('scegliendo solo l inizio del silenzio la fine viene proposta a +8 ore', () => {
    stubSettings(settings({ subscriptionCount: 1, preferences: {
      pushEnabled: true, intervalMinutes: 60, quietHoursStart: null, quietHoursEnd: null
    } }))
    cy.intercept('POST', '**/api/notifications/preferences', (req) => {
      req.reply(envelope(req.body))
    }).as('savePrefs')

    signIn('granted')
    openNotifications('granted')

    cy.get('ion-select').eq(SELECT_QUIET_FROM).click()
    cy.get('ion-popover').contains('22:00').click()

    cy.wait('@savePrefs').its('request.body').should('deep.include', {
      quietHoursStart: 22,
      quietHoursEnd: 6
    })
    cy.contains('.summary-note', 'except between 22:00 and 06:00').should('exist')
    // La terza select ("Quiet hours until") compare solo con una fascia attiva.
    cy.get('ion-select').should('have.length', 3)
  })

  it('la notifica di prova riferisce quanti dispositivi sono stati raggiunti', () => {
    stubSettings(settings({ subscriptionCount: 2, preferences: {
      pushEnabled: true, intervalMinutes: 60, quietHoursStart: null, quietHoursEnd: null
    } }))
    cy.intercept('POST', '**/api/notifications/test', {
      statusCode: 200,
      body: envelope({ sent: 2, failed: 0 })
    }).as('sendTest')

    signIn('granted')
    openNotifications('granted')

    cy.contains('ion-button', 'Send test notification').click()
    cy.wait('@sendTest')
    expectToast('Test sent to 2 device(s).')
  })

  /** Il 503 del contratto: il server non ha chiavi VAPID per firmare. */
  it('un 503 sulla prova arriva all utente con il messaggio del server', () => {
    stubSettings(settings({ subscriptionCount: 1, preferences: {
      pushEnabled: true, intervalMinutes: 60, quietHoursStart: null, quietHoursEnd: null
    } }))
    cy.intercept('POST', '**/api/notifications/test', {
      statusCode: 503,
      body: { success: false, message: 'Push notifications are not configured on this server.' }
    }).as('sendTest')

    signIn('granted')
    openNotifications('granted')

    cy.contains('ion-button', 'Send test notification').click()
    cy.wait('@sendTest')
    expectToast('Push notifications are not configured on this server.')
  })
})
