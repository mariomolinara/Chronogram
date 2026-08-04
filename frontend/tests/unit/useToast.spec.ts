import { beforeEach, describe, expect, test, vi } from 'vitest'

const present = vi.fn()
const create = vi.fn(async () => ({ present }))

vi.mock('@ionic/vue', () => ({
  toastController: {
    create: (...args: unknown[]) => create(...args)
  }
}))

import { useToast } from '@/composables/useToast'

describe('useToast', () => {
  beforeEach(() => {
    create.mockClear()
    present.mockClear()
  })

  /**
   * Il feedback DEVE passare dal controller: con `<ion-toast :is-open>` la
   * presentazione falliva prima dell'idratazione e nessun messaggio compariva.
   */
  test('creates the toast through the controller and presents it', async () => {
    const { showToast } = useToast()

    await showToast('Activity deleted', 'success')

    expect(create).toHaveBeenCalledWith({
      message: 'Activity deleted',
      color: 'success',
      duration: 2500
    })
    expect(present).toHaveBeenCalledOnce()
  })

  test('defaults to the danger color, the common case for errors', async () => {
    const { showToast } = useToast()

    await showToast('Network Error')

    expect(create).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'Network Error', color: 'danger' })
    )
  })
})
