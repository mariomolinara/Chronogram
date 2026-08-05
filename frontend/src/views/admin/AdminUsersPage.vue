<template>
  <ion-page>
    <ion-header translucent>
      <ion-toolbar color="dark">
        <ion-buttons slot="start">
          <ion-button class="tap-target" aria-label="Back to the dashboard" @click="goToDashboard">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" class="back-icon" />
          </ion-button>
        </ion-buttons>
        <ion-title class="ion-text-center title-peach">Participants</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true" class="ion-padding content-safe-area">
      <div class="admin-container">

        <!-- ── Coda delle richieste in attesa ───────────────────────── -->
        <!-- PENDING è l'unico stato che richiede una decisione: sta in testa
             alla pagina e resta visibile finché la coda non è vuota. La barra
             d'accento e il fondo appena tinto la fanno leggere come "da fare"
             prima ancora di leggerne il testo. -->
        <section
            v-if="counts.pending > 0"
            class="glass-card pending-banner"
            aria-labelledby="pending-heading"
        >
          <div class="pending-text">
            <h2 id="pending-heading" class="section-title">
              <ion-icon :icon="hourglassOutline" aria-hidden="true" />
              {{ counts.pending }}
              {{ counts.pending === 1 ? 'registration is' : 'registrations are' }}
              waiting for approval
            </h2>
            <p class="panel-note">
              Nobody can sign in until their account is approved.
            </p>
          </div>
          <ion-button
              v-if="status !== 'PENDING'"
              size="small"
              class="pending-cta"
              @click="setStatus('PENDING')"
          >
            Review them
          </ion-button>
          <p v-else class="pending-current" role="status">
            <ion-icon :icon="checkmarkCircleOutline" aria-hidden="true" />
            You are looking at the queue
          </p>
        </section>

        <!-- ── Filtri ───────────────────────────────────────────────── -->
        <section class="glass-card panel filters" aria-labelledby="filters-heading">
          <h2 id="filters-heading" class="sr-only">Search and filters</h2>

          <ion-searchbar
              v-model="searchInput"
              class="searchbar"
              placeholder="Search by email, name or surname"
              :debounce="0"
              inputmode="search"
              enterkeyhint="search"
              aria-label="Search participants by email, name or surname"
              @keydown.enter="applySearchNow"
              @ionClear="applySearchNow"
          />

          <ion-segment
              :value="status"
              scrollable
              class="status-segment"
              aria-label="Filter by account status"
              @ionChange="onStatusChange"
          >
            <ion-segment-button
                v-for="option in statusOptions"
                :key="option.value"
                :value="option.value"
                :aria-label="`${option.label}: ${option.count} ${option.count === 1 ? 'account' : 'accounts'}`"
            >
              <ion-label>
                {{ option.label }}
                <span
                    class="segment-count"
                    :class="{ 'segment-count--alert': option.value === 'PENDING' && option.count > 0 }"
                    aria-hidden="true"
                >{{ option.count }}</span>
              </ion-label>
            </ion-segment-button>
          </ion-segment>
        </section>

        <!-- ── Stati della lista ────────────────────────────────────── -->
        <!--
          Lo scheletro tiene la stessa griglia delle righe reali: al termine del
          caricamento la pagina non salta e l'occhio resta dov'era.
        -->
        <div v-if="loading" class="loading-block">
          <p class="sr-only" role="status" aria-live="polite">Loading participants…</p>
          <ul class="user-list" aria-hidden="true">
            <li v-for="placeholder in 3" :key="placeholder" class="glass-card user-row user-row--skeleton">
              <div class="user-identity">
                <ion-skeleton-text :animated="true" style="width: 55%; height: 1rem" />
                <ion-skeleton-text :animated="true" style="width: 75%; height: 0.8rem" />
              </div>
              <ion-skeleton-text :animated="true" class="skeleton-chip" />
              <div class="skeleton-meta">
                <ion-skeleton-text v-for="cell in 4" :key="cell" :animated="true" style="height: 1.6rem" />
              </div>
            </li>
          </ul>
        </div>

        <div v-else-if="error" class="state-block state-block--error" role="alert">
          <ion-icon :icon="alertCircleOutline" />
          <p>{{ error }}</p>
          <ion-button fill="outline" size="small" @click="load">
            <ion-icon slot="start" :icon="refreshOutline" aria-hidden="true" />
            Retry
          </ion-button>
        </div>

        <div v-else-if="items.length === 0" class="state-block" role="status" aria-live="polite">
          <ion-icon :icon="hasFilters ? searchOutline : peopleOutline" />
          <p>{{ emptyMessage }}</p>
          <p v-if="hasFilters" class="state-hint">Try a different term, or show every account.</p>
          <ion-button v-if="hasFilters" fill="outline" size="small" @click="clearFilters">
            Clear filters
          </ion-button>
        </div>

        <!-- ── Lista ────────────────────────────────────────────────── -->
        <template v-else>
          <p class="result-count" aria-live="polite">
            {{ totalItems }} {{ totalItems === 1 ? 'participant' : 'participants' }}
            <span v-if="hasFilters">matching the current filters</span>
          </p>

          <ul class="user-list">
            <li
                v-for="user in items"
                :key="user.userId"
                class="glass-card user-row"
                :class="[`user-row--${user.status.toLowerCase()}`, { 'user-row--busy': actionInProgress === user.userId }]"
            >
              <div class="user-identity">
                <p class="user-name">{{ fullName(user) }}</p>
                <p class="user-email">{{ user.email }}</p>
              </div>

              <!--
                Lo stato non è affidato al solo colore: icona + parola restano
                leggibili in bianco e nero e per chi non distingue peach da verde.
              -->
              <span class="status-chip" :class="`status-chip--${user.status.toLowerCase()}`">
                <ion-icon :icon="statusIcon(user.status)" aria-hidden="true" />
                {{ statusLabel(user.status) }}
              </span>

              <dl class="user-meta">
                <div>
                  <dt>Activities</dt>
                  <dd>{{ user.activityCount }}</dd>
                </div>
                <div>
                  <dt>Last activity</dt>
                  <dd>{{ formatDay(user.lastActivityDay) }}</dd>
                </div>
                <div>
                  <dt>Registered</dt>
                  <dd>{{ formatDateTime(user.registeredAt) }}</dd>
                </div>
                <div>
                  <dt>Last sign-in</dt>
                  <dd>{{ formatDateTime(user.lastLogin) }}</dd>
                </div>
              </dl>

              <!--
                Le azioni dipendono dallo stato, come le regole del servizio:
                approve/reject solo da PENDING, unblock solo da BLOCKED. Gli
                account amministratore (compreso il proprio e quello di sistema)
                non compaiono affatto in questa lista: il backend li esclude
                dalla query, quindi ogni riga qui è azionabile.
              -->
              <div class="user-actions">
                <ion-button
                    v-for="action in actionsFor(user.status)"
                    :key="action"
                    size="small"
                    :fill="actionFill(action)"
                    :color="actionColor(action)"
                    :disabled="actionInProgress !== null"
                    :aria-label="`${actionLabel(action)} ${user.email}`"
                    @click="openDialog(user, action)"
                >
                  <ion-icon slot="start" :icon="actionIcon(action)" aria-hidden="true" />
                  {{ actionLabel(action) }}
                </ion-button>
                <ion-spinner
                    v-if="actionInProgress === user.userId"
                    name="crescent"
                    class="row-spinner"
                    aria-label="Applying the change"
                />
              </div>
            </li>
          </ul>

          <!-- ── Paginazione ────────────────────────────────────────── -->
          <nav v-if="totalPages > 1" class="pagination" aria-label="Pagination">
            <ion-button
                fill="outline"
                size="small"
                :disabled="page === 0 || loading"
                @click="previousPage"
            >
              <ion-icon slot="start" :icon="chevronBackOutline" aria-hidden="true" />
              Previous
            </ion-button>
            <span class="page-indicator" aria-live="polite">
              Page {{ page + 1 }} of {{ totalPages }}
            </span>
            <ion-button
                fill="outline"
                size="small"
                :disabled="page >= totalPages - 1 || loading"
                @click="nextPage"
            >
              Next
              <ion-icon slot="end" :icon="chevronForwardOutline" aria-hidden="true" />
            </ion-button>
          </nav>
        </template>
      </div>

      <!--
        Modale dichiarativa (`:is-open`) come nel resto dell'app: la variante
        imperativa perde il primo tap perché il custom element non è ancora
        idratato (vedi la nota in RegistrationPage).
      -->
      <ion-modal
          class="confirm-modal"
          :is-open="dialog !== null"
          @didDismiss="closeDialog"
          @didPresent="focusMessage"
      >
        <div
            v-if="dialog"
            class="confirm-dialog"
            role="dialog"
            aria-modal="true"
            aria-labelledby="confirm-title"
        >
          <h2 id="confirm-title" class="confirm-title">{{ dialogTitle }}</h2>
          <p class="confirm-target">{{ dialog.user.email }}</p>

          <div v-if="dialog.action === 'delete'" class="danger-notice" role="alert">
            <ion-icon :icon="warningOutline" aria-hidden="true" />
            <p>
              <template v-if="dialog.user.activityCount > 0">
                This deletes the account <strong>and all
                {{ dialog.user.activityCount }}
                {{ dialog.user.activityCount === 1 ? 'activity' : 'activities' }}</strong>
                recorded by this person.
              </template>
              <template v-else>
                This deletes the account and everything attached to it. This person has not
                recorded any activity yet.
              </template>
              <strong>The operation cannot be undone.</strong>
            </p>
          </div>
          <template v-else>
            <p class="confirm-explanation">{{ dialogExplanation }}</p>
            <!-- Reversibilità dichiarata: la differenza fra Reject e Delete è
                 tutta qui, e va detta prima di premere, non dopo. -->
            <p v-if="dialogReversible" class="confirm-reversible">
              <ion-icon :icon="refreshOutline" aria-hidden="true" />
              {{ dialogReversible }}
            </p>
          </template>

          <div class="message-field">
            <label class="message-label" for="admin-message">
              Message to the user
              <span v-if="messageRequired" class="required">(required)</span>
              <span v-else class="optional">(optional)</span>
            </label>
            <ion-textarea
                id="admin-message"
                ref="messageField"
                v-model="dialogMessage"
                class="message-input"
                :rows="4"
                :maxlength="MESSAGE_MAX_LENGTH"
                :counter="true"
                auto-grow
                :placeholder="messagePlaceholder"
                :aria-describedby="'email-preview'"
                :aria-required="messageRequired"
            />
          </div>

          <!-- L'amministratore vede il testo esatto che partirà via email. -->
          <section id="email-preview" class="email-preview" aria-label="Email preview">
            <p class="preview-heading">
              <ion-icon :icon="mailOutline" aria-hidden="true" />
              The user will receive this email
            </p>
            <p class="preview-subject"><strong>Subject:</strong> {{ emailPreview.subject }}</p>
            <p v-for="(paragraph, index) in emailPreview.body" :key="index" class="preview-line">
              {{ paragraph }}
            </p>
            <blockquote v-if="trimmedMessage" class="preview-message">
              <span class="preview-message-label">Message from the administrator</span>
              {{ trimmedMessage }}
            </blockquote>
          </section>

          <p v-if="dialogError" class="field-error" role="alert">{{ dialogError }}</p>

          <div class="confirm-actions">
            <ion-button fill="outline" :disabled="submitting" @click="closeDialog">Cancel</ion-button>
            <ion-button
                :color="confirmColor"
                :disabled="submitting || (messageRequired && !trimmedMessage)"
                @click="confirm"
            >
              {{ submitting ? 'Working…' : confirmLabel }}
            </ion-button>
          </div>
        </div>
      </ion-modal>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton,
  IonIcon, IonSpinner, IonSearchbar, IonSegment, IonSegmentButton, IonLabel,
  IonModal, IonTextarea, IonSkeletonText
} from '@ionic/vue';
import {
  alertCircleOutline, arrowBackOutline, banOutline, checkmarkCircleOutline,
  chevronBackOutline, chevronForwardOutline, closeCircleOutline, hourglassOutline,
  lockClosedOutline, lockOpenOutline, mailOutline, peopleOutline, refreshOutline,
  searchOutline, trashOutline, warningOutline
} from 'ionicons/icons';
import { useToast } from '@/composables/useToast';
import {
  useAdminUsers,
  type AccountStatus,
  type AdminUser,
  type AdminUserAction,
  type StatusFilter
} from '@/composables/useAdminUsers';

