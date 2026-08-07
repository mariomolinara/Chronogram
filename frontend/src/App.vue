<template>
  <ion-app>
    <ion-router-outlet />
    <!-- Popup di primo avvio con le informazioni dell'About; si autodisattiva
         se l'utente spunta "Don't show this on the next start-up". -->
    <AboutStartupModal />
  </ion-app>
</template>

<script setup lang="ts">
import { IonApp, IonRouterOutlet } from '@ionic/vue';
import AboutStartupModal from '@/components/AboutStartupModal.vue';
</script>
