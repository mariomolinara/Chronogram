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
})
