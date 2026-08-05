import { computed, nextTick, ref, type ComputedRef, type Ref } from 'vue';

/**
 * Validazione condivisa dei form.
 *
 * Prima di questo modulo ogni pagina riscriveva le stesse regex (`isValidEmail`
 * e `isStrongPassword` erano duplicate identiche in registrazione, recupero e
 * reset password) e disabilitava il pulsante di invio finché il form non era
 * valido: l'utente non poteva premere e nessuno gli diceva quale campo mancava.
 *
 * Il pattern applicato in tutte le viste è l'opposto:
 *  - il pulsante resta premibile (si disabilita solo durante la chiamata);
 *  - gli errori inline compaiono SOLO dopo il primo tentativo di invio, così
 *    non si aggredisce chi sta ancora scrivendo;
 *  - dal primo tentativo in poi si aggiornano da soli mentre l'utente corregge;
 *  - il messaggio dice COME compilare il campo, non solo che è sbagliato.
 */

/* ─────────────────────────── costanti condivise ─────────────────────────── */

/** Regola applicata da `isStrongPassword`, mostrata come helper text nei form. */
export const PASSWORD_HINT =
    'At least 8 characters, with upper and lower case, a digit and a symbol.';

/** Messaggio d'errore per un indirizzo email non valido. */
export const EMAIL_ERROR = 'Enter a valid email address (e.g. name@example.com)';

/** Messaggio d'errore per una password che non rispetta `PASSWORD_HINT`. */
export const PASSWORD_ERROR =
    'Password must be at least 8 characters with uppercase, lowercase, number and symbol';

/** Legenda da mostrare sotto il titolo di ogni form con campi obbligatori. */
export const REQUIRED_LEGEND = 'Fields marked with * are required';

const EMAIL_PATTERN = /^[\w-.]+@([\w-]+\.)+[\w-]{2,}$/;
const STRONG_PASSWORD_PATTERN = /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$/;

/* ─────────────────────────────── predicati ──────────────────────────────── */

/** Email accettata dal backend: niente spazi, dominio con almeno un punto. */
export const isValidEmail = (value: string | null | undefined): boolean =>
    EMAIL_PATTERN.test(value ?? '');

/** Password robusta: maiuscola, minuscola, cifra, simbolo, almeno 8 caratteri. */
export const isStrongPassword = (value: string | null | undefined): boolean =>
    STRONG_PASSWORD_PATTERN.test(value ?? '');

/** Campo testuale considerato vuoto (spazi soli inclusi). */
export const isBlank = (value: string | null | undefined): boolean =>
    (value ?? '').trim().length === 0;

/** Messaggio standard per un campo obbligatorio lasciato vuoto. */
export const requiredMessage = (label: string): string => `${label} is required`;

/* ──────────────────────────── mappe di errori ───────────────────────────── */

/** Errori per campo: chiave = nome del campo, valore = messaggio da mostrare. */
export type FieldErrors<K extends string = string> = Partial<Record<K, string>>;

/** Regola singola: `invalid` è già valutata da chi costruisce l'elenco. */
export interface ValidationRule<K extends string = string> {
  field: K;
  /** true quando il campo NON è valido. */
  invalid: boolean;
  message: string;
}

/**
 * Costruisce la mappa degli errori dall'elenco delle regole, nell'ordine in cui
 * sono dichiarate. Per lo stesso campo vince la prima regola violata: è quella
 * che descrive il problema più elementare ("manca" prima di "formato errato").
 */
export function collectErrors<K extends string>(
    rules: ReadonlyArray<ValidationRule<K>>
): FieldErrors<K> {
  const errors: FieldErrors<K> = {};
  for (const rule of rules) {
    if (rule.invalid && errors[rule.field] === undefined) {
      errors[rule.field] = rule.message;
    }
  }
  return errors;
}

/**
 * Primo campo invalido secondo l'ordine visivo del form (`order`): è quello a
 * cui portare focus e scorrimento. Senza `order` vale l'ordine di inserimento
 * nella mappa.
 */
export function firstInvalidField<K extends string>(
    errors: FieldErrors<K>,
    order?: ReadonlyArray<K>
): K | null {
  if (order && order.length > 0) {
    return order.find((field) => errors[field] !== undefined) ?? null;
  }
  const keys = Object.keys(errors) as K[];
  return keys.length > 0 ? keys[0] : null;
}

/**
 * Testo del toast riassuntivo. Con un solo errore si ripete il messaggio del
 * campo (l'utente sa già cosa fare senza cercarlo nella pagina); con più errori
 * si dichiara quanti sono, perché elencarli tutti in un toast è illeggibile.
 */
