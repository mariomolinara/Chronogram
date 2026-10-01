import { describe, expect, test } from 'vitest'

import {
  computeReminderTimes,
  firstMomentOutsideQuietHours,
  hasQuietHours,
  isWithinQuietHours,
  normalizeHour,
  REMINDER_ID_BASE,
  REMINDER_ID_MAX,
  type ReminderSchedule
} from '@/composables/useLocalReminders'

/**
 * Calcolo degli istanti dei promemoria locali (app nativa).
 *
 * È la parte che può rompere la feature in silenzio: un errore qui significa
 * promemoria in mezzo alla notte oppure — peggio, perché invisibile —
 * promemoria che non arrivano mai. Le funzioni sono pure proprio per poter
 * essere verificate senza il bridge Capacitor.
 *
 * NOTA sul fuso: si costruiscono le date con `new Date(anno, mese, giorno, ora)`,
 * cioè nell'ora LOCALE, perché la fascia di silenzio è definita in ore locali
 * (`getHours()`). Usare stringhe ISO con `Z` renderebbe i test dipendenti dal
 * fuso della macchina che li esegue.
 */

const schedule = (overrides: Partial<ReminderSchedule> = {}): ReminderSchedule => ({
  intervalMinutes: 60,
  quietHoursStart: null,
  quietHoursEnd: null,
  ...overrides
})

/** 1 marzo 2026 (nessun cambio di ora legale nei giorni usati dai test). */
const at = (hour: number, minute = 0, day = 1) => new Date(2026, 2, day, hour, minute, 0, 0)

const hhmm = (date: Date) =>
    `${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`

describe('normalizeHour', () => {
  test('accetta solo interi 0-23', () => {
    expect(normalizeHour(0)).toBe(0)
    expect(normalizeHour(23)).toBe(23)
    expect(normalizeHour(8.7)).toBe(8)
  })

  test('scarta i valori fuori scala e i non-numeri', () => {
    // Un 24 lasciato passare renderebbe la fascia impossibile da valutare.
    expect(normalizeHour(24)).toBeNull()
    expect(normalizeHour(-1)).toBeNull()
    expect(normalizeHour(null)).toBeNull()
    expect(normalizeHour('22')).toBeNull()
    expect(normalizeHour(Number.NaN)).toBeNull()
  })
})

describe('hasQuietHours', () => {
  test('serve che entrambi gli estremi siano validi', () => {
    expect(hasQuietHours(schedule({ quietHoursStart: 22, quietHoursEnd: 8 }))).toBe(true)
    expect(hasQuietHours(schedule({ quietHoursStart: 22, quietHoursEnd: null }))).toBe(false)
    expect(hasQuietHours(schedule({ quietHoursStart: null, quietHoursEnd: 8 }))).toBe(false)
  })

  /**
   * `start === end` è quasi certamente un errore di configurazione: letto alla
   * lettera spegnerebbe i promemoria per 24 ore senza che l'utente capisca.
   */
  test('start uguale a end vale "nessun silenzio", non "silenzio tutto il giorno"', () => {
    expect(hasQuietHours(schedule({ quietHoursStart: 9, quietHoursEnd: 9 }))).toBe(false)
  })
})

describe('isWithinQuietHours', () => {
  const overnight = schedule({ quietHoursStart: 22, quietHoursEnd: 8 })

  test('gestisce il wrap-around di mezzanotte (22 -> 8)', () => {
    expect(isWithinQuietHours(at(22, 0), overnight)).toBe(true)
    expect(isWithinQuietHours(at(23, 59), overnight)).toBe(true)
    expect(isWithinQuietHours(at(0, 0), overnight)).toBe(true)
    expect(isWithinQuietHours(at(7, 59), overnight)).toBe(true)
  })

  test('gli estremi: l inizio è silenzio, la fine non lo è più', () => {
    expect(isWithinQuietHours(at(21, 59), overnight)).toBe(false)
    expect(isWithinQuietHours(at(8, 0), overnight)).toBe(false)
    expect(isWithinQuietHours(at(13, 0), overnight)).toBe(false)
  })

  test('gestisce anche una fascia dentro lo stesso giorno (13 -> 15)', () => {
    const daytime = schedule({ quietHoursStart: 13, quietHoursEnd: 15 })
    expect(isWithinQuietHours(at(12, 59), daytime)).toBe(false)
    expect(isWithinQuietHours(at(13, 0), daytime)).toBe(true)
    expect(isWithinQuietHours(at(14, 59), daytime)).toBe(true)
    expect(isWithinQuietHours(at(15, 0), daytime)).toBe(false)
    expect(isWithinQuietHours(at(3, 0), daytime)).toBe(false)
  })

  test('senza fascia configurata nessun istante è silenzioso', () => {
    expect(isWithinQuietHours(at(3, 0), schedule())).toBe(false)
  })
})

describe('firstMomentOutsideQuietHours', () => {
  const overnight = schedule({ quietHoursStart: 22, quietHoursEnd: 8 })

  test('un istante già udibile resta dov è', () => {
    expect(hhmm(firstMomentOutsideQuietHours(at(9, 30), overnight))).toBe('01 09:30')
  })

  test('la notte prima di mezzanotte scivola alle 08:00 del giorno dopo', () => {
    expect(hhmm(firstMomentOutsideQuietHours(at(23, 15), overnight))).toBe('02 08:00')
  })

  test('la notte dopo mezzanotte scivola alle 08:00 dello stesso giorno', () => {
    expect(hhmm(firstMomentOutsideQuietHours(at(2, 5), overnight))).toBe('01 08:00')
    expect(hhmm(firstMomentOutsideQuietHours(at(7, 59), overnight))).toBe('01 08:00')
  })

  test('funziona anche con una fascia diurna', () => {
    const daytime = schedule({ quietHoursStart: 13, quietHoursEnd: 15 })
    expect(hhmm(firstMomentOutsideQuietHours(at(13, 30), daytime))).toBe('01 15:00')
  })
})

