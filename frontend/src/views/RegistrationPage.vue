<template>
  <ion-page>
    <ion-content :fullscreen="true" class="ion-padding">
      <div class="registration-container">
        <div class="registration-header">
          <ion-icon :icon="personAddOutline" class="header-icon" />
          <h1 class="title-peach">Registration</h1>
        </div>

        <div class="form-wrapper">
          <ion-list lines="none">
            <ion-item :class="errorClass('name')" class="glass-input">
              <ion-icon slot="start" :icon="personOutline" class="input-icon" />
              <ion-input
                  v-model="form.name"
                  label="Name"
                  label-placement="floating"
                  type="text"
                  :aria-label="'Name'"
                  autocomplete="given-name"
              />
            </ion-item>

            <ion-item :class="errorClass('surname')" class="glass-input">
              <ion-icon slot="start" :icon="personOutline" class="input-icon" />
              <ion-input
                  v-model="form.surname"
                  label="Surname"
                  label-placement="floating"
                  type="text"
                  :aria-label="'Surname'"
                  autocomplete="family-name"
              />
            </ion-item>

            <ion-item :class="errorClass('address')" class="glass-input">
              <ion-icon slot="start" :icon="locationOutline" class="input-icon" />
              <ion-input
                  v-model="form.address"
                  label="Address"
                  label-placement="floating"
                  type="text"
                  :aria-label="'Address'"
                  autocomplete="street-address"
              />
            </ion-item>

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

            <ion-item :class="errorClass('email')" class="glass-input">
              <ion-icon slot="start" :icon="mailOutline" class="input-icon" />
              <ion-input
                  v-model="form.email"
                  label="Email"
                  label-placement="floating"
                  type="email"
                  :aria-label="'Email'"
                  autocomplete="email"
              />
            </ion-item>

            <ion-item :class="errorClass('password')" class="glass-input password-item">
              <ion-icon slot="start" :icon="keyOutline" class="input-icon" />
              <ion-input
                  v-model="form.password"
                  :type="showPassword ? 'text' : 'password'"
                  label="Password"
                  label-placement="floating"
                  :aria-label="'Password'"
                  autocomplete="new-password"
                  :helper-text="PASSWORD_HINT"
              />
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
                <ion-button
                    expand="block"
                    :disabled="isLoading || hasErrors"
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

      <!--
        Modale dichiarativa (`:is-open`) e NON imperativa (`$el.present()`):
        con la variante imperativa il primo tap veniva perso (il custom element
        non era ancora idratato) e la `dismiss()` invocata dentro `ionChange`
        cadeva durante l'animazione di apertura, lasciando l'overlay smontato a
        metà (`show-modal` senza `overlay-hidden`, focus bloccato sulla modale,
        `body { overflow: hidden }`): da lì in poi NESSUN campo del form era più
        digitabile e il pulsante Register non si abilitava mai.
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
    </ion-content>
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
  callOutline, mailOutline, personOutline,
  keyOutline, calendarOutline, transgenderOutline,
  locationOutline
} from 'ionicons/icons';
import dayjs from 'dayjs';
import { api } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';

/* ---------- costanti ---------- */
/** Regola applicata da `isStrongPassword`: senza indicarla il pulsante Register
 *  resta disabilitato e l'utente non ha modo di capire quale campo manca. */
const PASSWORD_HINT = 'At least 8 characters, with upper and lower case, a digit and a symbol.';

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
const hasErrors = computed(() =>
    !form.name.trim()       ||
    !form.surname.trim()    ||
    !form.address.trim()    ||
    !isValidEmail(form.email) ||
    !isStrongPassword(form.password)
);

/* ---------- helpers ---------- */
const isValidEmail = (e: string) =>
    /^[\w-.]+@([\w-]+\.)+[\w-]{2,}$/.test(e);

const isStrongPassword = (p: string) =>
    /^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[^A-Za-z\d]).{8,}$/.test(p);

const errorClass = (f: keyof typeof form) => ({
  'ion-invalid':
      (f === 'email'    && form.email    && !isValidEmail(form.email)) ||
      (f === 'password' && form.password && !isStrongPassword(form.password)) ||
      (!(form[f] as string).trim() && ['name', 'surname', 'address'].includes(f))
});

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
async function handleRegister() {
  if (hasErrors.value) {
    showToast('Please correct the highlighted fields.', 'danger');
    return;
  }

  isLoading.value = true;
  try {
    const { data } = await api.post('/api/auth/register', { ...form });
    if (!data?.success) throw new Error(data?.message ?? 'Unknown error');

    showToast(data.message || 'Registered successfully!', 'success');
    await router.push({ name: 'Login' });
  } catch (err: any) {
    console.error('Registration error:', err);
    const message = err.response?.data?.message || err.message || 'Unexpected error';
    showToast(message, 'danger');
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