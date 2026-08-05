import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, test, vi } from 'vitest'

/**
 * Guard di sessione nel contesto NATIVO (Capacitor/Android).
 *
 * Differenza con il web: `@capacitor/preferences` sul web e' un wrapper attorno
 * a localStorage (lettura sincrona incartata in una promise gia' risolta: la
 * ripresa della sessione costa una microtask), su Android e' una chiamata al
 * bridge verso SharedPreferences, cioe' un vero round-trip asincrono di
 * millisecondi. Ogni finestra in cui `isAuthenticated` e' ancora falso pur
 * esistendo una sessione salvata, invisibile sul web, su Android diventa un
 * "logout apparente" ad ogni avvio dell'app.
 *
 * Qui lo storage e' simulato CON latenza per rendere quella finestra osservabile.
 */
const NATIVE_LATENCY_MS = 25
const nativeStore = new Map<string, string>()

vi.mock('@capacitor/preferences', () => {
  const bridge = () => new Promise((resolve) => setTimeout(resolve, NATIVE_LATENCY_MS))
  return {
    Preferences: {
      async get({ key }: { key: string }) {
        await bridge()
        return { value: nativeStore.has(key) ? nativeStore.get(key)! : null }
      },
      async set({ key, value }: { key: string; value: string }) {
        await bridge()
        nativeStore.set(key, value)
      },
      async remove({ key }: { key: string }) {
        await bridge()
        nativeStore.delete(key)
      },
      async clear() {
        await bridge()
        nativeStore.clear()
      }
    }
  }
})

import router, { safeRedirectTarget } from '@/router'
import { useAuthStore } from '@/store/auth'

const SESSION_USER = { username: 'mario.rossi@unicas.it', role: 'USER', mustChangePassword: false }

function seedNativeSession(): void {
  nativeStore.set('authToken', 'jwt-nativo')
  nativeStore.set('userData', JSON.stringify(SESSION_USER))
}

beforeEach(() => {
  nativeStore.clear()
  setActivePinia(createPinia())
})

describe('avvio a freddo dell app', () => {
  /**
   * Caso 2 del brief: sessione salvata, la root instrada su /login e il guard
   * deve rimbalzare su Home senza mostrare il form.
   * Riproduce la prima navigazione dell'app (`main.ts` la fa partire con
   * `app.use(router)` prima di `router.isReady()`): root -> /login -> guard.
   */
  test('con sessione salvata entra in Home senza passare dal form di login', async () => {
    seedNativeSession()

    await router.replace('/')

    expect(router.currentRoute.value.name).toBe('Home')
  })

  /**
   * Caso 1 del brief: nessuna sessione, si atterra sul login e non su una
   * pagina privata, anche entrando direttamente su una rotta profonda.
   */
  test('senza sessione una rotta privata porta al login con il redirect', async () => {
    await router.replace('/activity?id=12')

    expect(router.currentRoute.value.name).toBe('Login')
    expect(router.currentRoute.value.query.redirect).toBe('/activity?id=12')
  })
})

describe('ripristino della sessione dallo storage nativo', () => {
  /**
   * REGRESSIONE ATTESA (oggi fallisce): `checkAuthStatus()` alza
   * `sessionChecked` PRIMA di attendere lo storage, quindi una seconda chiamata
   * che arriva mentre la prima e' ancora sul bridge esce subito e vede
   * `isAuthenticated === false` pur esistendo una sessione valida.
   * Sul web la finestra e' una microtask e non si nota; su Android dura quanto
   * due chiamate a SharedPreferences.
   */
  test('due chiamate sovrapposte vedono entrambe la sessione ripristinata', async () => {
    seedNativeSession()
    const auth = useAuthStore()

    const inFlight = auth.checkAuthStatus()
    await auth.checkAuthStatus()

    expect(auth.isAuthenticated, 'seconda chiamata durante il ripristino').toBe(true)

    await inFlight
    expect(auth.isAuthenticated).toBe(true)
  })

  /**
   * Stessa causa vista dal router: una seconda navigazione che parte mentre il
   * guard della prima e' fermo sullo storage manda l'utente al login pur avendo
   * la sessione salvata (il "logout apparente" ad ogni avvio).
   */
  test('due navigazioni sovrapposte non buttano fuori un utente autenticato', async () => {
    seedNativeSession()

    const first = router.replace('/home')
    const second = router.replace('/settings')
    await Promise.allSettled([first, second])

    expect(router.currentRoute.value.name).toBe('Settings')
  })

  /**
   * Un logout (401) che cade mentre il ripristino e' ancora sul bridge non deve
   * far resuscitare la sessione appena chiusa.
   */
  test('un logout durante il ripristino non fa resuscitare la sessione', async () => {
    seedNativeSession()
    const auth = useAuthStore()

    const inFlight = auth.checkAuthStatus()
    await auth.logout()
    await inFlight

    expect(auth.isAuthenticated, 'sessione risorta dopo il logout').toBe(false)
  })
})

describe('sessione scaduta durante l uso (401)', () => {
  test('porta al login con ?redirect= e il nuovo accesso torna dove si era', async () => {
    seedNativeSession()
    await router.replace('/activity?id=12')
    expect(router.currentRoute.value.path).toBe('/activity')

    const auth = useAuthStore()
    // Cio' che fa l'interceptor axios sul 401.
    await auth.logout(router.currentRoute.value.fullPath)

    expect(router.currentRoute.value.name).toBe('Login')
    const target = safeRedirectTarget(router.currentRoute.value.query.redirect)
    expect(target).toBe('/activity?id=12')

    // Nuovo accesso: cio' che fa LoginPage dopo `auth.login()`.
    auth.token = 'jwt-nuovo'
    auth.user = { ...SESSION_USER, role: 'USER' as const }
    await router.push(target!)

    expect(router.currentRoute.value.fullPath).toBe('/activity?id=12')
  })
})

describe('history vista dal tasto BACK di Android', () => {
  /**
   * Il BACK hardware su Android arriva a `WebView.goBack()` (Capacitor) oppure a
   * `history.go(-1)` (Ionic): in entrambi i casi conta la history del WebView.
   * Una navigazione bloccata dal guard non deve lasciare dietro di se' la voce
   * della pagina privata, altrimenti il BACK ci riporterebbe dentro.
   */
  test('la rotta privata bloccata non lascia una voce di history', async () => {
    await router.replace('/login')
    const before = window.history.length

    await router.push('/settings')

    expect(router.currentRoute.value.name).toBe('Login')
    expect(window.history.length - before, 'voci aggiunte dalla navigazione bloccata').toBeLessThanOrEqual(1)
  })

  /**
   * Il ritorno al login dopo un 401 usa `router.replace`: la pagina privata
   * NON resta nella history sotto il login. Con `push` il BACK ci tornava
   * sopra e il guard doveva rimbalzare una navigazione POP, che su Android
   * mandava fuori fase le viste di `ion-router-outlet` (form di login a
   * schermo con l'URL su /home). Sostituendo la voce non c'e' nulla da
   * rimbalzare.
   */
  test('il logout sostituisce la pagina privata invece di impilarci sopra', async () => {
    seedNativeSession()
    await router.replace('/home')
    const before = window.history.length

    const auth = useAuthStore()
    await auth.logout('/home')

    expect(router.currentRoute.value.name).toBe('Login')
    expect(window.history.length - before, 'voci aggiunte dal logout').toBe(0)
  })
})
