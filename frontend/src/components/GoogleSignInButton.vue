<template>
  <!-- Tutto il blocco (divisore incluso) sparisce quando VITE_GOOGLE_CLIENT_ID
       è vuoto: l'alternativa Google esiste solo se configurata su entrambi i
       lati (il backend nasconde la sua parte dietro GOOGLE_CLIENT_IDS). -->
  <div v-if="isAvailable" class="google-signin">
    <div class="divider" role="separator" aria-label="or">
      <span>or</span>
    </div>

    <!-- Su web il bottone lo disegna Google Identity Services: per il flusso a
         ID token GIS non offre un'API "headless" richiamabile da un bottone
         nostro. Su Android il flusso è nativo (Credential Manager) e il bottone
         è un normale ion-button. -->
    <div v-if="!isNative" ref="gisButtonHost" class="gis-button-host"></div>

    <ion-button
        v-else
        expand="block"
        class="pill-button gradient-outline"
        :disabled="busy"
        @click="signInNative"
    >
      <ion-icon slot="start" :icon="logoGoogle" aria-hidden="true" />
      Continue with Google
    </ion-button>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { IonButton, IonIcon } from '@ionic/vue';
import { logoGoogle } from 'ionicons/icons';
import { Capacitor } from '@capacitor/core';

/**
 * Accesso/registrazione con Google.
 *
 * Il componente produce SOLO l'ID token firmato da Google e lo consegna al
 * genitore con l'evento `credential`: è il backend (`/api/auth/google`) a
 * verificarlo e a decidere se creare l'account o aprire la sessione. Nessuna
 * decisione di autenticazione viene presa qui.
 */

const emit = defineEmits<{
  (e: 'credential', idToken: string): void;
  (e: 'error', message: string): void;
}>();

const clientId: string | undefined = import.meta.env.VITE_GOOGLE_CLIENT_ID;
const isAvailable = !!clientId;
const isNative = Capacitor.isNativePlatform();
const gisButtonHost = ref<HTMLElement | null>(null);
const busy = ref(false);

/* ---------- web: Google Identity Services ---------- */

const GIS_SCRIPT_ID = 'google-gsi-client';

function loadGis(): Promise<void> {
  return new Promise((resolve, reject) => {
    const existing = document.getElementById(GIS_SCRIPT_ID) as HTMLScriptElement | null;
    if (existing) {
      // Già richiesto da un'altra pagina: se l'oggetto globale c'è, è pronto;
      // altrimenti si aggancia al suo stesso caricamento.
      if ((window as any).google?.accounts?.id) {
        resolve();
      } else {
        existing.addEventListener('load', () => resolve());
        existing.addEventListener('error', () => reject(new Error('Google sign-in failed to load.')));
      }
      return;
    }
    const script = document.createElement('script');
    script.id = GIS_SCRIPT_ID;
    script.src = 'https://accounts.google.com/gsi/client';
    script.async = true;
    script.defer = true;
    script.onload = () => resolve();
    script.onerror = () => reject(new Error('Google sign-in failed to load.'));
    document.head.appendChild(script);
  });
}

onMounted(async () => {
  if (!isAvailable || isNative) {
    return;
  }
  try {
    await loadGis();
    const googleId = (window as any).google?.accounts?.id;
    if (!googleId || !gisButtonHost.value) {
      return;
    }
    googleId.initialize({
      client_id: clientId,
      callback: (response: { credential?: string }) => {
        if (response.credential) {
          emit('credential', response.credential);
        } else {
          emit('error', 'Google sign-in failed. Please try again.');
        }
      },
    });
    googleId.renderButton(gisButtonHost.value, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      text: 'continue_with',
      shape: 'pill',
      logo_alignment: 'center',
    });
  } catch (err) {
    // Bottone assente ma pagina funzionante: la registrazione standard resta
    // la via principale, il fallimento va solo segnalato in console.
    console.warn('Google Identity Services unavailable:', err);
  }
});

/* ---------- Android: Credential Manager via @capgo/capacitor-social-login ---------- */

let nativeInitialized = false;

async function signInNative() {
  busy.value = true;
  try {
    const { SocialLogin } = await import('@capgo/capacitor-social-login');
    if (!nativeInitialized) {
      // webClientId anche su Android: il token deve essere emesso per il client
      // WEB, che è quello che il backend riconosce (GOOGLE_CLIENT_IDS).
      await SocialLogin.initialize({ google: { webClientId: clientId as string } });
      nativeInitialized = true;
    }
    const res = await SocialLogin.login({
      provider: 'google',
      options: { scopes: ['email', 'profile'] },
    });
    const idToken = (res.result as { idToken?: string | null } | undefined)?.idToken;
    if (idToken) {
      emit('credential', idToken);
    } else {
      emit('error', 'Google sign-in did not return a credential. Please try again.');
    }
  } catch (err: unknown) {
    // L'annullamento da parte dell'utente non è un errore da mostrare.
    const message = err instanceof Error ? err.message : String(err);
    if (!/cancel/i.test(message)) {
      emit('error', 'Google sign-in failed. Please try again.');
    }
  } finally {
    busy.value = false;
  }
}
</script>

<style scoped>
.google-signin {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  margin-top: var(--space-4);
}
.divider {
  display: flex;
  align-items: center;
  gap: var(--space-3);
  color: var(--subtext0);
  font-size: var(--font-sm);
}
.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--surface1);
}
.gis-button-host {
  display: flex;
  justify-content: center;
  /* Il bottone GIS è un iframe bianco: su tema scuro resta leggibile, ma
     senza min-height la pagina "salta" quando l'iframe arriva. */
  min-height: 44px;
}
</style>
