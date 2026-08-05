import { describe, expect, test } from 'vitest'
import { safeRedirectTarget } from '@/router'

/**
 * Il guard mette in `?redirect=` la pagina che l'utente stava aprendo e la
 * login la riapre dopo l'accesso. Il valore arriva dalla barra degli indirizzi,
 * quindi è input non fidato: se venisse usato così com'è, un link confezionato
 * ad arte (`/login?redirect=https://phishing.example/login`) trasformerebbe il
 * nostro login in un trampolino verso un sito terzo, con l'utente convinto di
 * essere ancora dentro Chronogram.
 */
describe('safeRedirectTarget', () => {
  test('accetta un path interno, con query e hash', () => {
    expect(safeRedirectTarget('/activity')).toBe('/activity')
    expect(safeRedirectTarget('/activity?id=12&name=Run')).toBe('/activity?id=12&name=Run')
    expect(safeRedirectTarget('/details#top')).toBe('/details#top')
  })

  test('rifiuta gli URL assoluti e i protocol-relative', () => {
    expect(safeRedirectTarget('https://phishing.example/login')).toBeNull()
    expect(safeRedirectTarget('//phishing.example/login')).toBeNull()
    expect(safeRedirectTarget('http://localhost:5173/home')).toBeNull()
  })

  test('rifiuta gli schemi eseguibili e i valori non validi', () => {
    expect(safeRedirectTarget('javascript:alert(1)')).toBeNull()
    expect(safeRedirectTarget('activity')).toBeNull()
    expect(safeRedirectTarget('')).toBeNull()
    expect(safeRedirectTarget(undefined)).toBeNull()
    expect(safeRedirectTarget(['/a', '/b'])).toBeNull()
  })
})