/** Limite di `AdminUserActionRequest`/`DeleteUserRequest` lato backend. */
const MESSAGE_MAX_LENGTH = 1000;
/** I messaggi d'esito sono lunghi: 2,5s non bastano per leggerli. */
const TOAST_DURATION = 4000;

const router = useRouter();
const { showToast } = useToast();

const {
  items, counts, page, totalPages, totalItems, status, searchInput,
  searchTerm, loading, error, actionInProgress,
  load, setStatus, applySearchNow, nextPage, previousPage, runAction
} = useAdminUsers();

/* ---------- dialog di conferma ---------- */

interface PendingDecision {
  user: AdminUser;
  action: AdminUserAction;
}

const dialog = ref<PendingDecision | null>(null);
const dialogMessage = ref('');
const dialogError = ref<string | null>(null);
const submitting = ref(false);
const messageField = ref<{ $el?: HTMLElement } | null>(null);

const trimmedMessage = computed(() => dialogMessage.value.trim());
const messageRequired = computed(() => dialog.value?.action === 'delete');

const statusOptions = computed<Array<{ value: StatusFilter; label: string; count: number }>>(() => [
  {
    value: 'ALL',
    label: 'All',
    count: counts.value.pending + counts.value.active + counts.value.blocked
  },
  { value: 'PENDING', label: 'Pending', count: counts.value.pending },
  { value: 'ACTIVE', label: 'Active', count: counts.value.active },
  { value: 'BLOCKED', label: 'Blocked', count: counts.value.blocked }
]);

