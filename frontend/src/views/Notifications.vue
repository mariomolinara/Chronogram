<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/settings" />
        </ion-buttons>
        <ion-title>Notifications</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <ion-list class="glass-card">
        <ion-item>
          <ion-icon :icon="mailOutline" slot="start" class="purple-icon" />
          <ion-label>Email Notifications</ion-label>
          <ion-toggle v-model="emailNotifs" color="success"></ion-toggle>
        </ion-item>

        <ion-item>
          <ion-icon :icon="notificationsOutline" slot="start" class="purple-icon" />
          <ion-label>Push Notifications</ion-label>
          <ion-toggle v-model="pushNotifs" color="primary"></ion-toggle>
        </ion-item>

        <ion-item>
          <ion-icon :icon="chatbubbleOutline" slot="start" class="purple-icon" />
          <ion-label>SMS Notifications</ion-label>
          <ion-toggle v-model="smsNotifs" color="tertiary"></ion-toggle>
        </ion-item>
      </ion-list>
    </ion-content>
  </ion-page>
</template>

<script lang="ts">
// Nome multi-parola per soddisfare la regola ESLint vue/multi-word-component-names
// senza rinominare il file (referenziato dal router).
export default { name: 'NotificationsPage' };
</script>

<script setup lang="ts">
import { ref } from 'vue';
import {
  IonPage,
  IonHeader,
  IonToolbar,
  IonButtons,
  IonBackButton,
  IonTitle,
  IonContent,
  IonList,
  IonItem,
  IonIcon,
  IonLabel,
  IonToggle
} from '@ionic/vue';
import {
  mailOutline,
  notificationsOutline,
  chatbubbleOutline
} from 'ionicons/icons';

const emailNotifs = ref(true);
const pushNotifs = ref(false);
const smsNotifs = ref(false);
</script>

<style scoped>
.purple-icon {
  color: var(--mauve);
  font-size: 1.5rem;
}
</style>