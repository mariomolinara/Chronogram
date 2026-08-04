<template>
  <ion-page>
    <ion-content :fullscreen="true" class="ion-padding">
      <div class="registration-container">
        <div class="registration-header">
          <ion-icon :icon="keyOutline" class="header-icon" />
          <h1 class="title-peach">Recover Access</h1>
        </div>

        <div class="form-wrapper">
          <p class="reset-subtitle">
            Enter your email to receive reset instructions.
          </p>

          <ion-list lines="none">
            <ion-item :class="errorClass('email')" class="glass-input">
              <ion-icon slot="start" :icon="mailOutline" class="input-icon" />
              <ion-input
                  v-model="email"
                  label="Email"
                  label-placement="floating"
                  type="email"
                  :aria-label="'Email'"
                  autocomplete="email"
              />
            </ion-item>
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
                    :disabled="isLoading || !isValidEmail(email)"
                    class="pill-button gradient-outline"
                    @click="handleSendResetLink"
                >
                  Send Link
                </ion-button>
              </ion-col>
            </ion-row>
          </ion-grid>
        </div>
      </div>

      <ion-loading :is-open="isLoading" message="Sending reset link..." />
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonContent, IonList, IonItem, IonInput, IonIcon,
  IonButton, IonGrid, IonRow, IonCol, IonLoading
} from '@ionic/vue';
import { mailOutline, keyOutline } from 'ionicons/icons';
import { api } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';

/* ---------- state ---------- */
const router = useRouter();
const isLoading = ref(false);
const email = ref('');

const { showToast } = useToast();

/* ---------- computed ---------- */
const isValidEmail = (e: string) =>
    /^[\w-.]+@([\w-]+\.)+[\w-]{2,}$/.test(e);

const errorClass = (f: string) => ({
  'ion-invalid': f === 'email' && email.value && !isValidEmail(email.value)
});


/* ---------- password reset ---------- */
async function handleSendResetLink() {
  if (!isValidEmail(email.value)) {
    showToast('Please enter a valid email address', 'danger');
    return;
  }

  isLoading.value = true;
  try {
    const { data } = await api.post('/api/auth/request-reset', { email: email.value });

    if (!data?.success) throw new Error(data?.message ?? 'Unknown error');

    showToast(data.message || 'Reset link sent! Check your inbox.', 'success');
    router.push({ name: 'Login' });
  } catch (err: any) {
    console.error('Password reset error:', err);
    const message = err.response?.data?.message || err.message || 'Failed to send reset link';
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

ion-item.ion-invalid {
  --highlight-color-focused: var(--ion-color-danger);
  --background: rgba(var(--ion-color-danger-rgb), 0.1);
}

ion-icon.input-icon {
  color: var(--peach);
  font-size: var(--font-lg);
  margin-right: var(--space-2);
}

.pill-button {
  --border-radius: var(--radius-xl);
}
</style>