const hasFilters = computed(() => status.value !== 'ALL' || searchTerm.value.length > 0);

const emptyMessage = computed(() => {
  if (searchTerm.value) {
    return `No participant matches “${searchTerm.value}”.`;
  }
  if (status.value !== 'ALL') {
    return `No ${statusLabel(status.value as AccountStatus).toLowerCase()} account right now.`;
  }
  return 'No participant has registered yet.';
});

/* ---------- etichette e presentazione ---------- */

const fullName = (user: AdminUser) => {
  const name = [user.name, user.surname].filter(Boolean).join(' ').trim();
  // Il profilo può essere incompleto: senza fallback la riga resterebbe muta.
  return name || 'Name not provided';
};

const statusLabel = (value: AccountStatus): string => ({
  PENDING: 'Pending',
  ACTIVE: 'Active',
  BLOCKED: 'Blocked'
}[value]);

/** Seconda codifica dello stato accanto alla parola: mai il solo colore. */
const statusIcon = (value: AccountStatus): string => ({
  PENDING: hourglassOutline,
  ACTIVE: checkmarkCircleOutline,
  BLOCKED: lockClosedOutline
}[value]);

/**
 * Le date arrivano come ISO locali senza offset. Vengono ricomposte a mano
 * invece di passare da `Date`: `new Date('2026-08-05')` è interpretata in UTC e
 * a ovest di Greenwich mostrerebbe il giorno precedente.
 */
