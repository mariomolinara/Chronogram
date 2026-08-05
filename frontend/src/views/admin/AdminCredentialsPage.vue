<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <!-- Durante il cambio obbligatorio non c'è nessun altro posto dove andare. -->
        <ion-buttons v-if="!forced" slot="start">
          <ion-button aria-label="Back to dashboard" @click="goToDashboard">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" class="back-icon" />
          </ion-button>
        </ion-buttons>
        <ion-title class="ion-text-center title-peach">Administrator account</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <div class="form-wrapper">

        <div v-if="forced" class="notice" role="alert">
          <ion-icon :icon="warningOutline" aria-hidden="true" />
          <p>
            This account still uses the password it was created with.
            Choose a new one to continue.
          </p>
        </div>

        <p v-else class="page-subtitle">
          Update the email or the password of the built-in administrator.
          Leave a field empty to keep its current value.
        </p>

        <FormLegend />

        <form @submit.prevent="submit">
          <ion-item lines="inset" :class="fieldClass('currentPassword')" data-field="currentPassword">
            <ion-label position="stacked">Current password <RequiredMark /></ion-label>
            <ion-input
                v-model="currentPassword"
                type="password"
                autocomplete="current-password"
                required
                placeholder="Enter the current password"
                :aria-invalid="!!errorFor('currentPassword')"
            />
          </ion-item>
          <FieldError :message="errorFor('currentPassword')" />

          <ion-item lines="inset" :class="fieldClass('newEmail')" data-field="newEmail">
            <ion-label position="stacked">New email (optional)</ion-label>
            <ion-input
                v-model="newEmail"
                type="email"
                autocomplete="username"
                :placeholder="auth.username ?? 'admin@example.com'"
                :aria-invalid="!!errorFor('newEmail')"
            />
          </ion-item>
          <FieldError :message="errorFor('newEmail')" />

          <ion-item lines="inset" :class="fieldClass('newPassword')" data-field="newPassword">
            <ion-label position="stacked">
              New password
              <!-- Obbligatoria solo quando il cambio è imposto: negli altri
                   casi resta un campo facoltativo e va detto. -->
              <RequiredMark v-if="forced" />
              <template v-else>(optional)</template>
            </ion-label>
            <ion-input
                v-model="newPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="At least 8 characters"
                :aria-invalid="!!errorFor('newPassword')"
            />
            <ion-icon
                :icon="showPassword ? eyeOffOutline : eyeOutline"
                slot="end"
                class="eye-icon"
                role="button"
                tabindex="0"
                :aria-label="showPassword ? 'Hide password' : 'Show password'"
                @click="showPassword = !showPassword"
                @keydown.enter="showPassword = !showPassword"
                @keydown.space.prevent="showPassword = !showPassword"
            />
          </ion-item>
          <FieldError :message="errorFor('newPassword')" />

          <ion-item lines="inset" :class="fieldClass('confirmPassword')" data-field="confirmPassword">
            <ion-label position="stacked">
              Confirm new password
              <RequiredMark v-if="forced" />
            </ion-label>
            <ion-input
                v-model="confirmPassword"
                :type="showPassword ? 'text' : 'password'"
                autocomplete="new-password"
                placeholder="Repeat the new password"
                :aria-invalid="!!errorFor('confirmPassword')"
            />
          </ion-item>
          <FieldError :message="errorFor('confirmPassword')" />

          <!-- Errore restituito dal server (password corrente sbagliata, email
               già in uso): riguarda l'intero invio, non un singolo campo. -->
          <p v-if="submitError" class="field-error" role="alert">{{ submitError }}</p>

          <ion-button type="submit" expand="block" class="submit-btn" :disabled="submitting">
            {{ submitting ? 'Saving…' : 'Save changes' }}
          </ion-button>
        </form>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton,
  IonIcon, IonItem, IonLabel, IonInput, toastController
} from '@ionic/vue';
import { arrowBackOutline, eyeOffOutline, eyeOutline, warningOutline } from 'ionicons/icons';
import { api } from '@/composables/useApi';
import {
  collectErrors, isBlank, isValidEmail, requiredMessage, useFormValidation, EMAIL_ERROR
} from '@/composables/useValidation';
import RequiredMark from '@/components/RequiredMark.vue';
import FieldError from '@/components/FieldError.vue';
import FormLegend from '@/components/FormLegend.vue';
import { useAuthStore } from '@/store/auth';

const MIN_PASSWORD_LENGTH = 8;

const router = useRouter();
const auth = useAuthStore();

