<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <!-- Stessa freccia di SupportPage: la pagina è pubblica, quindi il
             ritorno dipende dallo stato di sessione (Settings da loggati,
             Login da ospiti). -->
        <ion-buttons slot="start">
          <ion-button aria-label="Back" @click="goBack">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" />
          </ion-button>
        </ion-buttons>
        <ion-title>About</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <!-- Il contenuto vive in AboutContent, condiviso col popup di avvio. -->
      <AboutContent />
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons,
  IonButton, IonIcon
} from '@ionic/vue';
import { arrowBackOutline } from 'ionicons/icons';
import { useRouter } from 'vue-router';
import { useAuthStore } from '@/store/auth';
import AboutContent from '@/components/AboutContent.vue';

const router = useRouter();
const auth = useAuthStore();

/**
 * La pagina è raggiungibile anche senza sessione (link dal login): il ritorno
 * porta alla base del proprio percorso — dashboard per l'amministratore,
 * Settings per l'utente autenticato, Login per gli ospiti.
 */
function goBack() {
  if (!auth.isAuthenticated) {
    router.push({ name: 'Login' });
  } else {
    router.push({ name: auth.isAdmin ? 'AdminDashboard' : 'Settings' });
  }
}
</script>