function formatDay(value: string | null): string {
  if (!value) {
    return '—';
  }
  const [year, month, day] = value.slice(0, 10).split('-');
  return day && month && year ? `${day}/${month}/${year}` : value;
}

function formatDateTime(value: string | null): string {
  if (!value) {
    return '—';
  }
  const day = formatDay(value);
  const time = value.slice(11, 16);
  return time ? `${day}, ${time}` : day;
}

/**
 * Su una richiesta in attesa il rifiuto sta accanto all'approvazione: le due
 * decisioni sono simmetriche e devono costare lo stesso numero di gesti.
 * `Delete` resta l'ultima e la più leggera visivamente, per non invitarla.
 */
const actionsFor = (state: AccountStatus): AdminUserAction[] => ({
  PENDING: ['approve', 'reject', 'delete'] as AdminUserAction[],
  ACTIVE: ['block', 'delete'] as AdminUserAction[],
  BLOCKED: ['unblock', 'delete'] as AdminUserAction[]
}[state]);

const actionLabel = (action: AdminUserAction): string => ({
  approve: 'Approve',
  reject: 'Reject',
  block: 'Block',
  unblock: 'Unblock',
  delete: 'Delete'
}[action]);

const actionColor = (action: AdminUserAction): string => ({
  approve: 'success',
  reject: 'warning',
  block: 'warning',
  unblock: 'success',
  delete: 'danger'
}[action]);