export function errorSummary(errors: FieldErrors): string {
  const messages = Object.values(errors).filter(
      (message): message is string => typeof message === 'string' && message.length > 0
  );
  if (messages.length === 0) {
    return '';
  }
  if (messages.length === 1) {
    return messages[0];
  }
  return `Please fix the ${messages.length} highlighted fields`;
}

/* ───────────────────────── focus del primo errore ───────────────────────── */

/**
 * Porta l'utente sul primo campo invalido: senza questo passaggio, in un form
 * più lungo dello schermo, l'errore può restare fuori dalla vista e il tap sul
 * pulsante sembra non fare nulla.
 *
 * Il campo si individua con `data-field="<nome>"` sull'`ion-item` (o
 * direttamente sul controllo), così non serve una `ref` per ogni input.
 */
export async function focusFirstInvalid(
    field: string,
    root: ParentNode = document
): Promise<void> {
  await nextTick();

  const host = root.querySelector<HTMLElement>(`[data-field="${field}"]`);
  if (!host) {
    return;
  }

  // jsdom non implementa scrollIntoView: il guard tiene i test verdi.
  if (typeof host.scrollIntoView === 'function') {
    host.scrollIntoView({ behavior: 'smooth', block: 'center' });
  }

  const control = host.matches('ion-input, ion-textarea, ion-select')
      ? host
      : host.querySelector<HTMLElement>('ion-input, ion-textarea, ion-select, input, textarea');

  const focusable = control as
      | (HTMLElement & { setFocus?: () => Promise<void> })
      | null;
  if (!focusable) {
    return;
  }

  try {
    // I controlli Ionic espongono `setFocus()`: `focus()` sull'host non entra
    // nello shadow DOM e il cursore non arriverebbe nel campo.
    if (typeof focusable.setFocus === 'function') {
      await focusable.setFocus();
    } else {
      focusable.focus?.();
    }
  } catch {
    /* elemento non ancora idratato: ininfluente */
  }
}

/* ──────────────────────────── stato del form ────────────────────────────── */

export interface FormValidation<K extends string> {
  /** true dopo la prima pressione del pulsante di invio. */
  submitAttempted: Ref<boolean>;
  /** Errori da MOSTRARE: vuoti finché non si è tentato l'invio. */
  errors: ComputedRef<FieldErrors<K>>;
  /** Errori CALCOLATI, indipendenti dal tentativo di invio. */
  pendingErrors: ComputedRef<FieldErrors<K>>;
  /** true quando non c'è alcun errore. */
  isValid: ComputedRef<boolean>;
  /** Messaggio da mostrare sotto un campo, o undefined. */
  errorFor: (field: K) => string | undefined;
  /** Classe da applicare all'`ion-item` del campo. */
  fieldClass: (field: K) => Record<string, boolean>;
  /**
   * Da chiamare all'inizio dell'handler di invio: accende i messaggi inline,
   * porta il focus sul primo campo invalido e ritorna false se il form non è
   * compilabile così com'è.
   */
  validateOnSubmit: () => Promise<boolean>;
  /** Rimette il form nello stato "mai inviato" (dopo un salvataggio riuscito). */
  reset: () => void;
}

/**
 * @param validator funzione che legge lo stato reattivo del form e ritorna gli
 *                  errori per campo (tipicamente costruita con `collectErrors`)
 * @param order     ordine visivo dei campi, per scegliere dove portare il focus
 */
export function useFormValidation<K extends string>(
    validator: () => FieldErrors<K>,
    order?: ReadonlyArray<K>
): FormValidation<K> {
  const submitAttempted = ref(false);

  const pendingErrors = computed(() => validator());
  const errors = computed<FieldErrors<K>>(() =>
      submitAttempted.value ? pendingErrors.value : {}
  );
  const isValid = computed(() => Object.keys(pendingErrors.value).length === 0);

  const errorFor = (field: K): string | undefined => errors.value[field];

  const fieldClass = (field: K): Record<string, boolean> => ({
    'ion-invalid': errors.value[field] !== undefined
  });

  async function validateOnSubmit(): Promise<boolean> {
    submitAttempted.value = true;
    const current = pendingErrors.value;
    const first = firstInvalidField(current, order);
    if (first === null) {
      return true;
    }
    await focusFirstInvalid(first);
    return false;
  }

  function reset(): void {
    submitAttempted.value = false;
  }

  return {
    submitAttempted,
    errors,
    pendingErrors,
    isValid,
    errorFor,
    fieldClass,
    validateOnSubmit,
    reset
  };
}