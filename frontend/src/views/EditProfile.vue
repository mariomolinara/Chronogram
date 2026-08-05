<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-button aria-label="Back to settings" @click="goBack">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" />
          </ion-button>
        </ion-buttons>
        <ion-title>Edit Profile</ion-title>
        <ion-buttons slot="end">
          <ion-icon :icon="personCircleOutline" class="profile-icon" aria-hidden="true" />
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <!-- Caricamento: scheletro in pagina e non un overlay, che resterebbe
           presentato sopra al form e bloccherebbe lo scorrimento. -->
      <div v-if="isLoading" class="state-block" role="status" aria-live="polite">
        <ion-spinner name="crescent" />
        <p>Loading your profile…</p>
      </div>

      <!-- Errore di caricamento: la pagina non ha nulla da mostrare, ma dice
           cosa è successo e offre il riprova (convenzione di AdminUsersPage). -->
      <div v-else-if="loadError" class="state-block state-block--error" role="alert">
        <ion-icon :icon="alertCircleOutline" />
        <p>{{ loadError }}</p>
        <ion-button fill="outline" size="small" @click="loadProfile">
          <ion-icon slot="start" :icon="refreshOutline" aria-hidden="true" />
          Retry
        </ion-button>
      </div>

      <template v-else>
        <FormLegend />

        <ion-list lines="none">
          <ion-item :class="fieldClass('name')" class="glass-input" data-field="name">
            <ion-input
                v-model="form.name"
                label-placement="floating"
                autocomplete="given-name"
                type="text"
                :maxlength="MAX_NAME"
                :aria-invalid="!!errorFor('name')"
            >
              <div slot="label">Name <RequiredMark /></div>
            </ion-input>
          </ion-item>
          <FieldError :message="errorFor('name')" />

          <ion-item :class="fieldClass('surname')" class="glass-input" data-field="surname">
            <ion-input
                v-model="form.surname"
                label-placement="floating"
                autocomplete="family-name"
                type="text"
                :maxlength="MAX_SURNAME"
                :aria-invalid="!!errorFor('surname')"
            >
              <div slot="label">Surname <RequiredMark /></div>
            </ion-input>
          </ion-item>
          <FieldError :message="errorFor('surname')" />

          <ion-item :class="fieldClass('address')" class="glass-input" data-field="address">
            <ion-input
                v-model="form.address"
                label-placement="floating"
                autocomplete="street-address"
                type="text"
                :maxlength="MAX_ADDRESS"
                :aria-invalid="!!errorFor('address')"
            >
              <div slot="label">Address <RequiredMark /></div>
            </ion-input>
          </ion-item>
          <FieldError :message="errorFor('address')" />

          <ion-item :class="fieldClass('phone')" class="glass-input" data-field="phone">
            <ion-input
                v-model="form.phone"
                label="Phone"
                label-placement="floating"
                autocomplete="tel"
                type="tel"
                :maxlength="MAX_PHONE"
                :aria-invalid="!!errorFor('phone')"
            />
          </ion-item>
          <FieldError :message="errorFor('phone')" />

          <!-- L'email identifica l'account: si mostra perché è il dato che
               l'utente cerca per primo, ma non è modificabile da qui.
               `readonly` e non `disabled`: un campo disabilitato esce dall'ordine
               di tabulazione e chi naviga da tastiera o con screen reader non
               leggerebbe mai il proprio indirizzo. -->
          <ion-item class="glass-input email-item">
            <ion-input
                v-model="form.email"
                label="Email"
                label-placement="floating"
                type="email"
                :readonly="true"
                aria-describedby="email-note"
            />
          </ion-item>
          <p id="email-note" class="field-note">Email cannot be changed.</p>

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
            <ion-label position="floating">Birthday</ion-label>
            <div class="custom-input-value">{{ formattedBirthday }}</div>
          </ion-item>

          <ion-item class="glass-input">
            <ion-select
                label="Gender"
                label-placement="floating"
                interface="popover"
                v-model="form.gender"
                :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
            >
              <ion-select-option value="male">Male</ion-select-option>
              <ion-select-option value="female">Female</ion-select-option>
              <ion-select-option value="other">Other</ion-select-option>
              <ion-select-option value="not_specified">Prefer not to say</ion-select-option>
            </ion-select>
          </ion-item>
        </ion-list>

        <!-- Premibile anche a form incompleto: è la pressione a spiegare cosa
             manca. Si disabilita solo mentre il salvataggio è in volo. -->
        <ion-button
            expand="block"
            class="ion-margin-top save-btn"
            :disabled="isSaving"
            @click="saveProfile"
        >
          <ion-spinner v-if="isSaving" slot="start" name="crescent" />
          {{ isSaving ? 'Saving…' : 'Save Changes' }}
        </ion-button>

        <!-- La password ha una pagina dedicata: tenerla qui significava un
             campo che non salvava nulla accanto a campi che salvano. -->
        <ion-button
            expand="block"
            fill="clear"
            class="password-link"
            @click="goToChangePassword"
        >
          <ion-icon slot="start" :icon="keyOutline" aria-hidden="true" />
          Change password
        </ion-button>
      </template>
    </ion-content>

    <!--
      La modale sta FUORI da `ion-content` (vedi RegistrationPage.vue): dentro
      lo scroller lasciava il proprio `ion-backdrop` nel DOM del form e
      dipendeva dal contesto di posizionamento del contenuto.

      Stessa correzione di RegistrationPage.vue anche sul modo di aprirla:
      modale dichiarativa (`:is-open`) invece di `$el.present()/dismiss()`. Con
      la variante imperativa il primo tap veniva perso (custom element non
      ancora idratato) e la `dismiss()` chiamata dentro `ionChange` cadeva
      durante l'animazione di apertura, lasciando l'overlay smontato a metà
      (`show-modal` senza `overlay-hidden`, focus bloccato sulla modale):
      da lì in poi nessun campo della pagina era più editabile.
    -->
    <ion-modal
        class="birthday-modal"
        :is-open="isBirthdayOpen"
        :keep-contents-mounted="true"
        @didDismiss="isBirthdayOpen = false"
    >
      <!--
        `prefer-wheel` + `show-default-buttons`: si scorre l'anno fino al 1900
        senza uscire dalla dialog e il valore viene confermato SOLO con "Done".
        Prima ogni cambio di mese/anno emetteva `ionChange` e chiudeva tutto.
      -->
      <ion-datetime
          id="birthday-datetime"
          presentation="date"
          prefer-wheel
          :value="pickerValue"
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
  </ion-page>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton, IonIcon,
  IonItem, IonLabel, IonInput, IonList, IonSelect, IonSelectOption, IonDatetime, IonModal,
  IonSpinner
} from '@ionic/vue';
import { useRouter } from 'vue-router';
import {
  personCircleOutline, arrowBackOutline, alertCircleOutline, refreshOutline, keyOutline
} from 'ionicons/icons';
import dayjs from 'dayjs';
import { apiErrorMessage } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';
import {
  collectErrors, errorSummary, isBlank, requiredMessage, useFormValidation
} from '@/composables/useValidation';
import {
  fetchProfile, formatBirthday, updateProfile, EMPTY_PROFILE_FORM, type ProfileForm
} from '@/composables/useProfile';
import RequiredMark from '@/components/RequiredMark.vue';
import FieldError from '@/components/FieldError.vue';
import FormLegend from '@/components/FormLegend.vue';

