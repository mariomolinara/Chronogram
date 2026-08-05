<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <!-- Si torna da dove si arriva: il cambio password si apre da Settings. -->
        <ion-buttons slot="start">
          <ion-button aria-label="Back to settings" @click="goBack">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" class="back-icon" />
          </ion-button>
        </ion-buttons>
        <ion-title class="ion-text-center title-peach">Change Password</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <div class="form-wrapper">
        <FormLegend />

        <!-- Current password: senza, il backend non può distinguere l'utente
             dal primo che trova il telefono sbloccato. -->
        <ion-item lines="inset" :class="fieldClass('currentPassword')" data-field="currentPassword">
          <ion-label position="stacked">Current Password <RequiredMark /></ion-label>
          <ion-input
              :type="showCurrentPassword ? 'text' : 'password'"
              v-model="currentPassword"
              autocomplete="current-password"
              placeholder="Enter your current password"
              :aria-invalid="!!errorFor('currentPassword')"
          ></ion-input>
          <ion-icon
              :icon="showCurrentPassword ? eyeOffOutline : eyeOutline"
              slot="end"
              class="eye-icon"
              role="button"
              tabindex="0"
              :aria-label="showCurrentPassword ? 'Hide password' : 'Show password'"
              @click="toggleCurrentPassword"
              @keydown.enter="toggleCurrentPassword"
              @keydown.space.prevent="toggleCurrentPassword"
          />
        </ion-item>
        <FieldError :message="errorFor('currentPassword')" />

        <!-- New Password with eye toggle -->
        <ion-item lines="inset" :class="fieldClass('newPassword')" data-field="newPassword">
          <ion-label position="stacked">New Password <RequiredMark /></ion-label>
          <ion-input
              :type="showNewPassword ? 'text' : 'password'"
              v-model="newPassword"
              autocomplete="new-password"
              placeholder="Enter new password"
              :helper-text="PASSWORD_HINT"
              :aria-invalid="!!errorFor('newPassword')"
          ></ion-input>
          <ion-icon
              :icon="showNewPassword ? eyeOffOutline : eyeOutline"
              slot="end"
              class="eye-icon"
              role="button"
              tabindex="0"
              :aria-label="showNewPassword ? 'Hide password' : 'Show password'"
              @click="toggleNewPassword"
              @keydown.enter="toggleNewPassword"
              @keydown.space.prevent="toggleNewPassword"
          />
        </ion-item>
        <FieldError :message="errorFor('newPassword')" />

        <!-- Confirm New Password with eye toggle -->
        <ion-item lines="inset" :class="fieldClass('confirmPassword')" data-field="confirmPassword">
          <ion-label position="stacked">Confirm New Password <RequiredMark /></ion-label>
          <ion-input
              :type="showConfirmPassword ? 'text' : 'password'"
              v-model="confirmPassword"
              autocomplete="new-password"
              placeholder="Confirm new password"
              :aria-invalid="!!errorFor('confirmPassword')"
          ></ion-input>
          <ion-icon
              :icon="showConfirmPassword ? eyeOffOutline : eyeOutline"
              slot="end"
              class="eye-icon"
              role="button"
              tabindex="0"
              :aria-label="showConfirmPassword ? 'Hide password' : 'Show password'"
              @click="toggleConfirmPassword"
              @keydown.enter="toggleConfirmPassword"
              @keydown.space.prevent="toggleConfirmPassword"
          />
        </ion-item>
        <FieldError :message="errorFor('confirmPassword')" />

        <!-- Submit button: premibile, si spegne solo mentre l'invio è in volo. -->
        <ion-button
            expand="block"
            class="submit-btn"
            :disabled="isSaving"
            @click="handleChangePassword"
        >
          <ion-spinner v-if="isSaving" slot="start" name="crescent" />
          {{ isSaving ? 'Updating…' : 'Update Password' }}
        </ion-button>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton,
  IonIcon, IonItem, IonLabel, IonInput, IonSpinner
} from '@ionic/vue'
import { eyeOutline, eyeOffOutline, arrowBackOutline } from 'ionicons/icons'
import { apiErrorMessage } from '@/composables/useApi'
import { useToast } from '@/composables/useToast'
import {
  collectErrors, errorSummary, isBlank, isStrongPassword, requiredMessage,
  useFormValidation, PASSWORD_ERROR, PASSWORD_HINT
} from '@/composables/useValidation'
import { changePassword } from '@/composables/useProfile'
import RequiredMark from '@/components/RequiredMark.vue'
import FieldError from '@/components/FieldError.vue'
import FormLegend from '@/components/FormLegend.vue'

// Navigation
const router = useRouter()
const { showToast } = useToast()

const goBack = () => router.push({ name: 'Settings' })

