import { onScopeDispose, ref, watch } from 'vue';
import { api, apiErrorMessage } from '@/composables/useApi';

/**
 * Stato del ciclo di vita di un account, allineato a
 * `it.unicas.chronogram.domain.AccountStatus`.
 */
export type AccountStatus = 'PENDING' | 'ACTIVE' | 'BLOCKED';

/** Valore del filtro: uno stato oppure "tutti". */
export type StatusFilter = AccountStatus | 'ALL';

/**
 * Azioni che l'amministratore può eseguire su un account.
 *
 * `reject` non è un endpoint a sé: rifiutare una richiesta in attesa significa
 * bloccarla (vedi `ACTION_ENDPOINT`). Resta però un'azione distinta lato UI
 * perché parte da uno stato diverso, ha un'altra conseguenza per chi la subisce
 * ("la tua richiesta non è stata accolta" invece di "il tuo accesso è sospeso")
 * e va confermata con un testo suo.
 */
export type AdminUserAction = 'approve' | 'reject' | 'block' | 'unblock' | 'delete';

/**
 * Segmento di path chiamato per ciascuna azione.
 *
 * L'unico scostamento è `reject -> block`: `AdminUserService.block` accetta un
 * account PENDING e lo porta a BLOCKED senza cancellarne il record, che è
 * esattamente il rifiuto. Il contratto REST resta quello esistente.
 */
const ACTION_ENDPOINT: Record<AdminUserAction, string> = {
    approve: 'approve',
    reject: 'block',
    block: 'block',
    unblock: 'unblock',
    delete: 'delete'
};

/**
 * Una riga della lista partecipanti (`AdminUserResponse` lato backend).
 *
 * Le date arrivano come stringhe ISO serializzate da `LocalDateTime`/`LocalDate`
 * (nessun offset): la formattazione è responsabilità della vista.
 */
export interface AdminUser {
    userId: number;
    email: string;
    name: string | null;
    surname: string | null;
    status: AccountStatus;
    registeredAt: string | null;
    lastLogin: string | null;
    activityCount: number;
    lastActivityDay: string | null;
}

/** Totali per stato, indipendenti dal filtro corrente. */
export interface StatusCounts {
    pending: number;
    active: number;
    blocked: number;
}

/** Pagina della lista (`AdminUserPageResponse` lato backend). */
export interface AdminUsersPage {
    items: AdminUser[];
    page: number;
    size: number;
    totalItems: number;
    totalPages: number;
    counts: StatusCounts;
}

/** Envelope standard delle API (`ApiResponse`). */
interface ApiEnvelope<T> {
    success: boolean;
    message: string;
    data?: T;
}

/** Esito di un'azione, già pronto per il toast della vista. */
export interface ActionOutcome {
    ok: boolean;
    message: string;
}

export interface UseAdminUsersOptions {
    /** Righe per pagina richieste al backend (max lato server: 100). */
    pageSize?: number;
    /** Attesa prima di trasformare la digitazione in una richiesta. */
    searchDebounceMs?: number;
}

const USERS_ENDPOINT = '/api/admin/users';
const DEFAULT_PAGE_SIZE = 25;
const DEFAULT_SEARCH_DEBOUNCE_MS = 350;

const EMPTY_COUNTS: StatusCounts = { pending: 0, active: 0, blocked: 0 };

/**
 * Stato della console utenti: elenco filtrato/paginato e azioni amministrative.
 *
 * Perché un composable e non uno store Pinia: questo stato vive quanto la pagina
 * che lo consuma e non è condiviso con altre viste; renderlo globale
 * significherebbe solo doverlo ripulire a mano ad ogni ingresso. La forma
 * (`ref` + funzioni, tipi espliciti) resta quella degli store del progetto ed è
 * testabile senza montare la vista.
 *
 * Due invarianti che la vista dà per acquisite:
 * - dopo un'azione la lista si ricarica mantenendo filtro, ricerca e pagina;
 * - le risposte fuori ordine (ricerca digitata in fretta) vengono scartate.
 */
