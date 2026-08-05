<template>
  <ion-page>
    <ion-content :fullscreen="true" class="ion-padding">
      <!--
        Esito "in attesa di approvazione": l'utente NON viene mandato al login,
        dove verrebbe respinto. Resta su una schermata che dice cosa è successo
        e cosa aspettarsi.
      -->
      <div v-if="pendingOutcome" class="registration-container">
        <div class="registration-header">
          <ion-icon :icon="mailUnreadOutline" class="header-icon" />
          <h1 class="title-peach">Request sent</h1>
        </div>

        <div class="form-wrapper glass-card outcome-card" role="status" aria-live="polite">
          <p class="outcome-message">{{ pendingOutcome }}</p>
          <ul class="outcome-steps">
            <li>Your account has been created but cannot sign in yet.</li>
            <li>An administrator reviews the request and you receive an email with the decision.</li>
            <li>After approval you sign in with the email and password you just chose.</li>
          </ul>
          <ion-button expand="block" class="pill-button gradient-outline" @click="goToLogin">
            Back to sign in
          </ion-button>
        </div>
      </div>

      <div v-else class="registration-container">
        <div class="registration-header">
          <ion-icon :icon="personAddOutline" class="header-icon" />
          <h1 class="title-peach">Registration</h1>
        </div>

        <div class="form-wrapper">
          <FormLegend />

          <ion-list lines="none">
            <ion-item :class="fieldClass('name')" class="glass-input" data-field="name">
              <ion-icon slot="start" :icon="personOutline" class="input-icon" />
              <ion-input
                  v-model="form.name"
                  label-placement="floating"
                  type="text"
                  autocomplete="given-name"
                  :aria-invalid="!!errorFor('name')"
              >
                <div slot="label">Name <RequiredMark /></div>
              </ion-input>
            </ion-item>
            <FieldError :message="errorFor('name')" />

            <ion-item :class="fieldClass('surname')" class="glass-input" data-field="surname">
              <ion-icon slot="start" :icon="personOutline" class="input-icon" />
              <ion-input
                  v-model="form.surname"
                  label-placement="floating"
                  type="text"
                  autocomplete="family-name"
                  :aria-invalid="!!errorFor('surname')"
              >
                <div slot="label">Surname <RequiredMark /></div>
              </ion-input>
            </ion-item>
            <FieldError :message="errorFor('surname')" />

            <ion-item :class="fieldClass('address')" class="glass-input" data-field="address">
              <ion-icon slot="start" :icon="locationOutline" class="input-icon" />
              <ion-input
                  v-model="form.address"
                  label-placement="floating"
                  type="text"
                  autocomplete="street-address"
                  :aria-invalid="!!errorFor('address')"
              >
                <div slot="label">Address <RequiredMark /></div>
              </ion-input>
            </ion-item>
            <FieldError :message="errorFor('address')" />

            <ion-item class="glass-input">
              <ion-icon slot="start" :icon="callOutline" class="input-icon" />
              <ion-input
                  v-model="form.phone"
                  label="Phone"
                  label-placement="floating"
                  type="tel"
                  :aria-label="'Phone'"
                  autocomplete="tel"
              />
            </ion-item>

            <ion-item :class="fieldClass('email')" class="glass-input" data-field="email">
              <ion-icon slot="start" :icon="mailOutline" class="input-icon" />
              <ion-input
                  v-model="form.email"
                  label-placement="floating"
                  type="email"
                  autocomplete="email"
                  :aria-invalid="!!errorFor('email')"
              >
                <div slot="label">Email <RequiredMark /></div>
              </ion-input>
            </ion-item>
            <FieldError :message="errorFor('email')" />

            <ion-item :class="fieldClass('password')" class="glass-input password-item" data-field="password">
              <ion-icon slot="start" :icon="keyOutline" class="input-icon" />
              <ion-input
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  label-placement="floating"
                  autocomplete="new-password"
                  :helper-text="PASSWORD_HINT"
                  :aria-invalid="!!errorFor('password')"
              >
                <div slot="label">Password <RequiredMark /></div>
              </ion-input>
              <ion-icon
                  slot="end"
                  :icon="showPassword ? eyeOffOutline : eyeOutline"
                  class="toggle-eye"
                  role="button"
                  tabindex="0"
                  :aria-label="showPassword ? 'Hide password' : 'Show password'"
                  @click="showPassword = !showPassword"
                  @keydown.enter="showPassword = !showPassword"
                  @keydown.space.prevent="showPassword = !showPassword"
              />
            </ion-item>
            <FieldError :message="errorFor('password')" />

            <ion-item
                class="glass-input"
                :class="{ 'item-has-value': !!form.birthday }"
                @click="openBirthdayModal"
                :detail="false"
                button
                aria-haspopup="dialog"
                :aria-expanded="isBirthdayOpen"
                :aria-label="birthdayAriaLabel"
            >
              <ion-icon slot="start" :icon="calendarOutline" class="input-icon" />
              <ion-label position="floating">Birthday</ion-label>
              <div class="custom-input-value">{{ formattedBirthday }}</div>
            </ion-item>

            <ion-item class="glass-input">
              <ion-icon slot="start" :icon="transgenderOutline" class="input-icon" />
              <ion-select
                  v-model="form.gender"
                  label="Gender"
                  label-placement="floating"
                  interface="popover"
                  :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
              >
                <ion-select-option value="male">Male</ion-select-option>
                <ion-select-option value="female">Female</ion-select-option>
                <ion-select-option value="other">Other</ion-select-option>
                <ion-select-option value="not_specified">Prefer not to say</ion-select-option>
              </ion-select>
            </ion-item>
          </ion-list>

          <ion-grid class="ion-margin-top">
            <ion-row class="ion-justify-content-around">
              <ion-col size="5">
                <ion-button expand="block" class="pill-button gradient-outline" @click="router.back()">Cancel</ion-button>
              </ion-col>
              <ion-col size="5">
                <!-- Premibile anche con il form incompleto: è la pressione a
                     dire quali campi mancano e come compilarli. -->
                <ion-button
                    expand="block"
                    :disabled="isLoading"
                    class="pill-button gradient-outline"
                    @click="handleRegister"
                >
                  Register
                </ion-button>
              </ion-col>
            </ion-row>
          </ion-grid>
        </div>
      </div>

    </ion-content>

    <!--
      Gli overlay stanno FUORI da `ion-content`, come fratelli di pagina.
      Dentro `ion-content` finivano nel contenitore di scorrimento: restavano
      nel DOM del form (un `ion-backdrop` sempre presente fra i campi, visibile
      a chiunque ispezioni la pagina) e dipendevano dal contesto di
      posizionamento dello scroller. Qui il loro unico contesto è `ion-page`.

      Modale dichiarativa (`:is-open`) e NON imperativa (`$el.present()`):
      con la variante imperativa il primo tap veniva perso (il custom element
      non era ancora idratato) e la `dismiss()` invocata dentro `ionChange`
      cadeva durante l'animazione di apertura, lasciando l'overlay smontato a
      metà (`show-modal` senza `overlay-hidden`, focus bloccato sulla modale,
      `body.backdrop-no-scroll` mai rimossa): da lì in poi NESSUN campo del form
      era più digitabile e il pulsante Register non si abilitava mai.
    -->
    <ion-modal
        class="birthday-modal"
        :is-open="isBirthdayOpen"
        :keep-contents-mounted="true"
        @didDismiss="isBirthdayOpen = false"
    >
      <!--
        `prefer-wheel` + `show-default-buttons`: la ruota permette di scorrere
        gli anni fino al 1900 senza uscire dalla dialog e il valore viene
        confermato SOLO con "Done". Prima ogni cambio di mese/anno emetteva
        `ionChange` e chiudeva la modale, costringendo a riaprirla a ogni passo.
      -->
      <ion-datetime
          presentation="date"
          prefer-wheel
          :value="dateIso"
          :min="MIN_BIRTHDAY"
          :max="MAX_BIRTHDAY"
          :show-default-buttons="true"
          done-text="Done"
          cancel-text="Cancel"
          aria-label="Date of birth"
          class="ion-dark"
          @ionChange="onBirthdaySelected"
          @ionCancel="isBirthdayOpen = false"
      />
    </ion-modal>

    <ion-loading :is-open="isLoading" message="Registering..." />
  </ion-page>
