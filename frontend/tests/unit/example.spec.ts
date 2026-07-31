import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, test } from 'vitest'
import { useActivityStore } from '@/store/activityStore'

describe('activityStore', () => {
  beforeEach(() => {
    // Ogni test parte da una Pinia pulita e attiva
    setActivePinia(createPinia())
  })

  test('needsRefresh defaults to false', () => {
    const store = useActivityStore()
    expect(store.needsRefresh).toBe(false)
  })

  test('needsRefresh can be toggled to request a refresh', () => {
    const store = useActivityStore()
    store.needsRefresh = true
    expect(store.needsRefresh).toBe(true)
  })
})