describe('computeReminderTimes - cadenza semplice', () => {
  test('il primo promemoria è a un intervallo di distanza, poi a cadenza regolare', () => {
    const times = computeReminderTimes(at(9, 0), schedule({ intervalMinutes: 60 }), {
      horizonHours: 4
    })

    expect(times.map(hhmm)).toEqual(['01 10:00', '01 11:00', '01 12:00', '01 13:00'])
  })

  test('rispetta il tetto al numero di notifiche in coda', () => {
    const times = computeReminderTimes(at(0, 0), schedule({ intervalMinutes: 15 }), {
      horizonHours: 48,
      maxCount: 10
    })

    expect(times).toHaveLength(10)
    expect(hhmm(times[0])).toBe('01 00:15')
    expect(hhmm(times[9])).toBe('01 02:30')
  })

  test('non supera l orizzonte richiesto', () => {
    const times = computeReminderTimes(at(9, 0), schedule({ intervalMinutes: 480 }), {
      horizonHours: 24
    })

    expect(times).toHaveLength(3)
    expect(hhmm(times[2])).toBe('02 09:00')
  })

  test('una cadenza non valida non produce nulla invece di un ciclo infinito', () => {
    expect(computeReminderTimes(at(9, 0), schedule({ intervalMinutes: 0 }))).toEqual([])
    expect(computeReminderTimes(at(9, 0), schedule({ intervalMinutes: -30 }))).toEqual([])
    expect(computeReminderTimes(at(9, 0), schedule(), { maxCount: 0 })).toEqual([])
  })
})

describe('computeReminderTimes - fascia di silenzio', () => {
  const overnight = { quietHoursStart: 22, quietHoursEnd: 8 }

  test('nessun promemoria cade dentro la fascia notturna', () => {
    const times = computeReminderTimes(
        at(20, 0),
        schedule({ intervalMinutes: 60, ...overnight }),
        { horizonHours: 24 }
    )

    expect(times.length).toBeGreaterThan(0)
    for (const time of times) {
      expect(isWithinQuietHours(time, schedule(overnight))).toBe(false)
    }
  })

  /**
   * Con una cadenza breve, i candidati notturni vengono tutti spostati a fine
   * fascia: senza la distanza minima si otterrebbero decine di notifiche
   * identiche tutte alle 08:00.
   */
  test('la notte produce UN solo promemoria al risveglio, non una raffica', () => {
    const times = computeReminderTimes(
        at(21, 30),
        schedule({ intervalMinutes: 15, ...overnight }),
        { horizonHours: 12 }
    )

    const atEightOClock = times.filter((time) => hhmm(time) === '02 08:00')
    expect(atEightOClock).toHaveLength(1)
    // Prima della fascia la cadenza resta quella scelta.
    expect(hhmm(times[0])).toBe('01 21:45')
    expect(hhmm(times[1])).toBe('02 08:00')
  })

  test('due promemoria consecutivi non sono mai più vicini della cadenza scelta', () => {
    const times = computeReminderTimes(
        at(21, 0),
        schedule({ intervalMinutes: 30, ...overnight }),
        { horizonHours: 36 }
    )

    for (let i = 1; i < times.length; i += 1) {
      const gapMinutes = (times[i].getTime() - times[i - 1].getTime()) / 60_000
      expect(gapMinutes).toBeGreaterThanOrEqual(30)
    }
  })

  /**
   * Il caso che lo scarto secco dei candidati notturni romperebbe: la cadenza
   * giornaliera cade sempre nella stessa ora, e se quell'ora è dentro la fascia
   * l'utente non riceverebbe MAI un promemoria.
   */
  test('la cadenza giornaliera dentro la fascia viene spostata, non perduta', () => {
    const times = computeReminderTimes(
        at(23, 0),
        schedule({ intervalMinutes: 1440, ...overnight }),
        { horizonHours: 48 }
    )

    expect(times.length).toBeGreaterThan(0)
    expect(hhmm(times[0])).toBe('03 08:00')
  })

  test('una fascia che copre quasi tutto il giorno lascia comunque un promemoria', () => {
    const times = computeReminderTimes(
        at(12, 0),
        schedule({ intervalMinutes: 60, quietHoursStart: 20, quietHoursEnd: 19 }),
        { horizonHours: 24 }
    )

    expect(times.length).toBeGreaterThan(0)
    expect(times.every((time) => time.getHours() === 19)).toBe(true)
  })
})

describe('range di id riservato', () => {
  /**
   * La cancellazione filtra su questo range: se si allargasse fino a toccare
   * gli id di un'altra feature, un ri-scheduling dei promemoria ne
   * cancellerebbe le notifiche.
   */
  test('gli id del batch stanno dentro il range dichiarato', () => {
    const times = computeReminderTimes(at(0, 0), schedule({ intervalMinutes: 15 }), {
      horizonHours: 48,
      maxCount: 60
    })

    expect(times).toHaveLength(60)
    expect(REMINDER_ID_BASE).toBe(9000)
    expect(REMINDER_ID_BASE + times.length - 1).toBeLessThanOrEqual(REMINDER_ID_MAX)
  })
})