/** Solo `delete` è senza bordo: pesa meno di ogni altra azione della riga. */
const actionFill = (action: AdminUserAction): 'clear' | 'outline' =>
    (action === 'delete' ? 'clear' : 'outline');

const actionIcon = (action: AdminUserAction): string => ({
  approve: checkmarkCircleOutline,
  reject: closeCircleOutline,
  block: banOutline,
  unblock: lockOpenOutline,
  delete: trashOutline
}[action]);

const dialogTitle = computed(() => {
  if (!dialog.value) {
    return '';
  }
  return {
    approve: 'Approve this registration?',
    reject: 'Reject this registration?',
    block: 'Block this account?',
    unblock: 'Unblock this account?',
    delete: 'Delete this account for good?'
  }[dialog.value.action];
});

const dialogExplanation = computed(() => {
  if (!dialog.value) {
    return '';
  }
  return {
    approve: 'The account will be able to sign in immediately.',
    reject: 'The request is turned down: the person is notified by email and will not be able '
        + 'to sign in. Nothing is deleted — the account stays in the list as blocked.',
    block: 'The person will no longer be able to sign in. Their data is kept and the block can be lifted later.',
    unblock: 'The person will be able to sign in again.',
    delete: ''
  }[dialog.value.action];
});

/** Rassicurazione esplicita sulle azioni che si possono disfare. */
const dialogReversible = computed(() => {
  if (!dialog.value) {
    return '';
  }
  return {
    approve: '',
    reject: 'You can change your mind later: unblock the account from the Blocked filter.',
    block: '',
    unblock: '',
    delete: ''
  }[dialog.value.action];
});

const confirmColor = computed(() => {
  if (!dialog.value) {
    return 'primary';
  }
  return { approve: 'primary', reject: 'warning', block: 'warning', unblock: 'primary', delete: 'danger' }[
      dialog.value.action
  ];
});

const confirmLabel = computed(() =>
    dialog.value ? actionLabel(dialog.value.action) : ''
);

const messagePlaceholder = computed(() => {
  if (messageRequired.value) {
    return 'Explain why the account and its data are being removed';
  }
  if (dialog.value?.action === 'reject') {
    return 'Say why the request was turned down — this is the only explanation the person gets';
  }
  return 'Add a note to the standard notice, or leave empty';
});

/**
 * Anteprima fedele di ciò che parte via email. I testi rispecchiano
 * `EmailService.sendAccountApprovedEmail/Blocked/Deleted`: vanno tenuti
 * allineati se le mail cambiano lato backend.
 *
 * `reject` passa da `AdminUserService.block`, quindi riceve esattamente la
 * mail di blocco: l'anteprima è la stessa, e per questo il messaggio libero
 * è l'unico posto dove spiegare che si trattava di un rifiuto.
 */
