import { createApp } from 'vue';
import App from './App.vue';
import router from './router';
import { IonicVue } from '@ionic/vue';
import { createPinia } from 'pinia';
import { initApiInterceptors } from '@/composables/useApi';
import { useAuthStore } from '@/store/auth';

/* Ionic CSS */
import '@ionic/vue/css/core.css';
import '@ionic/vue/css/normalize.css';
import '@ionic/vue/css/structure.css';
import '@ionic/vue/css/typography.css';
import '@ionic/vue/css/padding.css';
import '@ionic/vue/css/float-elements.css';
import '@ionic/vue/css/text-alignment.css';
import '@ionic/vue/css/text-transformation.css';
import '@ionic/vue/css/flex-utils.css';
import '@ionic/vue/css/display.css';

/* Custom themes */
import './theme/variables.css';
import '@/theme/catppuccin.scss';

// ⿡ Crea l'app
const app = createApp(App);

// ⿢ Crea Pinia separatamente
const pinia = createPinia();

// ⿣ Espone lo store Pinia e collega i devtools SOLO in sviluppo.
// In produzione questo blocco viene escluso dal tree-shaking di Vite.
if (import.meta.env.DEV) {
  (window as unknown as { __PINIA__?: typeof pinia }).__PINIA__ = pinia;

  // @vue/devtools si auto-inizializza all'import (nessun IP hardcodato:
  // usare l'estensione browser o l'app standalone su localhost).
  import('@vue/devtools').catch(() => {
    /* devtools non disponibili: ininfluente per l'app */
  });
}

// ⿤ Monta tutti i plugin
// Pinia PRIMA del router: installare il router avvia subito la navigazione
// iniziale, e il guard in router/index.ts usa lo store `auth`. Con l'ordine
// invertito lo store verrebbe creato senza una Pinia attiva.
app.use(IonicVue);
app.use(pinia);
app.use(router);

// ⿥ Imposta tema
document.documentElement.setAttribute('data-theme', 'mocha');

// ⿦ Sessione e interceptor PRIMA del mount.
//
// L'ordine è vincolante, non stilistico. `app.mount()` monta la pagina della
// rotta iniziale ed esegue i suoi `onMounted` in modo sincrono: le chiamate API
// che partono lì dentro fotografano la catena di interceptor esistente in quel
// momento. Registrandoli dopo il mount, la PRIMA richiesta di ogni avvio partiva
// senza header `Authorization` e senza gestione del 401 — misurato sull'app
// Android: `POST /api/activities/list auth=-` al lancio, `auth=Bearer ...` solo
// dopo aver premuto "Retry". Restava nascosto finché l'avvio a freddo finiva
// comunque sul login; da quando il guard di sessione porta un utente già
// autenticato direttamente in Home, si vedeva a ogni lancio come "Couldn't load
// your activities".
router.isReady().then(async () => {
  const authStore = useAuthStore();
  await authStore.checkAuthStatus();

  initApiInterceptors();
  app.mount('#app');
});