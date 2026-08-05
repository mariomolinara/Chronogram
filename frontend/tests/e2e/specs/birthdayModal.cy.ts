/**
 * Ciclo di vita della modale del compleanno (registrazione e modifica profilo).
 *
 * Il sospetto era che dopo apertura e chiusura restassero appesi alla pagina un
 * `ion-backdrop` e il blocco dello scorrimento. La misura ha mostrato che i due
 * indizi erano di altra natura:
 *
 * - `body { overflow: hidden }` è la regola di base di Ionic, presente dal primo
 *   paint anche senza mai aprire un overlay: in un'app Ionic si scorre dentro
 *   `ion-content`, non nel body. Quello che la modale aggiunge e toglie è la
 *   classe `body.backdrop-no-scroll` — ed è quella che va verificata;
 * - l'`ion-backdrop` che si vedeva nel markup apparteneva agli overlay inline
 *   (`ion-modal`, `ion-loading`) che erano dichiarati DENTRO `ion-content`:
 *   faceva parte del form, non era un residuo. Ora gli overlay sono fratelli di
 *   `ion-content` e il contenuto della pagina non ne contiene più nessuno.
 *
 * Questo test blocca l'invariante vera: dopo N cicli non resta nulla di
 * presentato e la pagina è ancora scorribile e digitabile.
 */

/** Nessun overlay presentato e nessun backdrop disegnato a schermo. */
function expectNoOverlayResidue(): void {
  cy.get('body').should('not.have.class', 'backdrop-no-scroll')
  cy.get('ion-router-outlet').should('not.have.attr', 'aria-hidden')

  cy.document().then((doc) => {
    doc.querySelectorAll('ion-modal').forEach((modal) => {
      expect(modal.className, 'nessuna modale presentata').to.not.contain('show-modal')
      expect(modal.getClientRects().length, 'la modale non occupa spazio').to.equal(0)
    })

    // Backdrop di luce e di ombra: nessuno deve avere box a schermo.
    const backdrops: Element[] = Array.from(doc.querySelectorAll('ion-backdrop'))
    doc.querySelectorAll('ion-modal, ion-loading').forEach((overlay) => {
      backdrops.push(...Array.from(overlay.shadowRoot?.querySelectorAll('ion-backdrop') ?? []))
    })
    backdrops.forEach((backdrop) => {
      expect(backdrop.getClientRects().length, 'backdrop non disegnato').to.equal(0)
    })

    // Gli overlay stanno fuori dal contenuto scorrevole della pagina.
    const content = doc.querySelector('ion-content')
    expect(content?.querySelector('ion-backdrop'), 'nessun backdrop dentro ion-content').to.equal(null)
    expect(content?.querySelector('ion-modal'), 'nessuna modale dentro ion-content').to.equal(null)
  })
}

/** Apre la modale, conferma con "Done" e attende la fine dell'animazione. */
function openAndConfirmBirthday(): void {
  cy.contains('ion-item', 'Birthday').click()
  cy.get('ion-modal.birthday-modal ion-datetime', { timeout: 8000 }).should('be.visible')
  cy.get('body').should('have.class', 'backdrop-no-scroll')
  cy.get('ion-modal.birthday-modal ion-datetime').shadow().find('#confirm-button').click()
  cy.get('ion-modal.birthday-modal').should('have.class', 'overlay-hidden')
}

/** Apre la modale e la chiude con "Cancel". */
function openAndCancelBirthday(): void {
  cy.contains('ion-item', 'Birthday').click()
  cy.get('ion-modal.birthday-modal ion-datetime', { timeout: 8000 }).should('be.visible')
  cy.get('ion-modal.birthday-modal ion-datetime').shadow().find('#cancel-button').click()
  cy.get('ion-modal.birthday-modal').should('have.class', 'overlay-hidden')
}

/** Sessione utente valida, con le chiamate del profilo stubbate. */
function signIn(): void {
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
  cy.intercept('POST', '**/api/user/**', { body: { success: true, data: {} } })
  cy.intercept('POST', '**/api/activities/**', { body: { success: true, data: [] } })

  cy.visit('/login')
  cy.get('ion-input').eq(0).find('input').type('mario.rossi@unicas.it')
  cy.get('ion-input').eq(1).find('input').type('Password1!')
  cy.contains('ion-button', 'Login').click()
  cy.wait('@login')
}

describe('Birthday modal - nessun residuo dopo il ciclo di vita', () => {
  it('registrazione: aperture e chiusure ripetute lasciano la pagina usabile', () => {
    cy.visit('/register')
    expectNoOverlayResidue()

    openAndConfirmBirthday()
    expectNoOverlayResidue()
    openAndCancelBirthday()
    expectNoOverlayResidue()
    openAndConfirmBirthday()
    expectNoOverlayResidue()

    // La data confermata è finita nel campo.
    cy.contains('ion-item', 'Birthday').find('.custom-input-value').should('not.have.text', '')

    // Il form resta digitabile e il contenuto resta scorrevole.
    cy.get('ion-input').eq(0).find('input').type('Mario')
    cy.get('ion-input').eq(0).find('input').should('have.value', 'Mario')

    cy.viewport(390, 640)
    cy.get('ion-content').then(($content) => {
      const content = $content[0] as HTMLElement & { scrollToBottom: (d?: number) => Promise<void> }
      return content.scrollToBottom(0)
    })
    cy.get('ion-content').then(($content) => {
      const scroller = $content[0].shadowRoot?.querySelector('.inner-scroll') as HTMLElement
      expect(scroller.scrollTop, 'il contenuto scorre ancora').to.be.greaterThan(0)
    })
  })

  it('modifica profilo: stessa modale, stesso esito', () => {
    // `/edit-profile` è una pagina privata: dal momento in cui il router ha un
    // guard di sessione, aprirla senza autenticarsi porta al login e la modale
    // non esiste nemmeno. Si entra come farebbe un utente.
    signIn()

    cy.visit('/edit-profile')
    expectNoOverlayResidue()

    openAndConfirmBirthday()
    expectNoOverlayResidue()
    openAndCancelBirthday()
    expectNoOverlayResidue()

    cy.get('ion-input').eq(0).find('input').type('Giulia')
    cy.get('ion-input').eq(0).find('input').should('have.value', 'Giulia')
  })
})