const emailPreview = computed<{ subject: string; body: string[] }>(() => {
  if (!dialog.value) {
    return { subject: '', body: [] };
  }
  return {
    approve: {
      subject: 'Your account has been approved - Chronogram',
      body: [
        'Your Chronogram account has been approved. You can now sign in with the email '
        + 'and password you chose when you registered.'
      ]
    },
    unblock: {
      subject: 'Your account has been approved - Chronogram',
      body: [
        'Your Chronogram account has been approved. You can now sign in with the email '
        + 'and password you chose when you registered.'
      ]
    },
    reject: {
      subject: 'Your account has been blocked - Chronogram',
      body: [
        'Your Chronogram account has been blocked by an administrator and you can no '
        + 'longer sign in. Your data has not been deleted.',
        'If you believe this is a mistake, please reply to the administrator who contacted you.'
      ]
    },
    block: {
      subject: 'Your account has been blocked - Chronogram',
      body: [
        'Your Chronogram account has been blocked by an administrator and you can no '
        + 'longer sign in. Your data has not been deleted.',
        'If you believe this is a mistake, please reply to the administrator who contacted you.'
      ]
    },
    delete: {
      subject: 'Your account has been deleted - Chronogram',
      body: [
        'Your Chronogram account and all the activities you recorded have been '
        + 'permanently deleted by an administrator. This action cannot be undone.'
      ]
    }
  }[dialog.value.action];
});

/* ---------- interazioni ---------- */

const goToDashboard = () => router.push({ name: 'AdminDashboard' });

function onStatusChange(event: CustomEvent<{ value?: string | number | undefined }>): void {
  const value = event.detail?.value;
  if (typeof value === 'string') {
    void setStatus(value as StatusFilter);
  }
}

async function clearFilters(): Promise<void> {
  searchInput.value = '';
  await applySearchNow();
  await setStatus('ALL');
}

function openDialog(user: AdminUser, action: AdminUserAction): void {
  dialog.value = { user, action };
  dialogMessage.value = '';
  dialogError.value = null;
}

function closeDialog(): void {
  if (submitting.value) {
    return;
  }
  dialog.value = null;
  dialogMessage.value = '';
  dialogError.value = null;
}

/** Il campo del messaggio è il primo elemento utile: ci si porta il focus. */
async function focusMessage(): Promise<void> {
  const element = messageField.value?.$el as (HTMLElement & { setFocus?: () => Promise<void> }) | undefined;
  await element?.setFocus?.();
}

async function confirm(): Promise<void> {
  if (!dialog.value) {
    return;
  }

  submitting.value = true;
  dialogError.value = null;
  const outcome = await runAction(dialog.value.user, dialog.value.action, dialogMessage.value);
  submitting.value = false;

  if (!outcome.ok) {
    // La dialog resta aperta: l'amministratore può correggere e riprovare senza
    // ricostruire il messaggio appena scritto.
    dialogError.value = outcome.message;
    await showToast(outcome.message, 'danger', TOAST_DURATION);
    return;
  }

  dialog.value = null;
  dialogMessage.value = '';
  await showToast(outcome.message, 'success', TOAST_DURATION);
}

onMounted(load);
</script>

<style scoped>
/*
  La console si consulta soprattutto da desktop: il contenitore arriva a 1280px
  e la lista diventa multi-colonna, così su uno schermo largo si vede l'intera
  coda senza scorrere (meno carico di memoria di lavoro fra una riga e l'altra).
  Sotto i 720px resta la card singola pensata per la WebView Android.
*/
.admin-container {
  max-width: 1200px;
  margin-inline: auto;
  padding-bottom: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
}