</template>

<script setup lang="ts">
import { reactive, ref, computed } from 'vue';
import {
  IonPage, IonContent, IonList, IonItem, IonInput, IonIcon,
  IonButton, IonGrid, IonRow, IonCol, IonSelect, IonSelectOption,
  IonLabel, IonDatetime, IonModal, IonLoading
} from '@ionic/vue';
import { useRouter } from 'vue-router';
import {
  personAddOutline, eyeOutline, eyeOffOutline,
  callOutline, mailOutline, mailUnreadOutline, personOutline,
  keyOutline, calendarOutline, transgenderOutline,
  locationOutline
} from 'ionicons/icons';
import dayjs from 'dayjs';
import { api, apiErrorMessage } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';
import {
  collectErrors, errorSummary, isBlank, isStrongPassword, isValidEmail,
  requiredMessage, useFormValidation,
  EMAIL_ERROR, PASSWORD_ERROR, PASSWORD_HINT
} from '@/composables/useValidation';
import RequiredMark from '@/components/RequiredMark.vue';
import FieldError from '@/components/FieldError.vue';
import FormLegend from '@/components/FormLegend.vue';

/**
 * Esito della registrazione restituito dal backend in `data`: `ACTIVE` quando
 * il dominio email è auto-approvato (vedi `RegistrationPolicy`), `PENDING`
 * quando serve la decisione di un amministratore.
 */
