import { api } from '@/composables/useApi';

/**
 * Profilo dell'utente autenticato.
 *
 * Contratto del backend (`/chronogram`):
 *  - `GET  /api/profile/me`     → `ProfileDto`
 *  - `POST /api/profile/update` → `ProfileDto` aggiornato
 *
 * La conversione DTO ⇄ form sta qui e non nella vista: è la parte che si può
 * sbagliare in silenzio (campi assenti, date in formati diversi) ed è l'unica
 * che vale la pena coprire con dei test.
 */

/** Come arriva il profilo dal backend. */
export interface ProfileDto {
  name: string;
  surname: string;
  address: string;
  phone: string;
  /** Non modificabile: l'email è l'identità dell'account. */
  email: string;
  /** `YYYY-MM-DD`, oppure null se mai indicata. */
  birthday: string | null;
  gender: string;
}

/**
 * Come vive il profilo dentro la form: solo stringhe, perché `v-model` su un
 * `ion-input` non deve mai ricevere `null` (Ionic lo stampa come "null").
 * `birthday` resta in ISO: è il formato che vuole `ion-datetime`.
 */
export interface ProfileForm {
  name: string;
  surname: string;
  address: string;
  phone: string;
  email: string;
  birthday: string;
  gender: string;
}

/** Corpo di `POST /api/profile/update`: l'email non c'è, non è modificabile. */
export interface ProfileUpdatePayload {
  name: string;
  surname: string;
  address: string;
  phone: string;
  birthday: string | null;
  gender: string;
}

/** Form vuota, usata come stato iniziale prima del caricamento. */
export const EMPTY_PROFILE_FORM: ProfileForm = {
  name: '', surname: '', address: '', phone: '', email: '', birthday: '', gender: ''
};

const asText = (value: unknown): string =>
    typeof value === 'string' ? value : (value === null || value === undefined ? '' : String(value));

/**
 * Normalizza una data a `YYYY-MM-DD`.
 *
 * Il backend dichiara una `LocalDate`, ma un serializzatore configurato
 * diversamente può mandare `2000-05-01T00:00:00`: si tiene la sola parte di
 * data invece di lasciare che `ion-datetime` riceva un valore che non capisce.
 */
export function normalizeIsoDate(value: unknown): string {
  const text = asText(value).trim();
  if (!text) {
    return '';
  }
  const candidate = text.slice(0, 10);
  return /^\d{4}-\d{2}-\d{2}$/.test(candidate) ? candidate : '';
}

/** Dal DTO del backend allo stato della form (campi assenti → stringa vuota). */
export function toProfileForm(dto: Partial<ProfileDto> | null | undefined): ProfileForm {
  if (!dto || typeof dto !== 'object') {
    return { ...EMPTY_PROFILE_FORM };
  }
  return {
    name: asText(dto.name),
    surname: asText(dto.surname),
    address: asText(dto.address),
    phone: asText(dto.phone),
    email: asText(dto.email),
    birthday: normalizeIsoDate(dto.birthday),
    gender: asText(dto.gender)
  };
}

/**
 * Dallo stato della form al corpo della richiesta.
 *
 * Gli spazi ai bordi si perdono qui e non a valle: un nome " Giulia " salvato
 * così tornerebbe identico al prossimo caricamento. `birthday` diventa `null`
 * quando non è indicata, come nel DTO in lettura.
 */
export function toProfileUpdatePayload(form: ProfileForm): ProfileUpdatePayload {
  return {
    name: form.name.trim(),
    surname: form.surname.trim(),
    address: form.address.trim(),
    phone: form.phone.trim(),
    birthday: normalizeIsoDate(form.birthday) || null,
    gender: form.gender
  };
}

/**
 * Data di nascita in formato leggibile (`DD/MM/YYYY`).
 *
 * Ricomposta a mano e non con `new Date(iso)`: quest'ultima interpreta la
 * stringa in UTC e a ovest di Greenwich mostrerebbe il giorno precedente
 * (stessa cautela già presa in AdminUsersPage).
 */
export function formatBirthday(iso: string): string {
  const normalized = normalizeIsoDate(iso);
  if (!normalized) {
    return '';
  }
  const [year, month, day] = normalized.split('-');
  return `${day}/${month}/${year}`;
}

/* ---------- chiamate ---------- */

/**
 * Estrae il profilo dalla risposta, che sia l'oggetto nudo (il contratto
 * concordato) o l'envelope `ApiResponse { success, data }` usato altrove nel
 * backend.
 *
 * Non è pignoleria: senza questa tolleranza un envelope inatteso produrrebbe
 * una form tutta vuota dall'aria innocente, e il primo salvataggio
 * sovrascriverebbe il profilo con dei campi vuoti.
 */
export function unwrapProfilePayload(payload: unknown): Partial<ProfileDto> | null {
  if (!payload || typeof payload !== 'object') {
    return null;
  }
  const candidate = payload as Record<string, unknown>;
  const looksLikeProfile = 'email' in candidate || 'name' in candidate || 'surname' in candidate;
  if (looksLikeProfile) {
    return candidate as Partial<ProfileDto>;
  }
  if (candidate.data && typeof candidate.data === 'object') {
    return candidate.data as Partial<ProfileDto>;
  }
  return null;
}

export async function fetchProfile(): Promise<ProfileForm> {
  const { data } = await api.get<ProfileDto>('/api/profile/me');
  const parsed = unwrapProfilePayload(data);

  // Meglio l'errore con il riprova che una form vuota che sembra un profilo
  // senza dati: da lì il primo salvataggio cancellerebbe tutto.
  if (!parsed) {
    throw new Error('The server returned an unexpected profile format.');
  }
  return toProfileForm(parsed);
}

export async function updateProfile(form: ProfileForm): Promise<ProfileForm> {
  const { data } = await api.post<ProfileDto>('/api/profile/update', toProfileUpdatePayload(form));
  // Il backend risponde con l'oggetto aggiornato: si riparte da quello, così la
  // form mostra ciò che è stato davvero salvato (normalizzazioni comprese).
  // Se la risposta non porta il profilo (solo un esito), si tiene quello che si
  // è appena inviato: svuotare la form dopo un salvataggio riuscito sarebbe il
  // peggiore dei feedback.
  return toProfileForm(unwrapProfilePayload(data) ?? form);
}

export async function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  await api.post('/api/profile/change-password', { currentPassword, newPassword });
}

/**
 * Cancella definitivamente l'account dell'utente autenticato.
 *
 * Contratto del backend: `POST /api/profile/delete-account` con corpo
 * `{ reasons: string[] }` — la lista è facoltativa e serve solo a capire perché
 * si abbandona l'app. Al ritorno l'account non esiste più: il token che il
 * client ha in mano non vale più nulla, quindi chi chiama DEVE chiudere la
 * sessione subito dopo.
 *
 * La normalizzazione sta qui e non nella vista: voci vuote o con soli spazi
 * sarebbero rumore nelle statistiche, e "nessun motivo selezionato" viaggia come
 * array vuoto e non come `null`, perché il campo è opzionale, non nullable.
 */
export async function deleteAccount(reasons: readonly string[] = []): Promise<void> {
  const cleaned = reasons.map((reason) => reason.trim()).filter((reason) => reason.length > 0);
  await api.post('/api/profile/delete-account', { reasons: cleaned });
}
