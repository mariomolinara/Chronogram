/**
 * Registrazione soggetta ad approvazione e accesso negato per stato account.
 *
 * Coprono il bivio introdotto dal backend: `POST /api/auth/register` risponde
 * `data: "PENDING" | "ACTIVE"` e il login restituisce 200 con `success:false`
 * e un messaggio specifico quando la password è giusta ma l'account non può
 * autenticarsi.
 */

const FORM = ['Giulia', 'Esposito', 'Via Roma 1', '3331112222', 'giulia@gmail.com', 'Password1!']

function fillRegistrationForm(): void {
  cy.visit('/register')
  cy.get('ion-input').should('have.length.at.least', 6)
  FORM.forEach((value, index) => {
    cy.get('ion-input').eq(index).find('input').type(value)
  })
}

describe('Registration - approval flow', () => {
  it('resta sulla pagina con un esito chiaro quando l account è in attesa', () => {
    cy.intercept('POST', '**/api/auth/register', {
      statusCode: 200,
      body: {
        success: true,
        message: 'Registration received. An administrator has to approve your account before '
          + 'you can sign in; we will email you as soon as that happens.',
        data: 'PENDING'
      }
    }).as('register')

    fillRegistrationForm()
    cy.contains('ion-button', 'Register').click()
    cy.wait('@register')

    // Nessun rimbalzo al login: lì l'utente verrebbe respinto.
    cy.location('pathname').should('eq', '/register')
    cy.contains('h1', 'Request sent').should('be.visible')
    cy.contains('An administrator has to approve your account').should('be.visible')
    cy.contains('ion-button', 'Back to sign in').click()
    cy.location('pathname').should('eq', '/login')
  })

  it('manda al login quando l account è approvato automaticamente', () => {
    cy.intercept('POST', '**/api/auth/register', {
      statusCode: 200,
      body: { success: true, message: 'Registration successful!', data: 'ACTIVE' }
    }).as('register')

    fillRegistrationForm()
    cy.contains('ion-button', 'Register').click()
    cy.wait('@register')

    cy.location('pathname').should('eq', '/login')
  })
})

describe('Login - account not usable', () => {
  const cases = [
    {
      title: 'in attesa di approvazione',
      message: 'Your account is waiting for administrator approval. '
        + 'You will receive an email once it has been reviewed.'
    },
    {
      title: 'bloccato',
      message: 'Your account has been blocked. Please contact the administrator.'
    }
  ]

  cases.forEach(({ title, message }) => {
    it(`mostra per esteso il motivo quando l account è ${title}`, () => {
      cy.intercept('POST', '**/api/auth/login', {
        statusCode: 200,
        body: { success: false, message, username: null, token: null, role: null, mustChangePassword: false }
      }).as('login')

      cy.visit('/login')
      cy.get('ion-input').eq(0).find('input').type('giulia@gmail.com')
      cy.get('ion-input').eq(1).find('input').type('Password1!')
      cy.contains('ion-button', 'Login').click()
      cy.wait('@login')

      // Il messaggio resta leggibile in pagina, non solo nel toast che scompare.
      cy.contains('.login-notice', message).should('be.visible')
      cy.location('pathname').should('eq', '/login')
    })
  })
})
