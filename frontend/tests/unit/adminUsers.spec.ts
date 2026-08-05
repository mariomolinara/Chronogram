import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { effectScope, nextTick } from 'vue'

// `vi.hoisted`: la factory di `vi.mock` viene issata in cima al file e non
// vedrebbe delle const dichiarate qui sotto.
const { get, post } = vi.hoisted(() => ({ get: vi.fn(), post: vi.fn() }))

vi.mock('@/composables/useApi', async () => {
  const actual = await vi.importActual<typeof import('@/composables/useApi')>('@/composables/useApi')
  return { ...actual, api: { get, post } }
})

import { useAdminUsers, type AdminUser, type AdminUsersPage } from '@/composables/useAdminUsers'

const SEARCH_DEBOUNCE_MS = 300

function user(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    userId: 7,
    email: 'mario.rossi@example.com',
    name: 'Mario',
    surname: 'Rossi',
    status: 'PENDING',
    registeredAt: '2026-08-01T10:15:00',
    lastLogin: null,
    activityCount: 3,
    lastActivityDay: '2026-08-02',
    ...overrides
  }
}

function page(overrides: Partial<AdminUsersPage> = {}): AdminUsersPage {
  return {
    items: [user()],
    page: 0,
    size: 25,
    totalItems: 1,
    totalPages: 1,
    counts: { pending: 1, active: 4, blocked: 0 },
    ...overrides
  }
}

/** Risposta axios nell'envelope `ApiResponse` del backend. */
const envelope = <T>(data: T, message = 'ok') => ({ data: { success: true, message, data } })

/** L'ultimo `params` passato a GET, per verificare filtro/ricerca/pagina. */
const lastParams = () => get.mock.calls[get.mock.calls.length - 1][1].params

/**
 * Il composable usa `onScopeDispose`, quindi va creato dentro uno scope: fuori
 * Vue emette un warning e il timer del debounce non verrebbe mai ripulito.
 */
function create(options?: Parameters<typeof useAdminUsers>[0]) {
  const scope = effectScope()
  const store = scope.run(() => useAdminUsers({ searchDebounceMs: SEARCH_DEBOUNCE_MS, ...options }))!
  return { store, scope }
}

let scopes: Array<ReturnType<typeof effectScope>> = []

function admin(options?: Parameters<typeof useAdminUsers>[0]) {
  const { store, scope } = create(options)
  scopes.push(scope)
  return store
}

beforeEach(() => {
  get.mockReset()
  post.mockReset()
  scopes = []
})

afterEach(() => {
  scopes.forEach((scope) => scope.stop())
  vi.useRealTimers()
})

describe('useAdminUsers - caricamento', () => {
  test('carica la prima pagina e pubblica righe, totali e conteggi per stato', async () => {
    get.mockResolvedValue(envelope(page()))
    const store = admin()

    await store.load()

    expect(lastParams()).toEqual({ status: undefined, query: undefined, page: 0, size: 25 })
    expect(store.items.value).toHaveLength(1)
    expect(store.counts.value.pending).toBe(1)
    expect(store.totalItems.value).toBe(1)
    expect(store.loading.value).toBe(false)
    expect(store.error.value).toBeNull()
  })

  test('espone un messaggio di errore e permette il retry', async () => {
    get.mockRejectedValueOnce({ response: { data: { message: 'Boom from server' } } })
    const store = admin()

    await store.load()

    expect(store.error.value).toBe('Boom from server')
    expect(store.items.value).toEqual([])

    get.mockResolvedValueOnce(envelope(page()))
    await store.load()

    expect(store.error.value).toBeNull()
    expect(store.items.value).toHaveLength(1)
  })

  /**
   * Digitando in fretta partono più richieste: se la prima risponde per ultima
   * la lista mostrerebbe i risultati di un termine che l'utente ha già superato.
   */
  test('scarta le risposte arrivate fuori ordine', async () => {
    let resolveFirst: (value: unknown) => void = () => undefined
    get.mockImplementationOnce(() => new Promise((resolve) => { resolveFirst = resolve }))
    get.mockResolvedValueOnce(envelope(page({
      items: [user({ userId: 99, email: 'second@example.com' })]
    })))

    const store = admin()
    const first = store.load()
    const second = store.load()
    await second

    resolveFirst(envelope(page({ items: [user({ userId: 1, email: 'stale@example.com' })] })))
    await first

    expect(store.items.value).toHaveLength(1)
    expect(store.items.value[0].email).toBe('second@example.com')
  })
})

