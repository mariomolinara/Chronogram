<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <!-- Back button to go Home -->
        <ion-buttons slot="start">
          <ion-button @click="goToHome">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" class="back-icon" />
          </ion-button>
        </ion-buttons>
        <ion-title class="ion-text-center title-peach">Change Password</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <div class="form-wrapper">
        <!-- New Password with eye toggle -->
        <ion-item lines="inset">
          <ion-label position="stacked">New Password</ion-label>
          <ion-input
              :type="showNewPassword ? 'text' : 'password'"
              v-model="newPassword"
              placeholder="Enter new password"
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

        <!-- Confirm New Password with eye toggle -->
        <ion-item lines="inset">
          <ion-label position="stacked">Confirm New Password</ion-label>
          <ion-input
              :type="showConfirmPassword ? 'text' : 'password'"
              v-model="confirmPassword"
              placeholder="Confirm new password"
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

        <!-- Submit button -->
        <ion-button expand="block" class="submit-btn">
          Update Password
        </ion-button>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { eyeOutline, eyeOffOutline, arrowBackOutline } from 'ionicons/icons'

// Navigation
const router = useRouter()
const goToHome = () => router.push({ name: 'Home' })

// Form fields
const newPassword = ref('')
const confirmPassword = ref('')

// Eye icon toggles
const showNewPassword = ref(false)
const showConfirmPassword = ref(false)

const toggleNewPassword = () => {
  showNewPassword.value = !showNewPassword.value
}
const toggleConfirmPassword = () => {
  showConfirmPassword.value = !showConfirmPassword.value
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
