<template>
  <ion-page>
    <ion-content class="ion-padding ion-text-center">
      <div class="login-container">
        <div class="logo-wrapper">
          <img src="/logo.png" alt="App Logo" class="login-logo" />
        </div>

        <div class="form-wrapper">
          <FormLegend />

          <ion-list lines="none">
            <ion-item :class="fieldClass('email')" class="glass-input" data-field="email">
              <ion-icon :icon="personOutline" class="input-icon" />
              <ion-input
                  label-placement="floating"
                  placeholder="Insert your email"
                  type="email"
                  autocomplete="email"
                  v-model="email"
                  :aria-invalid="!!errorFor('email')"
              >
                <div slot="label">Email <RequiredMark /></div>
              </ion-input>
            </ion-item>
            <FieldError :message="errorFor('email')" />

            <ion-item :class="fieldClass('password')" class="glass-input" data-field="password">
              <ion-icon :icon="keyOutline" class="input-icon" />
              <ion-input
                  label-placement="floating"
                  type="password"
                  placeholder="Insert your password"
                  autocomplete="current-password"
                  v-model="password"
                  :aria-invalid="!!errorFor('password')"
              >
                <div slot="label">Password <RequiredMark /></div>
              </ion-input>
            </ion-item>
            <FieldError :message="errorFor('password')" />
          </ion-list>

          <!--
            Il toast dura pochi secondi ed è facile da mancare: gli esiti che
            spiegano perché l'accesso è negato (account in attesa di
            approvazione, account bloccato) restano scritti qui finché non si
            ritenta. Sono messaggi lunghi, non un generico "login failed".
          -->
          <div v-if="notice" class="login-notice" role="alert">
            <ion-icon :icon="alertCircleOutline" aria-hidden="true" />
            <p>{{ notice }}</p>
          </div>

          <div class="forgot-password-container">
            <ion-button @click="handleForgotPassword" fill="clear" class="forgot-password-btn">
              Forgot Password?
            </ion-button>
          </div>

          <ion-grid class="ion-margin-top">
            <ion-row class="ion-justify-content-around">
              <ion-col size="5">
                <ion-button @click="handleLogin" expand="block" class="pill-button gradient-outline" :disabled="isLoading">
                  <ion-spinner v-if="isLoading" name="crescent"></ion-spinner>
                  <span v-else>Login</span>
                </ion-button>
              </ion-col>
              <ion-col size="5">
                <ion-button @click="goToRegistration" expand="block" class="pill-button gradient-outline">
                  Sign Up
                </ion-button>
              </ion-col>
            </ion-row>
          </ion-grid>
        </div>
      </div>

    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { safeRedirectTarget } from '@/router';
import { useAuthStore } from '@/store/auth';
import { useToast } from '@/composables/useToast';
import {
  IonPage, IonContent, IonList, IonItem, IonInput, IonIcon,
  IonButton, IonGrid, IonRow, IonCol, IonSpinner // MODIFIED: Imported IonSpinner
} from '@ionic/vue';
import { alertCircleOutline, personOutline, keyOutline } from 'ionicons/icons';
import { apiErrorMessage } from '@/composables/useApi';
import {
  collectErrors, errorSummary, isBlank, requiredMessage, useFormValidation
} from '@/composables/useValidation';
import RequiredMark from '@/components/RequiredMark.vue';
import FieldError from '@/components/FieldError.vue';
import FormLegend from '@/components/FormLegend.vue';

/** Oltre questa lunghezza il messaggio non si legge in un toast breve. */
const LONG_MESSAGE_LENGTH = 45;
const LONG_TOAST_DURATION = 6000;

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();

const isLoading = ref(false);
/** Ultimo esito negativo del login, mostrato in pagina finché non si ritenta. */
const notice = ref<string | null>(null);

// Form data
const email = ref('');
const password = ref('');

// Feedback all'utente (vedi `useToast`: la variante dichiarativa non veniva
// mai presentata).
const { showToast: presentToast } = useToast();

/* ---------- validazione ----------
   Qui si controlla solo la presenza: il formato dell'indirizzo lo giudica il
   backend (l'utenza amministrativa può non essere un'email) e un controllo
   client troppo severo chiuderebbe fuori chi ha credenziali valide. */
const REQUIRED_ORDER = ['email', 'password'] as const;
type RequiredField = (typeof REQUIRED_ORDER)[number];

const { errors, errorFor, fieldClass, validateOnSubmit } =
    useFormValidation<RequiredField>(() => collectErrors<RequiredField>([
      { field: 'email', invalid: isBlank(email.value), message: requiredMessage('Email') },
      { field: 'password', invalid: isBlank(password.value), message: requiredMessage('Password') }
    ]), REQUIRED_ORDER);

const handleLogin = async () => {
  if (!(await validateOnSubmit())) {
    await presentToast(errorSummary(errors.value), 'danger');
    return;
  }

  isLoading.value = true;
  notice.value = null;

  try {
    await auth.login({ email: email.value, password: password.value });
    presentToast('Login successful!', 'success');

    // Se il guard ci ha mandati qui da una pagina privata, si riprende da
    // quella: chi apre un link diretto e deve autenticarsi non viene scaricato
    // sulla home. Il valore è filtrato (solo path interni) per non farne un
    // open redirect. L'amministratore atterra in back-office; se deve ancora
    // cambiare la password di provisioning ci pensa il guard del router.
    const target = safeRedirectTarget(route.query.redirect);
    router.push(target ?? { name: auth.isAdmin ? 'AdminDashboard' : 'Home' });
  } catch (error) {
    // Il backend risponde 200 anche quando l'accesso è negato: lo store rilancia
    // il suo `message`, che qui è l'unica spiegazione disponibile (account in
    // attesa di approvazione, bloccato, credenziali errate). Va mostrato
    // integralmente, non sostituito da un testo generico.
    const errorMessage = apiErrorMessage(error, 'An unknown error occurred');
    notice.value = errorMessage;
    presentToast(
        errorMessage,
        'danger',
        errorMessage.length > LONG_MESSAGE_LENGTH ? LONG_TOAST_DURATION : undefined
    );
  } finally {
    isLoading.value = false;
  }
};
const goToRegistration = () => {
  router.push({ name: 'Register' });
};

const handleForgotPassword = () => {
  router.push({ name: 'ForgotPassword' });
};
</script>

<style scoped>

.pill-button ion-spinner {
  width: 1.5em;
  height: 1.5em;
}

.login-container {
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  height: 100%;
}

.logo-wrapper {
  margin-bottom: 2.5rem;
}
.login-logo {
  width: 65%;
  max-width: 240px;
  height: auto;
}
.form-wrapper {
  max-width: 450px;
  width: 100%;
}
.login-notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  margin-top: var(--space-4);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface0);
  border: 1px solid var(--peach);
  text-align: start;
}
.login-notice ion-icon {
  color: var(--peach);
  font-size: 1.3rem;
  flex-shrink: 0;
}
.login-notice p {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--subtext1);
  line-height: 1.5;
}
.forgot-password-container {
  display: flex;
  justify-content: flex-end;
  padding-right: 4px;
  margin-top: 4px;
  margin-bottom: 1.5rem;
}
.forgot-password-btn {
  --color: var(--subtext0);
  font-size: 0.8rem;
  text-transform: none;
  font-weight: 500;
  --padding-start: 0;
  --padding-end: 0;
  height: auto;
}
</style>