describe('useAdminUsers - filtri, ricerca e pagine', () => {
  test('il filtro di stato riparte dalla prima pagina', async () => {
    get.mockResolvedValue(envelope(page({ page: 2, totalPages: 3, totalItems: 60 })))
    const store = admin()
    await store.load()
    await store.goToPage(2)

    get.mockResolvedValue(envelope(page({ counts: { pending: 1, active: 4, blocked: 0 } })))
    await store.setStatus('PENDING')

    expect(lastParams()).toMatchObject({ status: 'PENDING', page: 0 })
  })

  test('la ricerca parte solo dopo il debounce e in una sola richiesta', async () => {
    vi.useFakeTimers()
    get.mockResolvedValue(envelope(page()))
    const store = admin()
    await store.load()
    expect(get).toHaveBeenCalledTimes(1)

    store.searchInput.value = 'mar'
    await nextTick()
    store.searchInput.value = 'mario'
    await nextTick()
    expect(get).toHaveBeenCalledTimes(1)

    vi.advanceTimersByTime(SEARCH_DEBOUNCE_MS)
    await vi.runAllTicks()
    await Promise.resolve()

    expect(get).toHaveBeenCalledTimes(2)
    expect(lastParams()).toMatchObject({ query: 'mario', page: 0 })
  })

  test('applySearchNow salta l attesa e non ripete una ricerca identica', async () => {
    get.mockResolvedValue(envelope(page()))
    const store = admin()
    await store.load()

    store.searchInput.value = '  mario  '
    await store.applySearchNow()
    expect(get).toHaveBeenCalledTimes(2)
    expect(lastParams()).toMatchObject({ query: 'mario' })

    await store.applySearchNow()
    expect(get).toHaveBeenCalledTimes(2)
  })

  test('goToPage resta dentro i limiti della paginazione', async () => {
    get.mockResolvedValue(envelope(page({ totalPages: 2, totalItems: 30 })))
    const store = admin()
    await store.load()

    await store.previousPage()
    expect(store.page.value).toBe(0)

    get.mockResolvedValue(envelope(page({ page: 1, totalPages: 2, totalItems: 30 })))
    await store.goToPage(9)
    expect(store.page.value).toBe(1)
    expect(lastParams()).toMatchObject({ page: 1 })
  })
})

describe('useAdminUsers - azioni', () => {
  test('approve invia il messaggio e ricarica mantenendo filtro e pagina', async () => {
    get.mockResolvedValue(envelope(page({ page: 1, totalPages: 3, totalItems: 60 })))
    const store = admin()
    await store.load()
    await store.setStatus('PENDING')
    await store.goToPage(1)
    post.mockResolvedValue({ data: { success: true, message: 'Account approved.' } })

    const outcome = await store.runAction(user(), 'approve', '  Welcome aboard  ')

    expect(post).toHaveBeenCalledWith('/api/admin/users/7/approve', { message: 'Welcome aboard' })
    expect(outcome).toEqual({ ok: true, message: 'Account approved.' })
    // La lista si ricarica con gli stessi filtri: nessun salto alla pagina 1.
    expect(lastParams()).toMatchObject({ status: 'PENDING', page: 1 })
    expect(store.actionInProgress.value).toBeNull()
  })

  test('un messaggio vuoto resta null per le azioni facoltative', async () => {
    get.mockResolvedValue(envelope(page()))
    const store = admin()
    post.mockResolvedValue({ data: { success: true, message: 'Account blocked.' } })

    await store.runAction(user({ status: 'ACTIVE' }), 'block', '   ')

    expect(post).toHaveBeenCalledWith('/api/admin/users/7/block', { message: null })
  })

  /** `DeleteUserRequest.message` è `@NotBlank`: il 400 è evitabile lato client. */
  test('la cancellazione senza messaggio non parte nemmeno', async () => {
    const store = admin()

    const outcome = await store.runAction(user(), 'delete', '   ')

    expect(post).not.toHaveBeenCalled()
    expect(outcome.ok).toBe(false)
    expect(outcome.message).toMatch(/message/i)
  })

  test('riporta il messaggio del server quando l azione viene rifiutata', async () => {
    get.mockResolvedValue(envelope(page()))
    const store = admin()
    post.mockRejectedValue({
      response: { data: { message: 'Only a pending account can be approved; this one is active.' } }
    })

    const outcome = await store.runAction(user({ status: 'ACTIVE' }), 'approve', '')

    expect(outcome.ok).toBe(false)
    expect(outcome.message).toBe('Only a pending account can be approved; this one is active.')
    expect(store.actionInProgress.value).toBeNull()
  })

  /**
   * Cancellare l'ultimo elemento di una pagina la svuota: senza correzione
   * l'amministratore vedrebbe "nessun partecipante" pur avendone altri.
   */
  test('dopo una cancellazione che svuota l ultima pagina si arretra di una', async () => {
    get.mockResolvedValue(envelope(page({ page: 1, totalPages: 2, totalItems: 26 })))
    const store = admin()
    await store.load()
    await store.goToPage(1)

    post.mockResolvedValue({ data: { success: true, message: 'Account deleted.' } })
    get.mockReset()
    get.mockResolvedValueOnce(envelope(page({ items: [], page: 1, totalPages: 1, totalItems: 25 })))
    get.mockResolvedValueOnce(envelope(page({ items: [user()], page: 0, totalPages: 1, totalItems: 25 })))

    await store.runAction(user(), 'delete', 'Duplicate account')

    expect(lastParams()).toMatchObject({ page: 0 })
    expect(store.page.value).toBe(0)
    expect(store.items.value).toHaveLength(1)
  })
})
