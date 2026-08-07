<template>
  <!-- Popup di avvio con le informazioni dell'About. Compare a ogni apertura
       dell'app finché l'utente non spunta "Don't show this on the next
       start-up" e conferma: la scelta è persistita con @capacitor/preferences
       (SharedPreferences su Android, localStorage sul web), quindi sopravvive
       alla chiusura dell'app ma non alla sua reinstallazione. -->
  <ion-modal
      :is-open="isOpen"
      :backdrop-dismiss="false"
      aria-label="About Chronogram"
      @didDismiss="isOpen = false"
  >
    <ion-header>
      <ion-toolbar>
        <ion-title>About Chronogram</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <AboutContent />
    </ion-content>

    <ion-footer class="modal-footer">
      <ion-checkbox v-model="dontShowAgain" label-placement="end" justify="start">
        Don't show this on the next start-up
      </ion-checkbox>
      <ion-button expand="block" class="ok-button" @click="dismiss">
        OK
      </ion-button>
    </ion-footer>
  </ion-modal>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import {
  IonModal, IonHeader, IonToolbar, IonTitle, IonContent, IonFooter,
  IonCheckbox, IonButton
} from '@ionic/vue';
import { Preferences } from '@capacitor/preferences';
import AboutContent from '@/components/AboutContent.vue';

/** Stessa area di storage di authToken/userData (vedi store/auth.ts). */
const ABOUT_POPUP_HIDDEN_KEY = 'aboutPopupHidden';

const isOpen = ref(false);
const dontShowAgain = ref(false);

onMounted(async () => {
  const { value } = await Preferences.get({ key: ABOUT_POPUP_HIDDEN_KEY });
  if (value !== 'true') {
    isOpen.value = true;
  }
});

/**
 * Il flag si scrive solo alla conferma: chiudere l'app col popup aperto non
 * conta come scelta, e al prossimo avvio il popup ricompare.
 */
async function dismiss() {
  if (dontShowAgain.value) {
    await Preferences.set({ key: ABOUT_POPUP_HIDDEN_KEY, value: 'true' });
  }
  isOpen.value = false;
}
</script>

<style scoped>
.modal-footer {
  padding: var(--space-3) var(--space-4) calc(var(--space-3) + env(safe-area-inset-bottom));
  background: var(--surface0);
}

.modal-footer ion-checkbox {
  display: block;
  margin-bottom: var(--space-3);
  color: var(--subtext0);
  font-size: var(--font-sm);
}

.ok-button {
  --background: var(--gradient-pink-mauve);
  --color: var(--crust);
  font-weight: var(--font-weight-bold);
  --border-radius: var(--radius-md);
}
</style>
