// Importazione delle dipendenze necessarie
import axios from 'axios'; // Libreria per effettuare chiamate HTTP
import { useAuthStore } from '@/store/auth'; // Store Pinia per la gestione dell'autenticazione
import router from '@/router'; // Istanza singleton del router (vedi nota nell'interceptor)
import { toastController } from '@ionic/vue'; // Controller per mostrare notifiche toast
import { isPublicApiPath } from '@/constants/apiRoutes'; // Rotte pubbliche condivise

/**
 * URL base dell'API risolto dalle variabili d'ambiente Vite.
 *
 * Il valore proviene da `VITE_API_BASE_URL` (definito nei file `.env.*`) e
 * DEVE includere il context-path del backend `/chronogram`, perché le chiamate
 * usano path tipo `/api/auth/login` senza tale prefisso.
 *
 * Fallback difensivo: se la variabile non è definita (file `.env` mancante o
 * mal configurato) emettiamo un warning chiaro e ripieghiamo su un base URL
 * relativo (`/chronogram`). Così le richieste restano same-origin dietro nginx
 * invece di puntare a `undefined/api/...`, e nessuna URL viene hardcodata.
 */
const resolvedBaseURL = import.meta.env.VITE_API_BASE_URL ?? '/chronogram';

if (!import.meta.env.VITE_API_BASE_URL) {
    console.warn(
        '[useApi] VITE_API_BASE_URL non definita: fallback su "/chronogram" ' +
        '(same-origin). Configura un file .env.* con VITE_API_BASE_URL ' +
        '(es. http://localhost:8080/chronogram in sviluppo).'
    );
}

/**
 * Creazione di un'istanza axios preconfigurata per l'API
 *
 * L'istanza viene configurata con:
 * - baseURL: preso dalle variabili d'ambiente (VITE_API_BASE_URL)
 * - headers: impostati per accettare/restituire JSON
 *
 * Questa istanza sarà utilizzata in tutta l'applicazione per le chiamate API
 */
export const api = axios.create({
    baseURL: resolvedBaseURL, // URL base dell'API da variabile d'ambiente (con fallback)
    headers: {
        'Content-Type': 'application/json', // Specifica che le richieste sono in JSON
    },
});

/**
 * Estrae il messaggio da mostrare all'utente a partire da un errore di rete.
 *
 * Ordine di preferenza:
 * 1. `response.data.message` — il testo scritto dal backend nell'envelope
 *    `ApiResponse` (es. "Only a pending account can be approved"): è l'unico che
 *    spiega davvero cosa è successo;
 * 2. il messaggio di un `Error` applicativo lanciato dal client;
 * 3. il fallback fornito dal chiamante.
 *
 * Il caso "richiesta partita ma nessuna risposta" (server irraggiungibile)
 * scavalca il punto 2: "Network Error" di axios non dice nulla all'utente.
 */
export function apiErrorMessage(error: unknown, fallback: string): string {
    const candidate = error as {
        response?: { data?: { message?: string } };
        request?: unknown;
        message?: string;
    } | null;

    const fromServer = candidate?.response?.data?.message;
    if (typeof fromServer === 'string' && fromServer.trim()) {
        return fromServer;
    }

    if (candidate?.request && !candidate.response) {
        return 'Could not reach the server. Please check your connection and try again.';
    }

    if (error instanceof Error && error.message.trim()) {
        return error.message;
    }

    return fallback;
}

/**
 * Funzione per inizializzare gli intercettori di risposta axios
 *
 * Gli intercettori permettono di:
 * 1. Intercettare tutte le risposte dall'API
 * 2. Gestire gli errori in modo centralizzato
 * 3. Eseguire azioni specifiche per determinati codici di stato (es. 401)
 *
 * Nota: Deve essere chiamata durante l'inizializzazione dell'app (tipicamente in main.js/ts)
 */
export function initApiInterceptors() {

    api.interceptors.request.use(
        config => {
            const authStore = useAuthStore();
            // Unica fonte di verità per il token: lo store `auth`.
            const token = authStore.getToken();

            // Aggiungi il token solo se la rotta non è pubblica e se il token è presente
            if (!isPublicApiPath(config.url) && token) {
                config.headers.Authorization = `Bearer ${token}`;
            }

            return config;
        },
        error => Promise.reject(error)
    );

    /**
     * Intercettore per le risposte
     *
     * La struttura è response.use(successHandler, errorHandler)
     * In questo caso gestiamo solo gli errori (secondo parametro)
     */
    api.interceptors.response.use(
        // Handler per risposte con successo - semplicemente le passa attraverso
        response => response,

        // Handler per errori - contiene la logica principale
        async error => {
            // Lo store va risolto dentro l'handler (serve una Pinia attiva), il
            // router no: qui siamo in un callback axios, fuori da qualsiasi
            // `setup()`, quindi `useRouter()` restituirebbe `undefined` e la
            // gestione del 401 esploderebbe su `router.currentRoute`. Si usa
            // l'istanza singleton importata a livello di modulo.
            const authStore = useAuthStore();

            /**
             * Gestione specifica per errori 401 Unauthorized.
             *
             * Il token è scaduto o è stato revocato mentre l'app lo teneva
             * ancora per buono: si chiude la sessione e si torna al login,
             * conservando la pagina da cui si veniva.
             *
             * Il redirect NON è più duplicato (prima lo facevano sia `logout()`
             * sia questo handler, con la prima navigazione annullata dalla
             * seconda e una promise rifiutata a vuoto) e non è più condizionato
             * a `currentRoute`: `logout()` è l'unico responsabile del ritorno al
             * login, così una sola navigazione parte e un suo fallimento non
             * lascia l'utente su una pagina privata senza sessione.
             */
            if (error.response && error.response.status === 401) {
                const from = router.currentRoute.value;
                const target = from.name === 'Login' || !from.matched.length
                    ? undefined
                    : from.fullPath;

                await authStore.logout(target);

                const toast = await toastController.create({
                    message: 'Sessione scaduta o non autorizzata. Effettua nuovamente il login.',
                    duration: 3000,
                    color: 'warning'
                });
                await toast.present();
            }

            // Propaga l'errore per permettere una gestione specifica nei componenti
            return Promise.reject(error);
        }
    );
}
