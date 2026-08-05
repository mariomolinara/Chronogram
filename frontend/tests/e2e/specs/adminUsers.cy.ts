/**
 * Console di amministrazione degli utenti.
 *
 * Le API sono stubbate con `cy.intercept`: il test verifica il comportamento del
 * frontend (filtri, conferme, aggiornamento della lista) e il contratto con cui
 * chiama il backend (path, query string, corpo della richiesta), non il backend.
 */

type Status = 'PENDING' | 'ACTIVE' | 'BLOCKED'

interface StubUser {
  userId: number
  email: string
  name: string | null
  surname: string | null
  status: Status
  registeredAt: string
  lastLogin: string | null
  activityCount: number
  lastActivityDay: string | null
}

const makeUser = (id: number, status: Status, overrides: Partial<StubUser> = {}): StubUser => ({
  userId: id,
  email: `user${id}@example.com`,
  name: `Name${id}`,
  surname: `Surname${id}`,
  status,
  registeredAt: '2026-07-15T09:30:00',
  lastLogin: status === 'PENDING' ? null : '2026-08-01T18:05:00',
  activityCount: status === 'PENDING' ? 0 : 12,
  lastActivityDay: status === 'PENDING' ? null : '2026-07-31',
  ...overrides
})

const pageOf = (items: StubUser[], extra: Record<string, unknown> = {}) => ({
  success: true,
  message: 'Users retrieved successfully',
  data: {
    items,
    page: 0,
    size: 25,
    totalItems: items.length,
    totalPages: 1,
    counts: { pending: 2, active: 3, blocked: 1 },
    ...extra
  }
})

const DEFAULT_ITEMS = [
  makeUser(1, 'PENDING', { email: 'giulia.esposito@gmail.com' }),
  makeUser(2, 'PENDING'),
  makeUser(3, 'ACTIVE', { email: 'mario.rossi@unicas.it' }),
  makeUser(4, 'BLOCKED')
]

function stubApi(items: StubUser[] = DEFAULT_ITEMS): void {
  cy.intercept('POST', '**/api/auth/login', {
    statusCode: 200,
    body: {
      success: true,
      message: 'Login successful!',
      username: 'admin@chronogram.local',
      token: 'stub-token',
      role: 'ADMIN',
      mustChangePassword: false
    }
  }).as('login')

  cy.intercept('GET', '**/api/admin/stats', {
    statusCode: 200,
    body: {
      success: true,
      message: 'ok',
      data: {
        totalUsers: 6, activeUsers: 3, regularUsers: 1,
        totalActivities: 40, activitiesLastWeek: 5,
        activeWindowDays: 30, regularWindowDays: 7,
        dailyActivities: [{ day: '2026-08-01', count: 2 }, { day: '2026-08-02', count: 3 }]
      }
    }
  }).as('stats')

  cy.intercept('GET', '**/api/admin/users*', (req) => {
    const status = req.query.status as string | undefined
    const query = ((req.query.query as string) || '').toLowerCase()
    const visible = items.filter((u) =>
      (!status || u.status === status)
      && (!query || `${u.email} ${u.name ?? ''} ${u.surname ?? ''}`.toLowerCase().includes(query)))
    req.reply(pageOf(visible))
  }).as('users')
}

/** Entra come amministratore passando dal form di login vero. */
function signInAsAdmin(): void {
  cy.visit('/login')
  cy.get('ion-input').eq(0).find('input').type('admin@chronogram.local')
  cy.get('ion-input').eq(1).find('input').type('Admin1234!')
  cy.contains('ion-button', 'Login').click()
  cy.wait('@login')
  // L'admin atterra sulla dashboard, che chiede subito il conteggio dei pending
  // (`?size=1`): va consumata qui, o i `cy.wait('@users')` dei test resterebbero
  // sfasati di una richiesta.
  cy.wait('@users').its('request.query').should('deep.include', { size: '1' })
}

/** Il testo del toast Ionic vive nello shadow DOM: si legge dalla proprietà. */
function expectToast(text: string): void {
  cy.get('ion-toast').should(($toast) => {
    const message = ($toast[0] as HTMLElement & { message?: string }).message ?? ''
    expect(message).to.contain(text)
  })
}