.section-title {
  font-size: var(--font-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text);
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.panel {
  padding: var(--space-4);
}

.panel-note {
  font-size: var(--font-sm);
  color: var(--subtext0);
  margin: var(--space-1) 0 0;
}

.back-icon {
  font-size: 1.4rem;
  color: var(--peach);
}

/* ── Coda dei pending ─────────────────────────────────────── */

.pending-banner {
  padding: var(--space-4) var(--space-4) var(--space-4) var(--space-5);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  border-color: var(--peach);
  /* Barra d'accento: dà peso alla coda senza gridare con un fondo pieno. */
  border-left: 4px solid var(--peach);
  background-color: color-mix(in srgb, var(--surface0), var(--peach) 6%);
}

.pending-banner .section-title {
  font-size: var(--font-xl);
  line-height: 1.25;
}

.pending-banner .section-title ion-icon {
  color: var(--peach);
  flex-shrink: 0;
}

.pending-cta {
  --background: var(--peach);
  --color: var(--crust);
  min-height: var(--touch-target);
}

.pending-current {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-size: var(--font-sm);
  color: var(--subtext0);
}

.pending-current ion-icon {
  color: var(--green);
}

/* ── Filtri ───────────────────────────────────────────────── */

.filters {
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.searchbar {
  --background: var(--surface1);
  --color: var(--text);
  --placeholder-color: var(--overlay1);
  --icon-color: var(--peach);
  --border-radius: var(--radius-md);
  padding: 0;
}

.status-segment {
  --background: var(--surface1);
}

/* Il conteggio è una pastiglia, non un numero appiccicato all'etichetta:
   si legge come dato a colpo d'occhio anche a segmento non selezionato. */
.segment-count {
  display: inline-block;
  margin-left: var(--space-2);
  min-width: 1.5em;
  padding: 0 var(--space-1);
  border-radius: var(--radius-pill);
  background: var(--surface2);
  font-size: var(--font-xs);
  font-weight: var(--font-weight-semibold);
  line-height: 1.5;
  color: var(--text);
}

.segment-count--alert {
  background: var(--peach);
  color: var(--crust);
}

/* ── Lista ────────────────────────────────────────────────── */

.result-count {
  font-size: var(--font-sm);
  color: var(--subtext0);
  margin: 0;
}

/*
  `auto-fill` + `minmax` invece di un media query per colonna: la lista si
  riempie da sola in 1, 2 o 3 colonne a seconda dello spazio reale, e il
  `min(100%, …)` evita il traboccamento sui telefoni stretti.
*/
.user-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(min(100%, 23rem), 1fr));
  gap: var(--space-3);
  align-items: start;
}

.user-row {
  padding: var(--space-4);
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-3);
  align-items: start;
  align-content: start;
  height: 100%;
  border-left: 3px solid transparent;
  transition: border-color 120ms ease, opacity 120ms ease;
}

/* La riga in attesa porta lo stesso accento del banner: ovunque cada nella
   griglia si riconosce come "richiede una decisione". */
.user-row--pending {
  border-left-color: var(--peach);
  background-color: color-mix(in srgb, var(--surface0), var(--peach) 4%);
}

.user-row--blocked {
  border-left-color: var(--red);
}

/* Riga in lavorazione: resta leggibile ma dichiara di essere occupata. */
.user-row--busy {
  opacity: 0.6;
}

.user-identity {
  min-width: 0;
}

.user-name {
  margin: 0;
  font-size: var(--font-md);
  font-weight: var(--font-weight-semibold);
  color: var(--text);
}

.user-email {
  margin: var(--space-1) 0 0;
  font-size: var(--font-sm);
  color: var(--subtext0);
  word-break: break-all;
}

.status-chip {
  justify-self: end;
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-xs);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid currentColor;
  white-space: nowrap;
}

.status-chip ion-icon {
  font-size: 0.95rem;
}

.status-chip--pending {
  color: var(--peach);
}

.status-chip--active {
  color: var(--green);
}

.status-chip--blocked {
  color: var(--red);
}

.user-meta {
  grid-column: 1 / -1;
  margin: 0;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--space-2) var(--space-3);
  padding-top: var(--space-2);
  border-top: 1px solid var(--glass-border);
}

.user-meta dt {
  font-size: var(--font-xs);
  /* `overlay1` non arriva a 4.5:1 sul fondo delle card: le etichette dei dati
     salgono a `subtext0`, che lo supera in entrambi i temi. */
  color: var(--subtext0);
  margin: 0;
}

.user-meta dd {
  font-size: var(--font-base);
  color: var(--text);
  font-variant-numeric: tabular-nums;
  margin: 0;
}

.user-actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-2);
  justify-content: flex-end;
  /* Le azioni restano in fondo alla card anche quando le righe della griglia
     hanno altezze diverse: la stessa azione cade sempre alla stessa altezza. */
  margin-top: auto;
}

