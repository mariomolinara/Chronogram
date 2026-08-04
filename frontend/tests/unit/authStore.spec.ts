import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, test } from 'vitest'
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