/** Intervallo ragionevole per una data di nascita: nessuna data futura. */
const MIN_BIRTHDAY = '1900-01-01';
const MAX_BIRTHDAY = dayjs().format('YYYY-MM-DD');
/** Anno di partenza della ruota quando il campo è ancora vuoto. */
const DEFAULT_BIRTHDAY = '2000-01-01';

const router = useRouter();
const { showToast } = useToast();

/* ---------- stato ---------- */
const form = reactive<ProfileForm>({ ...EMPTY_PROFILE_FORM });
const isLoading = ref(true);
const isSaving = ref(false);
const loadError = ref<string | null>(null);

const isBirthdayOpen = ref(false);
/** Valore su cui è posizionata la ruota: non deve riempire il campo. */
const pickerValue = ref<string>(DEFAULT_BIRTHDAY);

/* ---------- caricamento ---------- */

/**
 * Carica il profilo dell'utente autenticato.
 *
 * Un errore qui non è un dettaglio: senza dati la form mostrerebbe campi vuoti
 * e il salvataggio cancellerebbe quello che c'era. Per questo il form compare
 * solo a caricamento riuscito, e in caso di errore si offre il riprova.
 */
async function loadProfile(): Promise<void> {
  isLoading.value = true;
  loadError.value = null;

  try {
    const profile = await fetchProfile();
    Object.assign(form, profile);
    // La ruota parte dalla data già registrata, non dal 2000.
    pickerValue.value = profile.birthday || DEFAULT_BIRTHDAY;
  } catch (err: unknown) {
    loadError.value = apiErrorMessage(err, 'Could not load your profile. Please try again.');
  } finally {
    isLoading.value = false;
  }
}

onMounted(loadProfile);

