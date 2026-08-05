import { describe, expect, test } from 'vitest'
import { nextTick, ref } from 'vue'

import {
  collectErrors,
  errorSummary,
  firstInvalidField,
  focusFirstInvalid,
  isBlank,
  isStrongPassword,
  isValidEmail,
  requiredMessage,
  useFormValidation,
  EMAIL_ERROR,
  PASSWORD_ERROR
} from '@/composables/useValidation'

describe('isValidEmail', () => {
  test.each([
    'giulia@gmail.com',
    'nome.cognome@studenti.unicas.it',
    'a-b_c@sub.domain.co'
  ])('accetta %s', (value) => {
    expect(isValidEmail(value)).toBe(true)
  })

  test.each([
    '',
    'giulia',
    'giulia@',
    'giulia@gmail',
    'giulia gmail.com',
    '@gmail.com'
  ])('rifiuta %s', (value) => {
    expect(isValidEmail(value)).toBe(false)
  })

  test('un valore assente non è un indirizzo valido', () => {
    expect(isValidEmail(null)).toBe(false)
    expect(isValidEmail(undefined)).toBe(false)
  })
})

describe('isStrongPassword', () => {
  test('accetta una password con maiuscola, minuscola, cifra e simbolo', () => {
    expect(isStrongPassword('Password1!')).toBe(true)
  })

  test.each([
    ['troppo corta', 'Pw1!'],
    ['senza maiuscole', 'password1!'],
    ['senza minuscole', 'PASSWORD1!'],
    ['senza cifre', 'Password!'],
    ['senza simboli', 'Password1']
  ])('rifiuta una password %s', (_case, value) => {
    expect(isStrongPassword(value)).toBe(false)
  })
})

describe('isBlank', () => {
  test('i soli spazi valgono come campo vuoto', () => {
    expect(isBlank('   ')).toBe(true)
    expect(isBlank('')).toBe(true)
    expect(isBlank(null)).toBe(true)
    expect(isBlank(' x ')).toBe(false)
  })
})

describe('collectErrors', () => {
  test('per lo stesso campo vince la prima regola violata', () => {
    const errors = collectErrors([
      { field: 'email', invalid: true, message: requiredMessage('Email') },
      { field: 'email', invalid: true, message: EMAIL_ERROR }
    ])

    // "manca" viene prima di "formato errato": è il problema più elementare.
    expect(errors.email).toBe('Email is required')
  })

  test('i campi validi non compaiono nella mappa', () => {
    const errors = collectErrors([
      { field: 'name', invalid: false, message: requiredMessage('Name') },
      { field: 'password', invalid: true, message: PASSWORD_ERROR }
    ])

    expect(errors).toEqual({ password: PASSWORD_ERROR })
  })
})

describe('firstInvalidField', () => {
  test('segue l ordine visivo dei campi, non quello della mappa', () => {
    const errors = { password: 'x', name: 'y' }

    expect(firstInvalidField(errors, ['name', 'email', 'password'])).toBe('name')
  })

  test('senza errori non c è nessun campo su cui portare il focus', () => {
    expect(firstInvalidField({}, ['name'])).toBeNull()
  })
})

describe('errorSummary', () => {
  test('con un solo errore il toast ripete il messaggio del campo', () => {
    expect(errorSummary({ email: EMAIL_ERROR })).toBe(EMAIL_ERROR)
  })

  test('con più errori dichiara quanti sono invece di elencarli', () => {
    expect(errorSummary({ name: 'a', email: 'b', password: 'c' }))
      .toBe('Please fix the 3 highlighted fields')
  })

  test('senza errori non c è nulla da dire', () => {
    expect(errorSummary({})).toBe('')
  })
})

describe('useFormValidation', () => {
  const buildForm = () => {
    const name = ref('')
    const email = ref('')
    const validation = useFormValidation<'name' | 'email'>(
      () =>
        collectErrors<'name' | 'email'>([
          { field: 'name', invalid: isBlank(name.value), message: requiredMessage('Name') },
          { field: 'email', invalid: !isValidEmail(email.value), message: EMAIL_ERROR }
        ]),
      ['name', 'email']
    )
    return { name, email, validation }
  }

  test('non mostra errori finché non si tenta l invio', () => {
    const { validation } = buildForm()

    expect(validation.errors.value).toEqual({})
    expect(validation.errorFor('name')).toBeUndefined()
    expect(validation.fieldClass('name')).toEqual({ 'ion-invalid': false })
    // Gli errori esistono comunque: è solo la loro visibilità a essere differita.
    expect(validation.isValid.value).toBe(false)
    expect(validation.pendingErrors.value.name).toBe('Name is required')
  })

  test('alla pressione del pulsante accende i messaggi e blocca l invio', async () => {
    const { validation } = buildForm()

    await expect(validation.validateOnSubmit()).resolves.toBe(false)

    expect(validation.submitAttempted.value).toBe(true)
    expect(validation.errorFor('name')).toBe('Name is required')
    expect(validation.errorFor('email')).toBe(EMAIL_ERROR)
    expect(validation.fieldClass('email')).toEqual({ 'ion-invalid': true })
  })

  test('dopo il primo tentativo gli errori si aggiornano mentre si corregge', async () => {
    const { name, email, validation } = buildForm()

    await validation.validateOnSubmit()
    name.value = 'Giulia'
    await nextTick()

    expect(validation.errorFor('name')).toBeUndefined()
    expect(validation.errorFor('email')).toBe(EMAIL_ERROR)

    email.value = 'giulia@gmail.com'
    await nextTick()

    expect(validation.errors.value).toEqual({})
    expect(validation.isValid.value).toBe(true)
    await expect(validation.validateOnSubmit()).resolves.toBe(true)
  })

  test('reset riporta il form allo stato "mai inviato"', async () => {
    const { validation } = buildForm()

    await validation.validateOnSubmit()
    validation.reset()

    expect(validation.submitAttempted.value).toBe(false)
    expect(validation.errors.value).toEqual({})
  })
})

describe('focusFirstInvalid', () => {
  test('porta il focus sul controllo marcato con data-field', async () => {
    document.body.innerHTML = `
      <div data-field="name"><input id="name-input" /></div>
      <div data-field="email"><input id="email-input" /></div>
    `

    await focusFirstInvalid('email')

    expect(document.activeElement?.id).toBe('email-input')
  })

  test('un campo inesistente non fa esplodere l invio', async () => {
    document.body.innerHTML = '<div></div>'

    await expect(focusFirstInvalid('missing')).resolves.toBeUndefined()
  })
})