export function useAdminUsers(options: UseAdminUsersOptions = {}) {
    const pageSize = options.pageSize ?? DEFAULT_PAGE_SIZE;
    const debounceMs = options.searchDebounceMs ?? DEFAULT_SEARCH_DEBOUNCE_MS;

    const items = ref<AdminUser[]>([]);
    const counts = ref<StatusCounts>({ ...EMPTY_COUNTS });
    const page = ref(0);
    const totalPages = ref(0);
    const totalItems = ref(0);

    const status = ref<StatusFilter>('ALL');
    /** Testo digitato, legato al campo di ricerca. */
    const searchInput = ref('');
    /** Testo effettivamente inviato al backend (aggiornato dopo il debounce). */
    const searchTerm = ref('');

    const loading = ref(false);
    const error = ref<string | null>(null);
    /** Id dell'utente su cui è in corso un'azione, per disabilitarne i pulsanti. */
    const actionInProgress = ref<number | null>(null);

    /**
     * Ogni caricamento riceve un numero progressivo: quando ne parte uno nuovo
     * le risposte dei precedenti diventano irrilevanti. Senza questo, digitare
     * "mar" e poi "mario" può lasciare a schermo i risultati di "mar" se la
     * prima risposta arriva per ultima.
     */
    let requestToken = 0;
    let searchTimer: ReturnType<typeof setTimeout> | null = null;

    function cancelPendingSearch(): void {
        if (searchTimer !== null) {
            clearTimeout(searchTimer);
            searchTimer = null;
        }
    }

    async function fetchPage(pageIndex: number): Promise<AdminUsersPage> {
        const { data } = await api.get<ApiEnvelope<AdminUsersPage>>(USERS_ENDPOINT, {
            params: {
                status: status.value === 'ALL' ? undefined : status.value,
                query: searchTerm.value.trim() || undefined,
                page: pageIndex,
                size: pageSize
            }
        });

        if (!data?.data) {
            throw new Error(data?.message || 'Malformed response from the server.');
        }
        return data.data;
    }

    function apply(result: AdminUsersPage): void {
        items.value = result.items ?? [];
        counts.value = result.counts ?? { ...EMPTY_COUNTS };
        page.value = result.page;
        totalItems.value = result.totalItems;
        totalPages.value = result.totalPages;
    }

    /** Carica la pagina corrente con filtro e ricerca correnti. */
    async function load(): Promise<void> {
        const token = ++requestToken;
        loading.value = true;
        error.value = null;

        try {
            let result = await fetchPage(page.value);

            // Una cancellazione può svuotare l'ultima pagina: invece di mostrare
            // "nessun utente" con dei risultati esistenti, si arretra di una pagina.
            if (result.items.length === 0 && result.totalItems > 0 && page.value > 0) {
                const fallbackPage = Math.min(page.value - 1, Math.max(result.totalPages - 1, 0));
                result = await fetchPage(fallbackPage);
            }

            if (token !== requestToken) {
                return;
            }
            apply(result);
        } catch (err) {
            if (token !== requestToken) {
                return;
            }
            // Il 401 è già gestito dall'interceptor (logout + redirect al login):
            // qui resta il servizio non raggiungibile o un errore applicativo.
            error.value = apiErrorMessage(err, 'Could not load the user list. Please try again.');
            items.value = [];
        } finally {
            if (token === requestToken) {
                loading.value = false;
            }
        }
    }

    /** Cambia il filtro di stato e torna alla prima pagina. */
    async function setStatus(next: StatusFilter): Promise<void> {
        if (status.value === next) {
            return;
        }
        status.value = next;
        page.value = 0;
        await load();
    }

    /** Applica subito il testo digitato, senza aspettare il debounce. */
    async function applySearchNow(): Promise<void> {
        cancelPendingSearch();
        const next = searchInput.value.trim();
        if (next === searchTerm.value) {
            return;
        }
        searchTerm.value = next;
        page.value = 0;
        await load();
    }

    async function goToPage(next: number): Promise<void> {
        const last = Math.max(totalPages.value - 1, 0);
        const target = Math.min(Math.max(next, 0), last);
        if (target === page.value) {
            return;
        }
        page.value = target;
        await load();
    }

    const nextPage = () => goToPage(page.value + 1);
    const previousPage = () => goToPage(page.value - 1);

    /**
     * Esegue l'azione e ricarica la lista senza toccare filtri, ricerca o pagina.
     *
     * Il messaggio è facoltativo su approve/block/unblock e obbligatorio sulla
     * cancellazione (`DeleteUserRequest.message` è `@NotBlank`): il controllo qui
     * evita un 400 prevedibile, non sostituisce quello del server.
     */
    async function runAction(
        user: AdminUser,
        action: AdminUserAction,
        message: string
    ): Promise<ActionOutcome> {
        const trimmed = message.trim();
        if (action === 'delete' && !trimmed) {
            return { ok: false, message: 'A message for the user is required.' };
        }

        actionInProgress.value = user.userId;
        try {
            const { data } = await api.post<ApiEnvelope<unknown>>(
                `${USERS_ENDPOINT}/${user.userId}/${ACTION_ENDPOINT[action]}`,
                { message: trimmed || null }
            );
            await load();
            return { ok: true, message: data?.message || 'Done.' };
        } catch (err) {
            return {
                ok: false,
                message: apiErrorMessage(err, 'The action could not be completed. Please try again.')
            };
        } finally {
            actionInProgress.value = null;
        }
    }

    // La digitazione diventa una richiesta solo quando l'utente si ferma.
    watch(searchInput, (value) => {
        cancelPendingSearch();
        searchTimer = setTimeout(() => {
            searchTimer = null;
            const next = value.trim();
            if (next === searchTerm.value) {
                return;
            }
            searchTerm.value = next;
            page.value = 0;
            void load();
        }, debounceMs);
    });

    onScopeDispose(() => {
        cancelPendingSearch();
        // Le risposte ancora in volo non devono scrivere su stato smontato.
        requestToken++;
    });

    return {
        // stato
        items,
        counts,
        page,
        totalPages,
        totalItems,
        pageSize,
        status,
        searchInput,
        searchTerm,
        loading,
        error,
        actionInProgress,
        // azioni
        load,
        setStatus,
        applySearchNow,
        goToPage,
        nextPage,
        previousPage,
        runAction
    };
}
