import { createPinia, setActivePinia } from 'pinia'
import { afterEach, beforeEach, describe, expect, test, vi } from 'vitest'
import { Preferences } from '@capacitor/preferences'
import router from '@/router'
import { useAuthStore } from '@/store/auth'

describe('authStore.displayName', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  test('is empty when there is no session', () => {
    const store = useAuthStore()
    expect(store.displayName).toBe('')
  })

  test('uses the local part of the email returned by the login', () => {
    const store = useAuthStore()
    store.user = { username: 'mario.rossi@unicas.it', role: 'USER', mustChangePassword: false }
    expect(store.displayName).toBe('mario.rossi')
  })

  test('falls back to the raw username when it is not an email', () => {
    const store = useAuthStore()
    store.user = { username: 'admin', role: 'ADMIN', mustChangePassword: false }
    expect(store.displayName).toBe('admin')
  })
})

describe('authStore.logout', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  afterEach(() => {
    vi.restoreAllMocks()
  })

  /**
   * Copre la regressione per cui lo store risolveva il router con `useRouter()`:
   * creato dal guard (fuori da un `setup()`) restava `undefined` e il redirect
   * post-logout andava in eccezione.
   */
  test('clears the session in memory and in storage, then returns to login', async () => {
    const push = vi.spyOn(router, 'push').mockResolvedValue(undefined)
    const store = useAuthStore()

    store.token = 'jwt-token'
    store.user = { username: 'mario.rossi@unicas.it', role: 'USER', mustChangePassword: false }
    await Preferences.set({ key: 'authToken', value: 'jwt-token' })

    await store.logout()

    expect(store.token).toBeNull()
    expect(store.user).toBeNull()
    expect(store.isAuthenticated).toBe(false)
    expect(store.getToken()).toBeNull()
    expect((await Preferences.get({ key: 'authToken' })).value).toBeNull()
    expect(push).toHaveBeenCalledWith('/login')
  })
})
