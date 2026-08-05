/**
 * Protezione delle pagine private e ritorno al punto di partenza.
 *
 * Regressione riprodotta in produzione: aprendo un deep link (es.
 * /chronogram/activity) senza sessione — browser in incognito richiuso e
 * riaperto — la pagina privata veniva montata lo stesso. Il redirect era
 * affidato al 401 della prima chiamata API e non avveniva: restava solo un
 * toast che spariva dopo tre secondi.
 */

const PRIVATE_PATHS = ['/activity', '/home', '/calendar', '/settings', '/details', '/admin/users']

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
  // Le pagine private chiamano il backend appena montate: si stubba il minimo
  // perché il test parli di navigazione, non di dati.
  cy.intercept('POST', '**/api/activities/types', { body: { success: true, data: [] } })
  cy.intercept('POST', '**/api/activities/**', { body: { success: true, data: [] } })
  cy.intercept('GET', '**/api/**', { body: { success: true, data: {} } })
}

function signIn(): void {
  cy.get('ion-input').eq(0).find('input').type('mario.rossi@unicas.it')
  cy.get('ion-input').eq(1).find('input').type('Password1!')
  cy.contains('ion-button', 'Login').click()
  cy.wait('@login')
}

describe('Sessione assente', () => {
  beforeEach(() => {
    stubLogin()
    cy.clearLocalStorage()
  })

  PRIVATE_PATHS.forEach((path) => {
    it(`riporta al login aprendo ${path} senza sessione`, () => {
      cy.visit(path)

      cy.location('pathname').should('match', /\/login$/)
      cy.contains('ion-button', 'Login').should('be.visible')
    })
  })

  it('non lascia trapelare la pagina privata né chiama il backend', () => {
    const calls: string[] = []
    cy.intercept('**/api/**', (req) => {
      calls.push(req.url)
      req.reply({ body: { success: true, data: {} } })
    })

    cy.visit('/activity')

    cy.location('pathname').should('match', /\/login$/)
    cy.get('body').should('not.contain', 'New Activity')
    // Il guard decide prima del mount: nessuna richiesta parte dalla pagina
    // privata, quindi nessun 401 da cui dipendere.
    cy.then(() => expect(calls, 'chiamate API partite').to.have.length(0))
  })

  it('dopo il login riapre la pagina richiesta, non la home', () => {
    cy.visit('/activity?id=12')

    cy.location('search').should('contain', 'redirect=/activity?id=12')
    signIn()

    cy.location('pathname').should('match', /\/activity$/)
    cy.location('search').should('eq', '?id=12')
  })

  it('ignora un redirect verso un altro sito', () => {
    cy.visit('/login?redirect=https://phishing.example/login')
    signIn()

    // Destinazione predefinita, non il sito esterno. L'invariante è "non siamo
    // finiti fuori": si verifica sull'host di partenza, non su un host fisso,
    // così la prova vale anche eseguita contro la produzione.
    cy.location('pathname').should('match', /\/home$/)
    cy.location('hostname').should('not.contain', 'phishing.example')
    cy.location('origin').should('eq', new URL(Cypress.config('baseUrl') as string).origin)
  })

  it('manda al login anche un URL che non esiste', () => {
    cy.visit('/questa-rotta-non-esiste', { failOnStatusCode: false })

    cy.location('pathname').should('match', /\/login$/)
  })
})

describe('Sessione presente', () => {
  beforeEach(() => {
    stubLogin()
    cy.clearLocalStorage()
  })

  it('non richiede di nuovo le credenziali riaprendo la root', () => {
    cy.visit('/login')
    signIn()
    cy.location('pathname').should('match', /\/home$/)

    // Riapertura dell'app: la root instrada su /login, ma la sessione è ancora
    // nello storage e l'utente non deve rivedere il form.
    cy.visit('/')
    cy.location('pathname').should('match', /\/home$/)
  })

  it('lascia aprire un deep link privato', () => {
    cy.visit('/login')
    signIn()

    cy.visit('/activity')
    cy.location('pathname').should('match', /\/activity$/)
    cy.contains('ion-title', 'New Activity').should('be.visible')
  })
})