.user-actions ion-button {
  --padding-start: var(--space-3);
  --padding-end: var(--space-3);
  min-height: var(--touch-target);
  margin: 0;
}

.row-spinner {
  width: 1.1rem;
  height: 1.1rem;
  color: var(--peach);
}

/* ── Scheletro di caricamento ─────────────────────────────── */

.user-row--skeleton {
  gap: var(--space-3);
  pointer-events: none;
}

.skeleton-chip {
  justify-self: end;
  width: 5.5rem;
  height: 1.5rem;
  border-radius: var(--radius-pill);
}

.skeleton-meta {
  grid-column: 1 / -1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(120px, 1fr));
  gap: var(--space-2) var(--space-3);
}

.state-block--error ion-icon {
  color: var(--red);
}

.state-hint {
  margin: calc(var(--space-2) * -1) 0 0;
  font-size: var(--font-sm);
  color: var(--subtext0);
}

/* ── Paginazione ──────────────────────────────────────────── */

.pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.page-indicator {
  font-size: var(--font-sm);
  color: var(--subtext0);
}

/* ── Dialog di conferma ───────────────────────────────────── */

.confirm-modal {
  --width: min(560px, 92vw);
  --height: fit-content;
  --max-height: 90vh;
  --border-radius: var(--radius-lg);
  --box-shadow: 0 24px 48px rgba(0, 0, 0, 0.45);
}

.confirm-dialog {
  padding: var(--space-5);
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
  overflow-y: auto;
  background: var(--base, var(--ion-background-color));
}

.confirm-title {
  margin: 0;
  font-size: var(--font-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text);
}

.confirm-target {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--subtext0);
  word-break: break-all;
}

.confirm-explanation {
  margin: 0;
  font-size: var(--font-base);
  color: var(--subtext1);
  line-height: 1.5;
}

.confirm-reversible {
  margin: 0;
  display: flex;
  align-items: flex-start;
  gap: var(--space-2);
  font-size: var(--font-sm);
  color: var(--subtext0);
  line-height: 1.5;
}

.confirm-reversible ion-icon {
  color: var(--green);
  font-size: 1.1rem;
  flex-shrink: 0;
  margin-top: 0.15em;
}

.danger-notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  border-radius: var(--radius-md);
  background: var(--surface0);
  border: 1px solid var(--red);
}

.danger-notice ion-icon {
  color: var(--red);
  font-size: 1.4rem;
  flex-shrink: 0;
}

.danger-notice p {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--subtext1);
  line-height: 1.5;
}

.message-field {
  display: flex;
  flex-direction: column;
  gap: var(--space-1);
}

.message-label {
  font-size: var(--font-sm);
  color: var(--subtext1);
}

.message-label .required {
  color: var(--red);
}

.message-label .optional {
  color: var(--overlay1);
}

.message-input {
  --background: var(--surface0);
  --color: var(--text);
  --padding-start: var(--space-3);
  --padding-end: var(--space-3);
  border-radius: var(--radius-md);
  border: 1px solid var(--glass-border);
}

.email-preview {
  border-radius: var(--radius-md);
  background: var(--surface0);
  padding: var(--space-3) var(--space-4);
  font-size: var(--font-sm);
  color: var(--subtext0);
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.preview-heading {
  margin: 0;
  display: flex;
  align-items: center;
  gap: var(--space-2);
  font-weight: var(--font-weight-semibold);
  color: var(--subtext1);
}

.preview-heading ion-icon {
  color: var(--peach);
}

.preview-subject,
.preview-line {
  margin: 0;
  line-height: 1.5;
}

.preview-message {
  margin: 0;
  padding-left: var(--space-3);
  border-left: 2px solid var(--peach);
  color: var(--text);
  white-space: pre-wrap;
}

.preview-message-label {
  display: block;
  font-size: var(--font-xs);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--overlay1);
}

.field-error {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--red);
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.confirm-actions ion-button {
  min-height: var(--touch-target);
}

.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip: rect(0, 0, 0, 0);
  white-space: nowrap;
  border: 0;
}
</style>
