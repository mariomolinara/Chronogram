/**
 * Smoke test dell'avvio: la root instrada al login e la pagina è utilizzabile.
 * (Sostituisce il test di esempio dello starter Ionic, che cercava un markup
 * — `#container`, "Ready to create an app?" — mai esistito in Chronogram.)
 */
describe('App bootstrap', () => {
  it('apre la pagina di login sulla root', () => {
    cy.visit('/')

    cy.location('pathname').should('eq', '/login')
    cy.get('ion-input').should('have.length', 2)
    cy.contains('ion-button', 'Login').should('be.visible')
    cy.contains('ion-button', 'Sign Up').should('be.visible')
  })

  /**
   * La home apriva un `ion-loading` a tutta pagina mentre si montava, cioè
   * prima che il custom element fosse idratato. In quel caso Ionic rimanda la
   * `present()` a un `requestAnimationFrame`: se la risposta arriva prima, la
   * `dismiss()` non trova nulla di presentato e l'overlay compare subito dopo
   * senza che nessuno lo chiuda più. Restavano un backdrop a tutto schermo e
   * `body.backdrop-no-scroll`, con l'app inutilizzabile.
   *
   * La risposta qui è immediata apposta: è la condizione che innescava il
   * blocco (backend locale, cache, rete veloce).
   */
  it('la home non lascia overlay di caricamento appesi', () => {
    cy.intercept('POST', '**/api/auth/login', {
      statusCode: 200,
      body: {
        success: true, message: 'Login successful!', username: 'participant@example.com',
        token: 'stub-token', role: 'USER', mustChangePassword: false
      }
    }).as('login')
    cy.intercept('POST', '**/api/activities/list', {
      statusCode: 200,
      body: { success: true, message: 'ok', data: [] }
    }).as('activities')

    cy.visit('/login')
    cy.get('ion-input').eq(0).find('input').type('participant@example.com')
    cy.get('ion-input').eq(1).find('input').type('Password1!')
    cy.contains('ion-button', 'Login').click()
    cy.wait('@login')

    cy.visit('/home')
    cy.wait('@activities')
    cy.contains('.state-block', 'No activities for today').should('be.visible')

    cy.get('body').should('not.have.class', 'backdrop-no-scroll')
    cy.get('ion-loading:not(.overlay-hidden)').should('not.exist')
    cy.document().then((doc) => {
      Array.from(doc.querySelectorAll('ion-backdrop')).forEach((backdrop) => {
        expect(backdrop.getClientRects().length, 'nessun backdrop disegnato').to.equal(0)
      })
    })

    // Il contenuto risponde ancora: nulla lo copre.
    cy.get('.add-fab-btn').should('be.visible').click()
    cy.location('pathname').should('eq', '/activity')
  })
})
