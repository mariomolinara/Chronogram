import { defineStore } from 'pinia';
import { ref, computed } from 'vue';
import { Preferences } from '@capacitor/preferences';
import { api } from '@/composables/useApi';
import { useRouter } from 'vue-router';

interface User {
    username: string;
}

/**
 * Risposta del backend all'endpoint di login (`/api/auth/login`).
 */
interface LoginResponse {
    success: boolean;
    token?: string;
    username: string;
    message?: string;
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
    const router = useRouter();

    const isAuthenticated = computed(() => !!token.value && !!user.value);
    const username = computed(() => user.value?.username);

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
        user.value = { username: data.username };

        await persistSession(data.token, user.value);

        return data;
    }

    async function logout() {
        token.value = null;
        user.value = null;

        await clearSession();
        router.push('/login');
    }

    async function checkAuthStatus() {
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
        getToken,
        login,
        logout,
        checkAuthStatus
    };
});