// Form fields
const currentPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')
const isSaving = ref(false)

/**
 * Rifiuto del server sulla password corrente.
 *
 * È un errore di UN campo, non dell'invio: mostrarlo solo nel toast lo farebbe
 * sparire dopo pochi secondi lasciando l'utente davanti a un form che sembra
 * corretto. Entra nella validazione come una regola qualsiasi.
 */
const currentPasswordServerError = ref<string | null>(null)

// Si azzera appena l'utente tocca il campo: il verdetto del server vale per il
// valore rifiutato, non per quello che sta scrivendo adesso.
watch(currentPassword, () => {
  currentPasswordServerError.value = null
})

// Eye icon toggles
const showCurrentPassword = ref(false)
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

const toggleCurrentPassword = () => {
  showCurrentPassword.value = !showCurrentPassword.value
}
const toggleNewPassword = () => {
  showNewPassword.value = !showNewPassword.value
}
const toggleConfirmPassword = () => {
  showConfirmPassword.value = !showConfirmPassword.value
}

/* ---------- validazione ---------- */
const REQUIRED_ORDER = ['currentPassword', 'newPassword', 'confirmPassword'] as const
type RequiredField = (typeof REQUIRED_ORDER)[number]

const { errors, errorFor, fieldClass, validateOnSubmit, reset: resetValidation } =
    useFormValidation<RequiredField>(() => collectErrors<RequiredField>([
      {
        field: 'currentPassword',
        invalid: isBlank(currentPassword.value),
        message: requiredMessage('Current password')
      },
      {
        field: 'currentPassword',
        invalid: currentPasswordServerError.value !== null,
        message: currentPasswordServerError.value ?? ''
      },
      {
        field: 'newPassword',
        invalid: isBlank(newPassword.value),
        message: requiredMessage('New password')
      },
      {
        field: 'newPassword',
        invalid: !isStrongPassword(newPassword.value),
        message: PASSWORD_ERROR
      },
      {
        field: 'newPassword',
        // Cambiare per finta è peggio che non cambiare: l'utente crederebbe di
        // aver ruotato una password che invece è la stessa.
        invalid: !isBlank(newPassword.value) && newPassword.value === currentPassword.value,
        message: 'The new password must be different from the current one'
      },
      {
        field: 'confirmPassword',
        invalid: isBlank(confirmPassword.value),
        message: 'Repeat the new password to confirm it'
      },
      {
        field: 'confirmPassword',
        invalid: !isBlank(confirmPassword.value) && confirmPassword.value !== newPassword.value,
        message: 'The two passwords do not match'
      }
    ]), REQUIRED_ORDER)

/** Il server distingue la password corrente sbagliata dagli altri rifiuti. */
const isWrongCurrentPassword = (message: string): boolean =>
    message.toLowerCase().includes('current password')

/* ---------- cambio password ---------- */
async function handleChangePassword(): Promise<void> {
  if (!(await validateOnSubmit())) {
    await showToast(errorSummary(errors.value), 'danger')
    return
  }

  isSaving.value = true
  try {
    await changePassword(currentPassword.value, newPassword.value)

    currentPassword.value = ''
    newPassword.value = ''
    confirmPassword.value = ''
    resetValidation()

    await showToast('Password updated', 'success')
    await router.push({ name: 'Settings' })
  } catch (err: unknown) {
    const message = apiErrorMessage(err, 'Could not update your password. Please try again.')

    if (isWrongCurrentPassword(message)) {
      currentPasswordServerError.value = message
    }
    await showToast(message, 'danger')
  } finally {
    isSaving.value = false
  }
}
</script>

<style scoped>
.form-wrapper {
  margin-top: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  padding: 0 var(--space-5);
  max-width: 450px;
  margin-inline: auto;
}

/* I campi qui stanno in una colonna con `gap`, non hanno il margine inferiore
   di `glass-input`: l'errore non deve risalire sopra il campo. Il selettore è
   più specifico della regola globale per non dipendere dall'ordine di
   iniezione dei fogli di stile. */
.form-wrapper .field-error {
  margin: calc(-1 * var(--space-4)) 0 0;
  padding-inline-start: var(--space-4);
}

.form-wrapper .form-legend {
  margin: 0;
}

/* Eye icon color and size */
.eye-icon {
  color: var(--peach);
  font-size: 1.3rem;
  cursor: pointer;
}

/* Button styling */
.submit-btn {
  --background: var(--gradient-pink-mauve);
  --color: var(--crust);
  font-weight: var(--font-weight-semibold);
  --border-radius: var(--radius-pill);
  margin-top: var(--space-6);
}

/* Back button icon color */
.back-icon {
  font-size: 1.4rem;
  color: var(--peach);
}
</style>