type RegistrationOutcome = 'PENDING' | 'ACTIVE';

interface RegisterResponse {
  success: boolean;
  message?: string;
  data?: RegistrationOutcome;
}

/* ---------- costanti ---------- */
/** Intervallo ragionevole per una data di nascita: nessuna data futura. */
const MIN_BIRTHDAY = '1900-01-01';
const MAX_BIRTHDAY = dayjs().format('YYYY-MM-DD');
/** Anno di partenza della ruota quando il campo è ancora vuoto. */
const DEFAULT_BIRTHDAY = '2000-01-01';

/* ---------- state ---------- */
const router         = useRouter();
const isBirthdayOpen = ref(false);
const isLoading      = ref(false);
const showPassword   = ref(false);
const dateIso        = ref<string>(DEFAULT_BIRTHDAY);

const form = reactive({
  name: '', surname: '', address: '', phone: '',
  email: '', password: '', birthday: '', gender: ''
});

const { showToast } = useToast();

/* ---------- computed ---------- */
const formattedBirthday = computed(() => form.birthday);
const birthdayAriaLabel = computed(() =>
    form.birthday ? `Birthday, ${form.birthday}` : 'Birthday, not set'
);
/* ---------- validazione ---------- */
/** Campi obbligatori, nell'ordine in cui compaiono nella form. */
const REQUIRED_ORDER = ['name', 'surname', 'address', 'email', 'password'] as const;
type RequiredField = (typeof REQUIRED_ORDER)[number];

const { errors, errorFor, fieldClass, validateOnSubmit } =
    useFormValidation<RequiredField>(() => collectErrors<RequiredField>([
      { field: 'name', invalid: isBlank(form.name), message: requiredMessage('Name') },
      { field: 'surname', invalid: isBlank(form.surname), message: requiredMessage('Surname') },
      { field: 'address', invalid: isBlank(form.address), message: requiredMessage('Address') },
      { field: 'email', invalid: isBlank(form.email), message: requiredMessage('Email') },
      { field: 'email', invalid: !isValidEmail(form.email), message: EMAIL_ERROR },
      { field: 'password', invalid: isBlank(form.password), message: requiredMessage('Password') },
      { field: 'password', invalid: !isStrongPassword(form.password), message: PASSWORD_ERROR }
    ]), REQUIRED_ORDER);

const openBirthdayModal = () => {
  isBirthdayOpen.value = true;
};

/**
 * Con `show-default-buttons` `ionChange` scatta SOLO alla conferma ("Done"),
 * quindi qui la chiusura è voluta. Il valore si legge dall'evento e non dal
 * `v-model`: l'ordine dei due handler sullo stesso `ionChange` non è garantito.
 */
