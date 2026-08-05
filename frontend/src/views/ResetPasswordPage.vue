<template>
  <ion-page>
    <ion-content :fullscreen="true" class="ion-padding">
      <div class="registration-container">
        <div class="registration-header">
          <ion-icon :icon="keyOutline" class="header-icon" />
          <h1 class="title-peach">Set New Password</h1>
        </div>

        <div class="form-wrapper">
          <p class="reset-subtitle">
            Enter your new password below. Make sure it's a strong one!
          </p>

          <FormLegend />

          <ion-list lines="none">
            <!-- New Password Field -->
            <ion-item
                :class="fieldClass('newPassword')"
                class="glass-input password-item"
                data-field="newPassword"
            >
              <ion-icon slot="start" :icon="lockClosedOutline" class="input-icon" />
              <ion-input
                  v-model="form.newPassword"
                  label-placement="floating"
                  :type="showPassword ? 'text' : 'password'"
                  autocomplete="new-password"
                  :helper-text="PASSWORD_HINT"
                  :aria-invalid="!!errorFor('newPassword')"
              >
                <div slot="label">New Password <RequiredMark /></div>
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
            <FieldError :message="errorFor('newPassword')" />

            <!-- Confirm Password Field -->
            <ion-item
                :class="fieldClass('confirmPassword')"
                class="glass-input"
                data-field="confirmPassword"
            >
              <ion-icon slot="start" :icon="lockClosedOutline" class="input-icon" />
              <ion-input
                  v-model="form.confirmPassword"
                  label-placement="floating"
                  type="password"
                  autocomplete="new-password"
                  :aria-invalid="!!errorFor('confirmPassword')"
              >
                <div slot="label">Confirm Password <RequiredMark /></div>
              </ion-input>
            </ion-item>
            <FieldError :message="errorFor('confirmPassword')" />
          </ion-list>

          <ion-grid class="ion-margin-top">
            <ion-row class="ion-justify-content-around">
              <ion-col size="5">
                <ion-button expand="block" class="pill-button gradient-outline" @click="router.push('/login')">
                  Cancel
                </ion-button>
              </ion-col>
              <ion-col size="5">
                <ion-button
                    expand="block"
                    :disabled="isLoading"
                    class="pill-button gradient-outline"
                    @click="handleResetPassword"
                >
                  <ion-spinner v-if="isLoading" name="crescent"></ion-spinner>
                  <span v-else>Save Password</span>
                </ion-button>
              </ion-col>
            </ion-row>
          </ion-grid>
        </div>
      </div>

      <ion-loading :is-open="isLoading" message="Updating password..." />
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  IonPage, IonContent, IonList, IonItem, IonInput, IonIcon,
  IonButton, IonGrid, IonRow, IonCol, IonSpinner,
  IonLoading
} from '@ionic/vue';
import { keyOutline, lockClosedOutline, eyeOutline, eyeOffOutline } from 'ionicons/icons';
import { api } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';
import {
  collectErrors, errorSummary, isBlank, isStrongPassword, requiredMessage,
  useFormValidation, PASSWORD_ERROR, PASSWORD_HINT
} from '@/composables/useValidation';
import RequiredMark from '@/components/RequiredMark.vue';
import FieldError from '@/components/FieldError.vue';
import FormLegend from '@/components/FormLegend.vue';

/* ---------- state ---------- */
const route = useRoute();
const router = useRouter();
const token = ref(route.query.token as string || '');
const showPassword = ref(false);
const isLoading = ref(false);

const form = reactive({
  newPassword: '',
  confirmPassword: ''
});

const { showToast } = useToast();

/* ---------- validazione ---------- */
const REQUIRED_ORDER = ['newPassword', 'confirmPassword'] as const;
type RequiredField = (typeof REQUIRED_ORDER)[number];

const { errors, errorFor, fieldClass, validateOnSubmit } =
    useFormValidation<RequiredField>(() => collectErrors<RequiredField>([
      {
        field: 'newPassword',
        invalid: isBlank(form.newPassword),
        message: requiredMessage('New password')
      },
      {
        field: 'newPassword',
        invalid: !isStrongPassword(form.newPassword),
        message: PASSWORD_ERROR
      },
      {
        field: 'confirmPassword',
        invalid: isBlank(form.confirmPassword),
        message: 'Repeat the new password to confirm it'
      },
      {
        field: 'confirmPassword',
        invalid: !isBlank(form.confirmPassword) && form.newPassword !== form.confirmPassword,
        message: 'The two passwords do not match'
      }
    ]), REQUIRED_ORDER);

/* ---------- password reset ---------- */
async function handleResetPassword() {
  try {
    if (!(await validateOnSubmit())) {
      await showToast(errorSummary(errors.value), 'danger');
      return;
    }

    isLoading.value = true;
    const { data } = await api.post('/api/auth/reset-password', {
      token: token.value,
      newPassword: form.newPassword
    });

    if (!data?.success) throw new Error(data?.message ?? 'Unknown error');

    await showToast(data.message || 'Password updated successfully!', 'success');
    await router.push({ name: 'Login' });
  } catch (err: any) {
    console.error('Password reset error:', err);
    const message = err.response?.data?.message || err.message || 'Failed to update password';
    await showToast(message, 'danger');
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

.reset-subtitle {
  font-size: var(--font-base);
  text-align: center;
  margin: 0 0 var(--space-5);
  color: var(--subtext0);
}

ion-item.glass-input {
  --inner-padding-top: 4px;
  --inner-padding-bottom: 4px;
  --min-height: var(--touch-target);
  font-size: var(--font-base);
}

ion-input {
  font-size: var(--font-base);
  --padding-start: 0;
  --padding-end: 0;
}

.password-item ion-icon.toggle-eye {
  cursor: pointer;
  font-size: var(--font-lg);
}

ion-icon.input-icon {
  color: var(--peach);
  font-size: var(--font-lg);
  margin-right: var(--space-2);
}

ion-icon.toggle-eye {
  color: var(--peach);
}

.pill-button {
  --border-radius: var(--radius-xl);
}
</style>