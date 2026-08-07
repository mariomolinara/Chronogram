<template>
  <ion-page>
    <ion-header translucent>
      <ion-toolbar color="dark">
        <ion-title class="ion-text-center title-peach">Settings</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true" class="ion-padding content-safe-area">
      <div class="settings-container">
        <!-- Profile Header -->
        <div class="profile-header glass-input">
          <ion-icon :icon="personOutline" class="avatar-icon input-icon" aria-hidden="true" />
          <div class="name-container">
            <p class="hello">Hello</p>
            <h2 class="name">{{ displayName }}</h2>
          </div>
          <ion-button fill="clear" class="edit-button tap-target" aria-label="Edit profile" @click="goToEditProfile">
            <ion-icon :icon="createOutline" class="input-icon" aria-hidden="true" />
          </ion-button>
        </div>

        <!-- Settings Options -->
        <ion-list lines="none" class="form-wrapper">
          <ion-item button @click="goToEditProfile">
            <ion-icon slot="start" :icon="personOutline" class="input-icon" />
            <ion-label>Edit Profile</ion-label>
          </ion-item>

          <ion-item button @click="goToChangePassword">
            <ion-icon slot="start" :icon="lockClosedOutline" class="input-icon" />
            <ion-label>Change Password</ion-label>
          </ion-item>

          <ion-item button @click="goToNotifications">
            <ion-icon slot="start" :icon="notificationsOutline" class="input-icon" />
            <ion-label>Notifications</ion-label>
          </ion-item>

          <ion-item button @click="goToSupport">
            <ion-icon slot="start" :icon="helpCircleOutline" class="input-icon" />
            <ion-label>Support</ion-label>
          </ion-item>

          <ion-item button @click="goToAbout">
            <ion-icon slot="start" :icon="informationCircleOutline" class="input-icon" />
            <ion-label>About</ion-label>
          </ion-item>

          <!-- Solo per il ruolo ADMIN; l'accesso reale è comunque protetto dal backend. -->
          <ion-item v-if="auth.isAdmin" button @click="goToAdmin">
            <ion-icon slot="start" :icon="shieldCheckmarkOutline" class="input-icon" />
            <ion-label>Administration</ion-label>
          </ion-item>

          <ion-item button @click="goToDeleteAccount">
            <ion-icon slot="start" :icon="trashOutline" class="input-icon danger-text" />
            <ion-label class="danger-text">Delete Account</ion-label>
          </ion-item>
        </ion-list>
      </div>
    </ion-content>
    <!-- Bottom Navigation -->
    <!-- Bottom Navigation - Updated to match style -->
    <div class="bottom-icons-container">
      <!-- Home icon (clickable) -->
      <div
          class="bottom-icon home-icon"
          role="button"
          tabindex="0"
          aria-label="Home"
          @click="goToHome"
          @keydown.enter="goToHome"
          @keydown.space.prevent="goToHome"
      >
        <ion-icon :icon="homeOutline" aria-hidden="true" />
      </div>

      <!-- Logout button (center) -->
      <div class="bottom-icon center">
        <div
            class="logout-btn"
            role="button"
            tabindex="0"
            aria-label="Sign out"
            @click="confirmLogout"
            @keydown.enter="confirmLogout"
            @keydown.space.prevent="confirmLogout"
        >
          <ion-icon :icon="exitOutline" color="danger" aria-hidden="true" />
        </div>
      </div>

      <!-- Settings icon (current page, highlighted) -->
      <div class="bottom-icon settings-icon" aria-current="page">
        <ion-icon :icon="settingsOutline" aria-label="Settings" />
      </div>
    </div>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonIcon,
  IonList, IonItem, IonLabel, IonButton, alertController
} from '@ionic/vue';

import {
  personOutline, lockClosedOutline, notificationsOutline, helpCircleOutline, exitOutline,
  homeOutline, createOutline, trashOutline, settingsOutline, shieldCheckmarkOutline,
  informationCircleOutline
} from 'ionicons/icons';

import { useAuthStore } from '@/store/auth';

const router = useRouter();
const auth = useAuthStore();

/** Identificativo dell'utente autenticato (vedi `displayName` nello store). */
const displayName = computed(() => auth.displayName || '—');

const goToHome = () => {
  router.push({ name: 'Home' });
};