const onBirthdaySelected = (ev: CustomEvent<{ value?: string | string[] | null }>) => {
  const value = ev.detail?.value;
  const iso = Array.isArray(value) ? value[0] : value;

  if (iso) {
    dateIso.value = iso;
    form.birthday = dayjs(iso).format('DD-MM-YYYY');
  }
  isBirthdayOpen.value = false;
};

/* ---------- registration ---------- */

/** Messaggio dell'esito in attesa di approvazione; null finché non accade. */
const pendingOutcome = ref<string | null>(null);

const goToLogin = () => router.push({ name: 'Login' });

const PENDING_FALLBACK =
    'Registration received. An administrator has to approve your account before you can '
    + 'sign in; we will email you as soon as that happens.';

async function handleRegister() {
  if (!(await validateOnSubmit())) {
    await showToast(errorSummary(errors.value), 'danger');
    return;
  }

  isLoading.value = true;
  try {
    const { data } = await api.post<RegisterResponse>('/api/auth/register', { ...form });
    if (!data?.success) throw new Error(data?.message ?? 'Unknown error');

    // Il backend distingue i due esiti in `data`: un account PENDING non può
    // ancora autenticarsi, quindi mandarlo al login sarebbe un vicolo cieco.
    if (data.data === 'PENDING') {
      pendingOutcome.value = data.message || PENDING_FALLBACK;
      return;
    }

    showToast(data.message || 'Registered successfully!', 'success');
    // Lo spinner si chiude PRIMA di navigare: se la pagina si smonta con
    // l'overlay ancora presentato, nessuno esegue più la sua `dismiss()` e la
    // `body.backdrop-no-scroll` resta appesa alla pagina di destinazione.
    isLoading.value = false;
    await router.push({ name: 'Login' });
  } catch (err: unknown) {
    showToast(apiErrorMessage(err, 'Unexpected error'), 'danger');
  } finally {
    isLoading.value = false;
  }
}

</script>

<style scoped>
.registration-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: var(--space-6) 0;
}
.registration-header {
  text-align: center;
  margin-bottom: var(--space-6);
}
.header-icon {
  font-size: var(--space-10);
  color: var(--mauve);
  margin-bottom: var(--space-2);
}
.form-wrapper {
  max-width: 450px;
  width: 100%;
}
.outcome-card {
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}
.outcome-message {
  margin: 0;
  font-size: var(--font-base);
  line-height: 1.5;
  color: var(--text);
}
.outcome-steps {
  margin: 0;
  padding-left: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
  font-size: var(--font-sm);
  color: var(--subtext0);
  line-height: 1.5;
}
ion-item.glass-input {
  --inner-padding-top: 4px;
  --inner-padding-bottom: 4px;
  --min-height: var(--touch-target);
  font-size: var(--font-base);
}
ion-input,
ion-select {
  font-size: var(--font-base);
  --padding-start: 0;
  --padding-end: 0;
}
ion-item.ion-invalid {
  --highlight-color-focused: var(--ion-color-danger);
  --background: rgba(var(--ion-color-danger-rgb), 0.1);
}
.password-item ion-icon.toggle-eye {
  cursor: pointer;
  font-size: 1.2rem;
}
.custom-input-value {
  width: 100%;
  text-align: start;
  font-size: inherit;
  color: var(--ion-text-color);
  padding: var(--space-2) 0;
  min-height: calc(1em + 16px);
}
ion-icon.input-icon {
  color: var(--peach);
  font-size: var(--font-lg);
  margin-right: var(--space-2);
}
ion-icon.toggle-eye {
  color: var(--peach);
}
ion-select::part(icon) {
  color: var(--peach);
}
/* La modale della data si adatta al picker: prima era un pannello quasi vuoto
   a tutta pagina con la ruota schiacciata in un angolo. */
ion-modal.birthday-modal {
  --width: fit-content;
  --min-width: 290px;
  --height: fit-content;
  --border-radius: var(--radius-lg, 16px);
  --box-shadow: 0 24px 48px rgba(0, 0, 0, 0.45);
}
ion-modal.birthday-modal ion-datetime {
  height: auto;
}
</style>