const currentPassword = ref('');
const newEmail = ref('');
const newPassword = ref('');
const confirmPassword = ref('');
const showPassword = ref(false);
const submitting = ref(false);
/** Errore restituito dal server: vale per l'invio, non per un campo singolo. */
const submitError = ref<string | null>(null);

/** Il cambio è imposto finché l'account usa la password di provisioning. */
const forced = computed(() => auth.mustChangePassword);

const goToDashboard = () => router.push({ name: 'AdminDashboard' });

/* ---------- validazione ----------
   Prima il form mostrava un solo messaggio alla volta, in fondo alla pagina e
   senza indicare il campo. Ora ogni errore sta sotto il campo che lo genera;
   resta la regola "almeno un campo fra email e password", che non appartiene a
   un campo solo ed è agganciata al primo dei due. */
const REQUIRED_ORDER = ['currentPassword', 'newEmail', 'newPassword', 'confirmPassword'] as const;
type CredentialField = (typeof REQUIRED_ORDER)[number];

const { errorFor, fieldClass, validateOnSubmit } =
    useFormValidation<CredentialField>(() => {
      const wantsEmail = newEmail.value.trim().length > 0;
      const wantsPassword = newPassword.value.length > 0;

      return collectErrors<CredentialField>([
        {
          field: 'currentPassword',
          invalid: isBlank(currentPassword.value),
          message: requiredMessage('Current password')
        },
        {
          field: 'newEmail',
          invalid: wantsEmail && !isValidEmail(newEmail.value.trim()),
          message: EMAIL_ERROR
        },
        {
          field: 'newEmail',
          invalid: !forced.value && !wantsEmail && !wantsPassword,
          message: 'Enter a new email, a new password, or both'
        },
        {
          field: 'newPassword',
          invalid: forced.value && !wantsPassword,
          message: `Choose a new password to continue (at least ${MIN_PASSWORD_LENGTH} characters)`
        },
        {
          field: 'newPassword',
          invalid: wantsPassword && newPassword.value.length < MIN_PASSWORD_LENGTH,
          message: `The new password must be at least ${MIN_PASSWORD_LENGTH} characters long`
        },
        {
          field: 'confirmPassword',
          invalid: wantsPassword && newPassword.value !== confirmPassword.value,
          message: 'The two passwords do not match'
        }
      ]);
    }, REQUIRED_ORDER);

async function submit() {
  submitError.value = null;
  if (!(await validateOnSubmit())) {
    return;
  }

  submitting.value = true;
  try {
    const { data } = await api.post<{ success: boolean; message: string }>(
        '/api/admin/account/credentials',
        {
          currentPassword: currentPassword.value,
          newEmail: newEmail.value.trim() || null,
          newPassword: newPassword.value || null
        }
    );

    const emailChanged = newEmail.value.trim().length > 0;
    const toast = await toastController.create({
      message: data.message,
      duration: 3500,
      color: 'success'
    });
    await toast.present();

    if (emailChanged) {
      // Il subject del token è la vecchia email: la sessione non è più valida.
      await auth.logout();
      return;
    }

    await auth.clearMustChangePassword();
    await router.push({ name: 'AdminDashboard' });
  } catch (err: unknown) {
    const message = (err as { response?: { data?: { message?: string } } })
        ?.response?.data?.message;
    submitError.value = message ?? 'Could not update the credentials. Please try again.';
  } finally {
    submitting.value = false;
  }
}
</script>

<style scoped>
.form-wrapper {
  margin-top: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  padding: 0 var(--space-5);
  max-width: 450px;
  margin-inline: auto;
}

form {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface0);
  border: 1px solid var(--peach);
}

.notice ion-icon {
  color: var(--peach);
  font-size: 1.4rem;
  flex-shrink: 0;
}

.notice p {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--subtext1);
  line-height: 1.5;
}

/* Il `form` distanzia i figli con `gap: var(--space-4)`: senza correzione
   l'errore resterebbe sospeso a metà fra il campo che descrive e il
   successivo. Il margine negativo lo riavvicina al proprio campo (4px sopra,
   16px sotto). Colore e tipografia restano quelli della regola globale. */
.form-wrapper .field-error {
  margin: calc(-1 * var(--space-3)) 0 0;
}

.eye-icon {
  color: var(--peach);
  font-size: 1.3rem;
  cursor: pointer;
}

.submit-btn {
  --background: var(--gradient-pink-mauve);
  --color: var(--crust);
  font-weight: var(--font-weight-semibold);
  --border-radius: var(--radius-pill);
  margin-top: var(--space-4);
}

.back-icon {
  font-size: 1.4rem;
  color: var(--peach);
}
</style>
