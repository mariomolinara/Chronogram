import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { Preferences } from '@capacitor/preferences';
import { api } from '@/composables/useApi';
import { useRouter } from 'vue-router';

/**
 * Ruoli riconosciuti dal backend (`user_auth.role`).
 */
export type UserRole = 'USER' | 'ADMIN';

interface User {
    username: string;
    role: UserRole;
    /**
     * Vero finché l'account usa la password con cui è stato creato (caso
     * dell'amministratore provisionato da `.env`): il router impedisce di
     * navigare altrove finché non viene cambiata.
     */
    mustChangePassword: boolean;
}

/**
 * Risposta del backend all'endpoint di login (`/api/auth/login`).
 */
interface LoginResponse {
    success: boolean;
    token?: string;
    username: string;
    message?: string;
    role?: UserRole;
    mustChangePassword?: boolean;
}

/**
 * Chiavi usate per la persistenza della sessione.
 *
 * La persistenza usa `@capacitor/preferences` (storage nativo su Android,
 * localStorage-backed sul web) per uno storage coerente cross-platform. Le API
 * di Preferences sono asincrone, quindi le funzioni `persistSession`/
 * `clearSession`/`restoreSession` sono async; l'accesso allo storage resta
 * incapsulato qui e `getToken()` continua a leggere dallo stato in-memory.
 */
const TOKEN_STORAGE_KEY = 'authToken';
const USER_STORAGE_KEY = 'userData';

export const useAuthStore = defineStore('auth', () => {
    const user = ref<User | null>(null);
    const token = ref<string | null>(null);
    /** Evita di rileggere lo storage a ogni navigazione (vedi checkAuthStatus). */
    const sessionChecked = ref(false);
    const router = useRouter();

    const isAuthenticated = computed(() => !!token.value && !!user.value);
    const username = computed(() => user.value?.username);

    /**
     * Etichetta da mostrare nella UI. Il backend non conserva un nome proprio:
     * `LoginResponse.username` è l'email dell'account, quindi si usa la parte
     * locale dell'indirizzo, che è il dato più presentabile disponibile senza
     * introdurre endpoint nuovi. Stringa vuota se non c'è sessione: la vista
     * decide il proprio segnaposto.
     */
    const displayName = computed(() => {
        const name = user.value?.username?.trim();
        if (!name) {
            return '';
        }
        const atIndex = name.indexOf('@');
        return atIndex > 0 ? name.slice(0, atIndex) : name;
    });

    /**
     * Solo per decidere cosa mostrare nella UI. L'autorizzazione reale è del
     * backend, che rilegge il ruolo dal database a ogni richiesta: nascondere il
     * link non protegge nulla, protegge `hasRole('ADMIN')` su `/api/admin/**`.
     */
    const isAdmin = computed(() => user.value?.role === 'ADMIN');
    const mustChangePassword = computed(() => user.value?.mustChangePassword === true);

    /**
     * Unica fonte di verità per il token: l'interceptor axios deve leggere il
     * token da qui e non direttamente dallo storage.
     */
    function getToken(): string | null {
        return token.value;
    }

    // --- Persistenza incapsulata su @capacitor/preferences (storage nativo) ---

    async function persistSession(currentToken: string, currentUser: User): Promise<void> {
        await Preferences.set({ key: TOKEN_STORAGE_KEY, value: currentToken });
        await Preferences.set({ key: USER_STORAGE_KEY, value: JSON.stringify(currentUser) });
    }

    async function clearSession(): Promise<void> {
        await Preferences.remove({ key: TOKEN_STORAGE_KEY });
        await Preferences.remove({ key: USER_STORAGE_KEY });
    }

    async function restoreSession(): Promise<{ token: string; user: User } | null> {
        const { value: storedToken } = await Preferences.get({ key: TOKEN_STORAGE_KEY });
        const { value: storedUser } = await Preferences.get({ key: USER_STORAGE_KEY });

        if (!storedToken || !storedUser) {
            return null;
        }

        try {
            return { token: storedToken, user: JSON.parse(storedUser) as User };
        } catch {
            // Dati corrotti nello storage: puliamo per evitare stati incoerenti.
            await clearSession();
            return null;
        }
    }

    // --- Azioni pubbliche ---

    async function login(credentials: { email: string; password: string }): Promise<LoginResponse> {
        const response = await api.post<LoginResponse>('/api/auth/login', credentials);
        const data = response.data;

        if (!data.success || !data.token) {
            throw new Error(data.message || 'Login failed');
        }

        token.value = data.token;
        user.value = {
            username: data.username,
            // Backend precedenti alla V3 non inviano il ruolo: si degrada a USER,
            // il caso meno privilegiato.
            role: data.role ?? 'USER',
            mustChangePassword: data.mustChangePassword === true
        };

        await persistSession(data.token, user.value);

        return data;
    }

    /**
     * Da chiamare dopo un cambio password riuscito: toglie il blocco senza
     * costringere l'utente a rifare il login.
     */
    async function clearMustChangePassword(): Promise<void> {
        if (!user.value || !token.value) {
            return;
        }
        user.value = { ...user.value, mustChangePassword: false };
        await persistSession(token.value, user.value);
    }

    async function logout() {
        token.value = null;
        user.value = null;
        // La sessione resta "verificata": lo storage è appena stato ripulito.
        sessionChecked.value = true;

        await clearSession();
        router.push('/login');
    }

    async function checkAuthStatus() {
        // Il router può chiamarla su ogni navigazione: dopo il primo tentativo lo
        // stato in memoria è già autorevole, inutile rileggere lo storage.
        if (sessionChecked.value) {
            return;
        }
        sessionChecked.value = true;

        const restored = await restoreSession();

        if (restored) {
            token.value = restored.token;
            user.value = restored.user;
        }
    }

    return {
        user,
        token,
        isAuthenticated,
        username,
        displayName,
        isAdmin,
        mustChangePassword,
        getToken,
        login,
        logout,
        checkAuthStatus,
        clearMustChangePassword
    };
});