const goToAdmin = () => {
  router.push({ name: 'AdminDashboard' });
};

const goToEditProfile = () => {
  router.push({ name: 'EditProfile' });
};

const goToChangePassword = () => {
  router.push({ name: 'ChangePassword' });
};

const goToNotifications = () => {
  router.push({ name: 'Notifications' });
};

const goToSupport = () => {
  router.push({ name: 'Support' });
};

const goToAbout = () => {
  router.push({ name: 'About' });
};

const goToDeleteAccount = () => {
  router.push({ name: 'DeleteAccount' });
};


/**
 * Stesso wording di HomePage e della dashboard admin: la conferma di uscita è
 * identica ovunque nell'app, così il gesto si impara una volta sola. La domanda
 * nomina l'azione ("Sign out?") invece di "Are you sure?", che da solo non dice
 * a cosa si sta rispondendo quando l'alert è l'unica cosa a schermo.
 */
const confirmLogout = async () => {
  const alert = await alertController.create({
    header: 'Sign out?',
    buttons: [
      { text: 'Cancel', role: 'cancel' },
      { text: 'Sign out', role: 'destructive', handler: () => auth.logout() }
    ]
  });
  await alert.present();
};

onMounted(() => {
  document.documentElement.setAttribute('data-theme', 'mocha');
});
</script>

<style scoped>
/* Safe padding for FAB and bottom nav */
.content-safe-area {
  --padding-bottom: var(--bottom-nav-space);
}

/* Centered container for content */
.settings-container {
  max-width: 450px;
  width: 100%;
  margin: var(--space-5) auto 0;
  padding: 0 var(--space-4);
  box-sizing: border-box;
}

/* Profile Header */
.profile-header {
  display: flex;
  align-items: center;
  gap: var(--space-4);
  padding: var(--space-4);
  border-radius: var(--radius-lg);
  color: var(--text);
  margin-bottom: var(--space-4);
  width: 100%;
  box-sizing: border-box;
}

.name-container {
  flex: 1;
  min-width: 0;
}

.hello {
  margin: 0;
  font-size: 0.9rem;
  opacity: 0.7;
}

.name {
  margin: 0;
  font-size: clamp(1.1rem, 2.5vw, 1.4rem);
  font-weight: bold;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.avatar-icon {
  font-size: 48px;
  background-color: var(--surface0, rgba(255, 255, 255, 0.08));
  border-radius: 50%;
  padding: 8px;
}

.edit-button {
  margin-left: auto;
  padding: 0;
  min-width: unset;
  height: auto;
}

/* Glass effect reused (solid surface variant for the profile header div) */
.glass-input {
  background-color: var(--surface0);
  border-radius: var(--radius-lg);
  backdrop-filter: blur(var(--glass-blur));
  -webkit-backdrop-filter: blur(var(--glass-blur));
  border: 1px solid var(--glass-border);
}

.input-icon {
  color: var(--peach);
  font-size: var(--font-lg);
}

.danger-text {
  color: var(--red) !important;
}

.form-wrapper {
  width: 100%;
}




/* Bottom Navigation */
.bottom-icons-container {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--space-6) calc(var(--space-5) + env(safe-area-inset-bottom));
  z-index: 100;
}

.bottom-icon {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.bottom-icon ion-icon {
  font-size: 28px;
  color: var(--overlay1); /* Default color for inactive icons */
}

/* Home icon */
.home-icon {
  pointer-events: auto;
}

/* Settings icon - highlighted */
.settings-icon ion-icon {
  color: var(--peach); /* Peach color for active tab */
}

/* Center logout button */
.logout-btn {
  width: 60px;
  height: 60px;
  border-radius: 50%;
  background: var(--surface0, rgba(255, 255, 255, 0.08));
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--red, #f45c5c);
  cursor: pointer;
  transition: transform 0.2s ease, background-color 0.2s ease;
}

.logout-btn:hover {
  background-color: rgba(255, 255, 255, 0.12);
  transform: scale(1.05);
}

.logout-btn ion-icon {
  font-size: 28px;
}

/* Optional hover effect for items */
ion-item.button::part(native):hover {
  background-color: rgba(255, 255, 255, 0.05);
}

ion-item {
  --inner-padding-top: 12px;
  --inner-padding-bottom: 12px;
  --min-height: 56px;
}
</style>
