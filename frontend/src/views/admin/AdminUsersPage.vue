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
             alla pagina e resta visibile finché la coda non è vuota. -->
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
              fill="outline"
              size="small"
              @click="setStatus('PENDING')"
          >
            Review them
          </ion-button>
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
            <ion-segment-button v-for="option in statusOptions" :key="option.value" :value="option.value">
              <ion-label>
                {{ option.label }}
                <span class="segment-count">{{ option.count }}</span>
              </ion-label>
            </ion-segment-button>
          </ion-segment>
        </section>

        <!-- ── Stati della lista ────────────────────────────────────── -->
        <div v-if="loading" class="state-block" role="status" aria-live="polite">
          <ion-spinner name="crescent" />
          <p>Loading participants…</p>
        </div>

        <div v-else-if="error" class="state-block" role="alert">
          <ion-icon :icon="alertCircleOutline" />
          <p>{{ error }}</p>
          <ion-button fill="outline" size="small" @click="load">Retry</ion-button>
        </div>

        <div v-else-if="items.length === 0" class="state-block" role="status" aria-live="polite">
          <ion-icon :icon="peopleOutline" />
          <p>{{ emptyMessage }}</p>
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
            <li v-for="user in items" :key="user.userId" class="glass-card user-row">
              <div class="user-identity">
                <p class="user-name">{{ fullName(user) }}</p>
                <p class="user-email">{{ user.email }}</p>
              </div>

              <span class="status-chip" :class="`status-chip--${user.status.toLowerCase()}`">
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
                approve solo da PENDING, unblock solo da BLOCKED. Gli account
                amministratore (compreso il proprio e quello di sistema) non
                compaiono affatto in questa lista: il backend li esclude dalla
                query, quindi ogni riga qui è azionabile.
              -->
              <div class="user-actions">
                <ion-button
                    v-for="action in actionsFor(user.status)"
                    :key="action"
                    size="small"
                    :fill="action === 'delete' ? 'clear' : 'outline'"
                    :color="actionColor(action)"
                    :disabled="actionInProgress !== null"
                    :aria-label="`${actionLabel(action)} ${user.email}`"
                    @click="openDialog(user, action)"
                >
                  <ion-icon slot="start" :icon="actionIcon(action)" aria-hidden="true" />
                  {{ actionLabel(action) }}
                </ion-button>
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
          <p v-else class="confirm-explanation">{{ dialogExplanation }}</p>

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
                :color="dialog.action === 'delete' ? 'danger' : 'primary'"
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
  IonModal, IonTextarea
} from '@ionic/vue';
import {
  alertCircleOutline, arrowBackOutline, banOutline, checkmarkCircleOutline,
  chevronBackOutline, chevronForwardOutline, hourglassOutline, lockOpenOutline,
  mailOutline, peopleOutline, trashOutline, warningOutline
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

const actionsFor = (state: AccountStatus): AdminUserAction[] => ({
  PENDING: ['approve', 'delete'] as AdminUserAction[],
  ACTIVE: ['block', 'delete'] as AdminUserAction[],
  BLOCKED: ['unblock', 'delete'] as AdminUserAction[]
}[state]);

const actionLabel = (action: AdminUserAction): string => ({
  approve: 'Approve',
  block: 'Block',
  unblock: 'Unblock',
  delete: 'Delete'
}[action]);

const actionColor = (action: AdminUserAction): string => ({
  approve: 'success',
  block: 'warning',
  unblock: 'success',
  delete: 'danger'
}[action]);

const actionIcon = (action: AdminUserAction): string => ({
  approve: checkmarkCircleOutline,
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
    block: 'The person will no longer be able to sign in. Their data is kept and the block can be lifted later.',
    unblock: 'The person will be able to sign in again.',
    delete: ''
  }[dialog.value.action];
});

const confirmLabel = computed(() =>
    dialog.value ? actionLabel(dialog.value.action) : ''
);

const messagePlaceholder = computed(() =>
    messageRequired.value
        ? 'Explain why the account and its data are being removed'
        : 'Add a note to the standard notice, or leave empty'
);

/**
 * Anteprima fedele di ciò che parte via email. I testi rispecchiano
 * `EmailService.sendAccountApprovedEmail/Blocked/Deleted`: vanno tenuti
 * allineati se le mail cambiano lato backend.
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
.admin-container {
  max-width: 1000px;
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
  padding: var(--space-4);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
  border-color: var(--peach);
}

.pending-banner .section-title ion-icon {
  color: var(--peach);
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

.segment-count {
  display: inline-block;
  margin-left: var(--space-1);
  font-size: var(--font-xs);
  color: var(--subtext0);
}

/* ── Lista ────────────────────────────────────────────────── */

.result-count {
  font-size: var(--font-sm);
  color: var(--subtext0);
  margin: 0;
}

.user-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: var(--space-3);
}

.user-row {
  padding: var(--space-4);
  display: grid;
  grid-template-columns: 1fr auto;
  gap: var(--space-3);
  align-items: start;
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
  font-size: var(--font-xs);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  padding: var(--space-1) var(--space-3);
  border-radius: var(--radius-pill);
  border: 1px solid currentColor;
  white-space: nowrap;
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
  grid-template-columns: repeat(auto-fit, minmax(130px, 1fr));
  gap: var(--space-2) var(--space-3);
}

.user-meta dt {
  font-size: var(--font-xs);
  color: var(--overlay1);
  margin: 0;
}

.user-meta dd {
  font-size: var(--font-sm);
  color: var(--subtext1);
  margin: 0;
}

.user-actions {
  grid-column: 1 / -1;
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-2);
  justify-content: flex-end;
}

.user-actions ion-button {
  --padding-start: var(--space-3);
  --padding-end: var(--space-3);
  min-height: var(--touch-target);
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
