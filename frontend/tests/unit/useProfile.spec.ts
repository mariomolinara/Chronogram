import { beforeEach, describe, expect, test, vi } from 'vitest'

const get = vi.fn()
const post = vi.fn()

// L'istanza axios reale legge le variabili d'ambiente e monta gli interceptor:
// qui interessa solo il contratto (path e corpo delle richieste).
vi.mock('@/composables/useApi', () => ({
  api: {
    get: (...args: unknown[]) => get(...args),
    post: (...args: unknown[]) => post(...args)
  }
}))

import {
  changePassword,
  fetchProfile,
  formatBirthday,
  normalizeIsoDate,
  toProfileForm,
  toProfileUpdatePayload,
  updateProfile,
  EMPTY_PROFILE_FORM,
  type ProfileForm
} from '@/composables/useProfile'

const fullDto = {
  name: 'Giulia',
  surname: 'Esposito',
  address: 'Via Roma 1',
  phone: '3331112222',
  email: 'giulia@gmail.com',
  birthday: '1998-04-23',
  gender: 'female'
}

const formOf = (overrides: Partial<ProfileForm> = {}): ProfileForm => ({
  ...EMPTY_PROFILE_FORM,
  ...overrides
})

beforeEach(() => {
  get.mockReset()
  post.mockReset()
})

describe('toProfileForm', () => {
  test('riporta il profilo del backend nella form', () => {
    expect(toProfileForm(fullDto)).toEqual(fullDto)
  })

  /**
   * `v-model` su un `ion-input` non deve mai ricevere `null`: Ionic lo stampa
   * come la stringa "null" dentro al campo.
   */
  test('i campi assenti o nulli diventano stringhe vuote', () => {
    expect(toProfileForm({ name: 'Giulia', birthday: null })).toEqual({
      ...EMPTY_PROFILE_FORM,
      name: 'Giulia'
    })
  })

  test('una risposta vuota non lascia la form in uno stato indefinito', () => {
    expect(toProfileForm(null)).toEqual(EMPTY_PROFILE_FORM)
    expect(toProfileForm(undefined)).toEqual(EMPTY_PROFILE_FORM)
  })

  test('una data-ora completa viene ridotta al giorno', () => {
    expect(toProfileForm({ ...fullDto, birthday: '1998-04-23T00:00:00' }).birthday)
      .toBe('1998-04-23')
  })
})

describe('normalizeIsoDate', () => {
  test.each([
    ['1998-04-23', '1998-04-23'],
    ['1998-04-23T10:30:00', '1998-04-23'],
    ['', ''],
    ['23/04/1998', ''],
    ['not a date', '']
  ])('normalizza %s in %s', (input, expected) => {
    expect(normalizeIsoDate(input)).toBe(expected)
  })

  test('un valore non testuale non produce una data', () => {
    expect(normalizeIsoDate(null)).toBe('')
    expect(normalizeIsoDate(undefined)).toBe('')
  })
})

describe('formatBirthday', () => {
  /**
   * Ricomposta a mano e non con `new Date(iso)`: quest'ultima interpreta la
   * stringa in UTC e a ovest di Greenwich mostrerebbe il giorno precedente.
   */
  test('mostra la data nel formato del giorno/mese/anno', () => {
    expect(formatBirthday('1998-04-23')).toBe('23/04/1998')
    expect(formatBirthday('2000-01-01')).toBe('01/01/2000')
  })

  test('senza data non inventa un segnaposto', () => {
    expect(formatBirthday('')).toBe('')
  })
})

describe('toProfileUpdatePayload', () => {
  test('non include l email: non è modificabile dal profilo', () => {
    const payload = toProfileUpdatePayload(formOf({ ...fullDto }))

    expect(payload).not.toHaveProperty('email')
    expect(Object.keys(payload).sort())
      .toEqual(['address', 'birthday', 'gender', 'name', 'phone', 'surname'])
  })

  test('gli spazi ai bordi si perdono qui, non nel database', () => {
    const payload = toProfileUpdatePayload(formOf({
      name: '  Giulia  ',
      surname: ' Esposito ',
      address: ' Via Roma 1 ',
      phone: ' 3331112222 '
    }))

    expect(payload).toMatchObject({
      name: 'Giulia',
      surname: 'Esposito',
      address: 'Via Roma 1',
      phone: '3331112222'
    })
  })

  test('un compleanno non indicato viaggia come null, come nel DTO in lettura', () => {
    expect(toProfileUpdatePayload(formOf({ birthday: '' })).birthday).toBeNull()
    expect(toProfileUpdatePayload(formOf({ birthday: '1998-04-23' })).birthday)
      .toBe('1998-04-23')
  })
})

describe('chiamate al backend', () => {
  test('fetchProfile legge GET /api/profile/me e mappa la risposta', async () => {
    get.mockResolvedValue({ data: fullDto })

    await expect(fetchProfile()).resolves.toEqual(fullDto)
    expect(get).toHaveBeenCalledWith('/api/profile/me')
  })

  test('updateProfile invia il payload normalizzato e riparte dalla risposta', async () => {
    post.mockResolvedValue({ data: { ...fullDto, name: 'Giulia Maria' } })

    const saved = await updateProfile(formOf({ ...fullDto, name: '  Giulia Maria  ' }))

    expect(post).toHaveBeenCalledWith('/api/profile/update', {
      name: 'Giulia Maria',
      surname: 'Esposito',
      address: 'Via Roma 1',
      phone: '3331112222',
      birthday: '1998-04-23',
      gender: 'female'
    })
    // Vince ciò che il backend dice di aver salvato, non ciò che si è inviato.
    expect(saved.name).toBe('Giulia Maria')
  })

  /**
   * Il contratto concordato è l'oggetto nudo, ma altrove il backend usa
   * l'envelope `ApiResponse`: una form vuota che sembra un profilo senza dati
   * sarebbe il peggiore dei fallimenti (il salvataggio successivo lo cancella).
   */
  test('fetchProfile accetta anche la risposta incapsulata in ApiResponse', async () => {
    get.mockResolvedValue({ data: { success: true, message: 'ok', data: fullDto } })

    await expect(fetchProfile()).resolves.toEqual(fullDto)
  })

  test('fetchProfile fallisce invece di restituire una form vuota', async () => {
    get.mockResolvedValue({ data: { success: true, message: 'ok' } })

    await expect(fetchProfile()).rejects.toThrow(/unexpected profile format/i)
  })

  test('updateProfile senza profilo in risposta tiene i valori inviati', async () => {
    post.mockResolvedValue({ data: { success: true, message: 'Profile updated' } })

    await expect(updateProfile(formOf({ ...fullDto }))).resolves.toEqual(fullDto)
  })

  test('changePassword manda solo le due password', async () => {
    post.mockResolvedValue({ data: { success: true } })

    await changePassword('Old1!pass', 'New1!pass')

    expect(post).toHaveBeenCalledWith('/api/profile/change-password', {
      currentPassword: 'Old1!pass',
      newPassword: 'New1!pass'
    })
  })
})
