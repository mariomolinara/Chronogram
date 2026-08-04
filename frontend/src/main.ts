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

// ⿦ Esegui il mount dopo auth + interceptor
router.isReady().then(async () => {
  const authStore = useAuthStore();
  await authStore.checkAuthStatus();

  app.mount('#app');
  initApiInterceptors();
});