describe('Admin - participants', () => {
  beforeEach(() => {
    stubApi()
  })

  it('mostra il badge dei pending sulla dashboard e porta alla lista', () => {
    signInAsAdmin()

    cy.wait('@stats')
    cy.contains('.pending-badge', '2 pending').should('be.visible')
    cy.contains('ion-button', 'Review requests').click()

    cy.location('pathname').should('eq', '/admin/users')
    cy.wait('@users')
    cy.contains('.pending-banner', '2 registrations are waiting for approval').should('be.visible')
    cy.get('.user-row').should('have.length', 4)
  })

  it('offre solo le azioni compatibili con lo stato dell account', () => {
    signInAsAdmin()
    cy.visit('/admin/users')
    cy.wait('@users')

    cy.get('.user-row').eq(0).within(() => {
      cy.contains('.status-chip', 'Pending')
      cy.contains('ion-button', 'Approve').should('exist')
      cy.contains('ion-button', 'Reject').should('exist')
      cy.contains('ion-button', 'Delete').should('exist')
      cy.contains('ion-button', 'Block').should('not.exist')
    })
    cy.get('.user-row').eq(2).within(() => {
      cy.contains('ion-button', 'Block').should('exist')
      cy.contains('ion-button', 'Approve').should('not.exist')
      cy.contains('ion-button', 'Reject').should('not.exist')
    })
    cy.get('.user-row').eq(3).within(() => {
      cy.contains('ion-button', 'Unblock').should('exist')
      cy.contains('ion-button', 'Reject').should('not.exist')
    })
  })

  it('approva una richiesta dopo conferma e ricarica la lista', () => {
    cy.intercept('POST', '**/api/admin/users/1/approve', {
      statusCode: 200,
      body: { success: true, message: 'Account approved. The user has been notified by email.' }
    }).as('approve')

    signInAsAdmin()
    cy.visit('/admin/users')
    cy.wait('@users')

    cy.get('.user-row').eq(0).contains('ion-button', 'Approve').click()
    cy.get('.confirm-dialog').should('be.visible')
    cy.contains('.confirm-title', 'Approve this registration?')
    // L'anteprima dell'email è parte della decisione, non un dettaglio.
    cy.contains('.email-preview', 'Your account has been approved')
    cy.get('.message-field').contains('(optional)')

    cy.get('.message-input').find('textarea').type('Welcome aboard')
    cy.contains('.preview-message', 'Welcome aboard').should('be.visible')

    cy.get('.confirm-actions').contains('ion-button', 'Approve').click()

    cy.wait('@approve').its('request.body').should('deep.equal', { message: 'Welcome aboard' })
    // Dopo l'azione la lista si ricarica e la dialog si chiude.
    cy.wait('@users')
    cy.get('.confirm-dialog').should('not.exist')
    expectToast('Account approved')
  })

  /**
   * Il rifiuto non ha un endpoint proprio: `POST .../block` su un account
   * PENDING lo respinge senza cancellarne il record. La dialog deve dirlo,
   * mostrare l'anteprima della mail di blocco (quella che il backend invia
   * davvero) e chiarire che la decisione è reversibile.
   */
  it('rifiuta una richiesta in attesa passando dall endpoint block', () => {
    cy.intercept('POST', '**/api/admin/users/1/block', {
      statusCode: 200,
      body: { success: true, message: 'Account blocked. The user has been notified by email.' }
    }).as('reject')

    signInAsAdmin()
    cy.visit('/admin/users')
    cy.wait('@users')

    cy.get('.user-row').eq(0).contains('ion-button', 'Reject').click()
    cy.get('.confirm-dialog').should('be.visible')
    cy.contains('.confirm-title', 'Reject this registration?')
    cy.contains('.confirm-explanation', 'notified by email')
    cy.contains('.confirm-explanation', 'Nothing is deleted')
    // Reversibilità dichiarata: è la differenza fra Reject e Delete.
    cy.contains('.confirm-reversible', 'unblock the account')
    // L'anteprima rispecchia `EmailService.sendAccountBlockedEmail`.
    cy.contains('.email-preview', 'Your account has been blocked')
    cy.contains('.email-preview', 'Your data has not been deleted')
    // A differenza di Delete, il messaggio resta facoltativo.
    cy.get('.message-field').contains('(optional)')
    cy.get('.confirm-actions').contains('ion-button', 'Reject')
      .should('not.have.attr', 'disabled')

    cy.get('.message-input').find('textarea').type('Your email domain is not part of the study')
    cy.get('.confirm-actions').contains('ion-button', 'Reject').click()

    cy.wait('@reject').its('request.body')
      .should('deep.equal', { message: 'Your email domain is not part of the study' })
    cy.wait('@users')
    cy.get('.confirm-dialog').should('not.exist')
    expectToast('Account blocked')
  })

  it('impone un messaggio per la cancellazione e avverte che è irreversibile', () => {
    cy.intercept('POST', '**/api/admin/users/1/delete', {
      statusCode: 200,
      body: { success: true, message: 'Account deleted. The user has been notified by email.' }
    }).as('delete')

    signInAsAdmin()
    cy.visit('/admin/users')
    cy.wait('@users')

    // Un account con attività: l'avviso ne dichiara il numero.
    cy.get('.user-row').eq(2).contains('ion-button', 'Delete').click()
    cy.contains('.danger-notice', 'all 12 activities')
    cy.contains('.confirm-actions ion-button', 'Cancel').click()

    cy.get('.user-row').eq(0).contains('ion-button', 'Delete').click()
    cy.contains('.danger-notice', 'cannot be undone')
    cy.contains('.danger-notice', 'has not recorded any activity yet')
    cy.get('.message-field').contains('(required)')
    // Il pulsante è premibile: è la pressione a dire cosa manca. Prima restava
    // disabilitato e nessuno spiegava perché.
    cy.get('.confirm-actions').contains('ion-button', 'Delete')
      .should('not.have.attr', 'disabled')
      .click()
    cy.contains('.field-error', 'Write the message the user will receive')
    // Niente chiamata al backend finché il messaggio manca.
    cy.get('@delete.all').should('have.length', 0)

    cy.get('.message-input').find('textarea').type('Duplicate account')
    cy.get('.confirm-actions').contains('ion-button', 'Delete').click()

    cy.wait('@delete').its('request.body').should('deep.equal', { message: 'Duplicate account' })
    cy.get('.confirm-dialog').should('not.exist')
  })

  it('filtra per stato e cerca in modo debounced', () => {
    signInAsAdmin()
    cy.visit('/admin/users')
    cy.wait('@users')

    cy.contains('ion-segment-button', 'Blocked').click()
    cy.wait('@users').its('request.query').should('deep.include', { status: 'BLOCKED', page: '0' })
    cy.get('.user-row').should('have.length', 1)

    cy.contains('ion-segment-button', 'All').click()
    cy.wait('@users')

    cy.get('ion-searchbar').find('input').type('giulia')
    cy.wait('@users').its('request.query').should('deep.include', { query: 'giulia', page: '0' })
    cy.get('.user-row').should('have.length', 1)
    cy.contains('.user-email', 'giulia.esposito@gmail.com')
  })

  it('mostra il motivo del rifiuto quando il server nega l azione', () => {
    cy.intercept('POST', '**/api/admin/users/3/block', {
      statusCode: 400,
      body: { success: false, message: 'Administrator accounts cannot be managed from this list.' }
    }).as('block')

    signInAsAdmin()
    cy.visit('/admin/users')
    cy.wait('@users')

    cy.get('.user-row').eq(2).contains('ion-button', 'Block').click()
    cy.get('.confirm-actions').contains('ion-button', 'Block').click()

    cy.wait('@block')
    // La dialog resta aperta con la spiegazione del server.
    cy.contains('.field-error', 'Administrator accounts cannot be managed from this list.')
  })

  it('offre il retry quando la lista non si carica', () => {
    signInAsAdmin()
    cy.intercept('GET', '**/api/admin/users*', {
      statusCode: 500,
      body: { success: false, message: 'An internal server error occurred. Please try again later.' }
    }).as('usersFailure')

    cy.visit('/admin/users')
    cy.wait('@usersFailure')
    cy.contains('.state-block', 'An internal server error occurred')

    stubApi()
    cy.contains('.state-block ion-button', 'Retry').click()
    cy.wait('@users')
    cy.get('.user-row').should('have.length', 4)
  })
})
