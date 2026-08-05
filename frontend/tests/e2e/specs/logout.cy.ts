/**
 * Conferma di uscita: una sola formulazione in tutta l'app.
 *
 * Le tre pagine che offrono il logout (home, impostazioni, dashboard admin)
 * usavano wording diversi ("Sign out?" contro "Are you sure?" / "Yes, log out").
 * Questo test blocca la formulazione unica: titolo che nomina l'azione, due
 * scelte, quella distruttiva in seconda posizione.
 */

function signIn(role: 'USER' | 'ADMIN'): void {
  cy.intercept('POST', '**/api/auth/login', {
    statusCode: 200,
    body: {
      success: true,
      message: 'Login successful!',
      username: 'participant@example.com',
      token: 'stub-token',
      role,
      mustChangePassword: false
    }
  }).as('login')

  // La dashboard admin chiede statistiche e conteggio pending appena montata.
  cy.intercept('GET', '**/api/admin/stats', {
    statusCode: 200,
    body: {
      success: true,
      message: 'ok',
      data: {
        totalUsers: 1, activeUsers: 1, regularUsers: 0, totalActivities: 0,
        activitiesLastWeek: 0, activeWindowDays: 30, regularWindowDays: 7,
        dailyActivities: [{ day: '2026-08-01', count: 0 }]
      }
    }
  })
  cy.intercept('GET', '**/api/admin/users*', {
    statusCode: 200,
    body: {
      success: true,
      message: 'ok',
      data: {
        items: [], page: 0, size: 1, totalItems: 0, totalPages: 0,
        counts: { pending: 0, active: 0, blocked: 0 }
      }
    }
  })

  // La home chiede subito le attivita del giorno: senza stub la richiesta
  // fallisce, la pagina si ridisegna e il pulsante sparisce sotto il click.
  cy.intercept('POST', '**/api/activities/list', {
    statusCode: 200,
    body: { success: true, message: 'ok', data: [] }
  }).as('activities')

  cy.visit('/login')
  cy.get('ion-input').eq(0).find('input').type('participant@example.com')
  cy.get('ion-input').eq(1).find('input').type('Password1!')
  cy.contains('ion-button', 'Login').click()
  cy.wait('@login')
}

/**
 * Ionic monta la pagina entrante come `ion-page-invisible` (opacity 0) e la
 * rivela solo a transizione conclusa; nel frattempo tiene montata anche quella
 * che si sta lasciando. Per Cypress un elemento a opacity 0 è "visibile",
 * quindi senza questa attesa il click parte su un bottone che sta per essere
 * sostituito. Si aspetta la fine della transizione: nessuna pagina invisibile
 * e una sola pagina non nascosta.
 */
function waitForPageTransition(): void {
  cy.get('ion-router-outlet > .ion-page.ion-page-invisible').should('not.exist')
  cy.get('ion-router-outlet > .ion-page:not(.ion-page-hidden)').should('have.length', 1)
  // Nessun overlay di caricamento davanti: coprirebbe l'alert di conferma.
  cy.get('ion-loading:not(.overlay-hidden)').should('not.exist')
}

/**
 * Ionic lascia in pagina gli alert già chiusi (marcati `overlay-hidden`): si
 * guarda solo quello presentato, altrimenti si finisce su un residuo.
 */
function expectSignOutAlert(): void {
  cy.get('ion-alert:not(.overlay-hidden)').should('be.visible').within(() => {
    cy.get('.alert-title, .alert-head h2').should('contain.text', 'Sign out?')
    cy.get('button.alert-button').should('have.length', 2)
    cy.get('button.alert-button').eq(0).should('contain.text', 'Cancel')
    cy.get('button.alert-button').eq(1).should('contain.text', 'Sign out')
    cy.get('button.alert-button').eq(1).should('have.class', 'alert-button-role-destructive')
    // Annullare non deve sloggare: la sessione resta.
    cy.get('button.alert-button').eq(0).click()
  })
}

describe('Sign out - stessa conferma ovunque', () => {
  it('home', () => {
    signIn('USER')
    cy.visit('/home')
    cy.wait('@activities')
    waitForPageTransition()
    cy.get('ion-button.logout-btn:visible').click()
    expectSignOutAlert()
    cy.location('pathname').should('eq', '/home')
  })

  it('impostazioni', () => {
    signIn('USER')
    cy.visit('/settings')
    waitForPageTransition()
    cy.get('[aria-label="Sign out"]:visible').click()
    expectSignOutAlert()
    cy.location('pathname').should('eq', '/settings')
  })

  it('dashboard admin', () => {
    signIn('ADMIN')
    cy.visit('/admin')
    waitForPageTransition()
    cy.get('ion-header ion-buttons[slot="end"] ion-button:visible').click()
    expectSignOutAlert()
    cy.location('pathname').should('eq', '/admin')
  })
})
