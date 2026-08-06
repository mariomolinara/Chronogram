<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/settings" />
        </ion-buttons>
        <ion-title>Delete account</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <div class="delete-wrapper">
        <h2 class="title">Are you sure you want to delete your account?</h2>
        <p class="description">
          Once you delete your account, it cannot be undone. All your data will be permanently erased from this app: your profile information, preferences, saved content, and any activity history.
        </p>
        <p class="description">
          We're sad to see you go, but we understand that sometimes it’s necessary. Please take a moment to consider the consequences before proceeding.
        </p>

        <ion-button expand="block" color="danger" class="delete-button" @click="goToFeedback">
          Delete account
        </ion-button>

        <ion-button expand="block" fill="outline" class="cancel-button" router-link="/settings">
          Go back
        </ion-button>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
// Import espliciti dei componenti Ionic usati nel template, come nelle altre
// viste: senza registrazione il tag resta un custom element sconosciuto e
// `ion-back-button` finisce per non renderizzare nulla di cliccabile.
import {
  IonPage, IonHeader, IonToolbar, IonButtons, IonBackButton, IonTitle,
  IonContent, IonButton
} from '@ionic/vue'
import { useRouter } from 'vue-router'

const router = useRouter()

/**
 * Passa alla raccolta dei motivi, dove avviene la cancellazione vera.
 *
 * Il nome della rotta è `DeleteReasons` (vedi `router/index.ts`): al singolare
 * `router.push` non trovava alcuna rotta corrispondente e il tap moriva in
 * silenzio, senza navigazione e senza errore visibile all'utente.
 */
const goToFeedback = () => {
  router.push({ name: 'DeleteReasons' })
}
</script>

<style scoped>
.delete-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
  margin-top: var(--space-6);
  max-width: 450px;
  margin-inline: auto;
}

.title {
  font-size: var(--font-lg);
  font-weight: var(--font-weight-bold);
}

.description {
  font-size: var(--font-base);
  color: var(--subtext0);
}

.delete-button {
  margin-top: var(--space-4);
}

.cancel-button {
  margin-top: var(--space-2);
}
</style>