/* ---------- validazione ----------
   I limiti rispecchiano `@Size` di `UpdateProfileRequest` e le colonne della
   tabella `user`: superarli farebbe rifiutare l'intero salvataggio dal server.
   I `maxlength` dei campi impediscono di digitare oltre; le regole qui sotto
   coprono incolla e autofill, dove `maxlength` non tronca sempre. */
const MAX_NAME = 100;
const MAX_SURNAME = 100;
const MAX_ADDRESS = 255;
const MAX_PHONE = 30;

/** Ordine visivo dei campi validati (guida il focus sul primo invalido). */
const FIELD_ORDER = ['name', 'surname', 'address', 'phone'] as const;
type ProfileField = (typeof FIELD_ORDER)[number];

const tooLong = (value: string, max: number): boolean => value.trim().length > max;

const { errors, errorFor, fieldClass, validateOnSubmit } =
    useFormValidation<ProfileField>(() => collectErrors<ProfileField>([
      { field: 'name', invalid: isBlank(form.name), message: requiredMessage('Name') },
      {
        field: 'name',
        invalid: tooLong(form.name, MAX_NAME),
        message: `Name must be ${MAX_NAME} characters or fewer`
      },
      { field: 'surname', invalid: isBlank(form.surname), message: requiredMessage('Surname') },
      {
        field: 'surname',
        invalid: tooLong(form.surname, MAX_SURNAME),
        message: `Surname must be ${MAX_SURNAME} characters or fewer`
      },
      { field: 'address', invalid: isBlank(form.address), message: requiredMessage('Address') },
      {
        field: 'address',
        invalid: tooLong(form.address, MAX_ADDRESS),
        message: `Address must be ${MAX_ADDRESS} characters or fewer`
      },
      // Il telefono resta facoltativo: si controlla solo la lunghezza.
      {
        field: 'phone',
        invalid: tooLong(form.phone, MAX_PHONE),
        message: `Phone must be ${MAX_PHONE} characters or fewer`
      }
    ]), FIELD_ORDER);

/* ---------- compleanno ---------- */
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
    // `ion-datetime` restituisce una data-ora ISO: al profilo serve il giorno.
    const day = iso.slice(0, 10);
    pickerValue.value = day;
    form.birthday = day;
  }
  isBirthdayOpen.value = false;
};

const formattedBirthday = computed(() => formatBirthday(form.birthday));

const birthdayAriaLabel = computed(() =>
    formattedBirthday.value ? `Birthday, ${formattedBirthday.value}` : 'Birthday, not set'
);

/* ---------- salvataggio ---------- */
async function saveProfile(): Promise<void> {
  if (!(await validateOnSubmit())) {
    await showToast(errorSummary(errors.value), 'danger');
    return;
  }

  isSaving.value = true;
  try {
    const saved = await updateProfile(form);
    // Si riparte da ciò che il backend ha davvero salvato: se normalizza un
    // valore, l'utente lo vede subito invece di scoprirlo al prossimo accesso.
    Object.assign(form, saved);
    pickerValue.value = saved.birthday || DEFAULT_BIRTHDAY;

    await showToast('Profile updated', 'success');
  } catch (err: unknown) {
    await showToast(
        apiErrorMessage(err, 'Could not save your profile. Please try again.'),
        'danger'
    );
  } finally {
    isSaving.value = false;
  }
}

/* ---------- navigazione ---------- */
const goBack = () => {
  router.push({ name: 'Settings' });
};

const goToChangePassword = () => {
  router.push({ name: 'ChangePassword' });
};
</script>

<style scoped>
.profile-icon {
  font-size: var(--font-xl);
  color: var(--mauve);
}
ion-item.glass-input {
  --inner-padding-top: var(--space-2);
  --inner-padding-bottom: var(--space-2);
}
/* Il campo non editabile si distingue dagli altri senza sbiadire: resta
   leggibile quanto il resto della form. */
.email-item {
  --background: var(--glass-bg-subtle);
}
.email-item ion-input {
  --color: var(--subtext1);
}
.field-note {
  margin: calc(-1 * var(--space-3)) 0 var(--space-4);
  padding-inline-start: var(--space-4);
  font-size: var(--font-sm);
  color: var(--subtext0);
}
.custom-input-value {
  width: 100%;
  text-align: start;
  font-size: inherit;
  color: var(--ion-text-color);
  padding-top: var(--space-2);
  padding-bottom: var(--space-2);
  min-height: calc(1em + 16px);
}
.save-btn {
  --background: var(--gradient-pink-mauve);
  --color: var(--crust);
  font-weight: var(--font-weight-semibold);
  --border-radius: var(--radius-pill);
}
.password-link {
  --color: var(--peach);
  margin-top: var(--space-2);
  text-transform: none;
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
