<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-back-button default-href="/settings" />
        </ion-buttons>
        <ion-title>Notifications</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding content-safe-area">
      <div class="page-form-wrapper">
        <!-- Caricamento in pagina e non come overlay: un loading presentato
             sopra ai controlli ne bloccherebbe lo scorrimento (stessa scelta
             di EditProfile). -->
        <div v-if="loading" class="state-block" role="status" aria-live="polite">
          <ion-spinner name="crescent" />
          <p>Loading your notification settings…</p>
        </div>

        <!-- Errore di caricamento: niente da configurare, ma si dice cosa è
             andato storto e si offre il riprova. -->
        <div v-else-if="loadError" class="state-block state-block--error" role="alert">
          <ion-icon :icon="alertCircleOutline" aria-hidden="true" />
          <p>{{ loadError }}</p>
          <ion-button fill="outline" size="small" @click="loadSettings">
            <ion-icon slot="start" :icon="refreshOutline" aria-hidden="true" />
            Retry
          </ion-button>
        </div>

        <template v-else>
          <p class="page-subtitle">
            Chronogram can remind you to log your activities during the day.
          </p>

          <!-- Banner informativi: uno solo alla volta, dal più bloccante.
               Ognuno dice l'unica cosa che l'utente può fare al riguardo. -->
          <div v-if="blockingNotice" class="notice" :class="`notice--${blockingNotice.tone}`" role="note">
            <ion-icon :icon="blockingNotice.icon" aria-hidden="true" />
            <div>
              <p class="notice-title">{{ blockingNotice.title }}</p>
              <p class="notice-body">{{ blockingNotice.body }}</p>
            </div>
          </div>

          <ion-list lines="none" class="glass-card settings-group">
            <ion-item>
              <ion-icon slot="start" :icon="notificationsOutline" class="input-icon" aria-hidden="true" />
              <ion-label>
                <h2>Push notifications</h2>
                <p>{{ deliveryHint }}</p>
              </ion-label>
              <!-- `:checked` + `@ionChange` invece di `v-model`: attivare è
                   un'operazione asincrona che può essere rifiutata (permesso
                   negato, server senza chiavi), e con `v-model` il toggle
                   resterebbe acceso su un'attivazione mai avvenuta. -->
              <ion-toggle
                  slot="end"
                  :checked="pushEnabled"
                  :disabled="toggleDisabled"
                  aria-label="Enable periodic reminders"
                  @ionChange="onToggle"
              />
            </ion-item>
          </ion-list>

          <ion-list v-if="pushEnabled" lines="none" class="glass-card settings-group">
            <ion-item>
              <ion-icon slot="start" :icon="timeOutline" class="input-icon" aria-hidden="true" />
              <ion-select
                  label="Remind me"
                  label-placement="stacked"
                  interface="popover"
                  :value="intervalMinutes"
                  :disabled="busy"
                  :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
                  @ionChange="onIntervalChange"
              >
                <ion-select-option
                    v-for="option in INTERVAL_OPTIONS"
                    :key="option.minutes"
                    :value="option.minutes"
                >
                  {{ option.label }}
                </ion-select-option>
              </ion-select>
            </ion-item>

            <ion-item>
              <ion-icon slot="start" :icon="moonOutline" class="input-icon" aria-hidden="true" />
              <ion-select
                  label="Quiet hours from"
                  label-placement="stacked"
                  interface="popover"
                  :value="quietStartValue"
                  :disabled="busy"
                  :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
                  @ionChange="onQuietStartChange"
              >
                <ion-select-option :value="NO_QUIET">No quiet hours</ion-select-option>
                <ion-select-option v-for="hour in HOURS" :key="`from-${hour}`" :value="hour">
                  {{ formatHour(hour) }}
                </ion-select-option>
              </ion-select>
            </ion-item>

            <ion-item v-if="quietStartValue !== NO_QUIET">
              <ion-icon slot="start" :icon="sunnyOutline" class="input-icon" aria-hidden="true" />
              <ion-select
                  label="Quiet hours until"
                  label-placement="stacked"
                  interface="popover"
                  :value="quietEndValue"
                  :disabled="busy"
                  :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
                  @ionChange="onQuietEndChange"
              >
                <ion-select-option v-for="hour in HOURS" :key="`until-${hour}`" :value="hour">
                  {{ formatHour(hour) }}
                </ion-select-option>
              </ion-select>
            </ion-item>
          </ion-list>

          <!-- Il riepilogo in parole è l'unico modo di verificare a colpo
               d'occhio una fascia a cavallo di mezzanotte. -->
          <p v-if="pushEnabled" class="summary-note">{{ scheduleSummary }}</p>

          <div v-if="pushEnabled" class="actions">
            <ion-button
                expand="block"
                fill="outline"
                class="test-btn"
                :disabled="busy"
                @click="onSendTest"
            >
              <ion-icon slot="start" :icon="paperPlaneOutline" aria-hidden="true" />
              Send test notification
            </ion-button>

            <p v-if="subscriptionCount > 0" class="device-count">
              <ion-icon :icon="phonePortraitOutline" aria-hidden="true" />
              {{ subscriptionCount }}
              {{ subscriptionCount === 1 ? 'registered device' : 'registered devices' }}
            </p>
          </div>
        </template>
      </div>
    </ion-content>
  </ion-page>
</template>

<script lang="ts">
// Nome multi-parola per soddisfare la regola ESLint vue/multi-word-component-names
// senza rinominare il file (referenziato dal router).
export default { name: 'NotificationsPage' };
</script>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  IonPage,
  IonHeader,
  IonToolbar,
  IonButtons,
  IonBackButton,
  IonTitle,
  IonContent,
  IonList,
  IonItem,
  IonIcon,
  IonLabel,
  IonToggle,
  IonSelect,
  IonSelectOption,
  IonButton,
  IonSpinner
} from '@ionic/vue';
import {
  alertCircleOutline,
  informationCircleOutline,
  moonOutline,
  notificationsOutline,
  notificationsOffOutline,
  paperPlaneOutline,
  phonePortraitOutline,
  shareOutline,
  refreshOutline,
  sunnyOutline,
  timeOutline
} from 'ionicons/icons';

import { useToast } from '@/composables/useToast';
import {
  INTERVAL_OPTIONS,
  usePushNotifications
} from '@/composables/usePushNotifications';

const { showToast } = useToast();

const {
  loading,
  loadError,
  busy,
  preferences,
  subscriptionCount,
  permission,
  nativePlatform,
  webPushSupported,
  iosNotInstalled,
  configured,
  canUsePush,
  pushEnabled,
  loadSettings,
  setPushEnabled,
  savePreferences,
  sendTest
} = usePushNotifications();

/**
 * Valore sentinella per "nessuna fascia di silenzio".
 *
 * Non si usa `null`: `ion-select` interpreta un valore nullo come "nessuna
 * selezione" e mostrerebbe il placeholder al posto dell'etichetta, facendo
 * sembrare la select vuota e non impostata.
 */
const NO_QUIET = -1;

/** Le 24 ore, per le select della fascia di silenzio. */
const HOURS: number[] = Array.from({ length: 24 }, (_, hour) => hour);

const formatHour = (hour: number): string => `${String(hour).padStart(2, '0')}:00`;

/* Stato locale dei controlli: separato dalle preferenze salvate, così un
   salvataggio rifiutato può riallineare le select alla verità del server. */
const intervalMinutes = ref(preferences.value.intervalMinutes);
const quietStartValue = ref(NO_QUIET);
const quietEndValue = ref(NO_QUIET);

function syncFormFromPreferences(): void {
  intervalMinutes.value = preferences.value.intervalMinutes;
  quietStartValue.value = preferences.value.quietHoursStart ?? NO_QUIET;
  quietEndValue.value = preferences.value.quietHoursEnd ?? NO_QUIET;
}

watch(preferences, syncFormFromPreferences, { immediate: true, deep: true });

/** Il toggle è utilizzabile solo dove i promemoria possono davvero arrivare. */
const toggleDisabled = computed(() =>
    busy.value || (!pushEnabled.value && (!canUsePush.value || permission.value === 'denied')));

/** Dove verranno consegnati i promemoria: web push o notifiche del dispositivo. */
const deliveryHint = computed(() => nativePlatform
    ? 'Reminders are scheduled on this device.'
    : 'Reminders arrive in this browser, even when the tab is closed.');

/**
 * Unico banner mostrato, scelto dal problema più bloccante.
 *
 * Mostrarli tutti insieme sarebbe rumore: i casi si escludono a vicenda nella
 * pratica e solo il primo è azionabile.
 */
const blockingNotice = computed<{ tone: 'info' | 'warning'; icon: string; title: string; body: string } | null>(() => {
  if (!nativePlatform && !webPushSupported) {
    return {
      tone: 'warning',
      icon: notificationsOffOutline,
      title: 'Not supported in this browser',
      body: 'Push notifications need a recent version of Chrome, Edge, Firefox or Safari. '
          + 'You can also install the Android app.'
    };
  }
  if (iosNotInstalled) {
    return {
      tone: 'info',
      icon: shareOutline,
      title: 'Add Chronogram to your Home Screen',
      body: 'On iPhone and iPad, notifications only work once the app is installed: '
          + 'tap Share, then "Add to Home Screen", and open Chronogram from there.'
    };
  }
  if (permission.value === 'denied') {
    return {
      tone: 'warning',
      icon: notificationsOffOutline,
      title: 'Notifications are blocked',
      body: nativePlatform
          ? 'Allow notifications for Chronogram in the Android system settings, then come back here.'
          : 'Allow notifications for this site from the padlock icon in the address bar, then reload.'
    };
  }
  if (!nativePlatform && !configured.value) {
    return {
      tone: 'info',
      icon: informationCircleOutline,
      title: 'Not available yet',
      body: 'Push notifications are not configured on the server. Please try again later.'
    };
  }
  return null;
});

/** Riepilogo in parole della configurazione corrente. */
const scheduleSummary = computed(() => {
  const label = INTERVAL_OPTIONS.find((option) => option.minutes === intervalMinutes.value)?.label
      ?? `Every ${intervalMinutes.value} minutes`;
  const cadence = label.toLowerCase();
  if (quietStartValue.value === NO_QUIET || quietEndValue.value === NO_QUIET) {
    return `Reminders ${cadence}, at any time of day.`;
  }
  return `Reminders ${cadence}, except between `
      + `${formatHour(quietStartValue.value)} and ${formatHour(quietEndValue.value)}.`;
});

/**
 * Salva e riallinea sempre i controlli.
 *
 * Il riallineamento avviene anche dopo un errore: le select tornano al valore
 * effettivamente salvato invece di mostrare una scelta che il server non ha
 * accettato. Serve anche nel caso riuscito, perché il backend può completare la
 * fascia di silenzio indicata a metà.
 */
async function persist(
    nextInterval: number,
    nextStart: number,
    nextEnd: number
): Promise<void> {
  const quietStart = nextStart === NO_QUIET ? null : nextStart;
  const quietEnd = nextEnd === NO_QUIET ? null : nextEnd;

  const outcome = await savePreferences(nextInterval, quietStart, quietEnd);
  syncFormFromPreferences();
  await showToast(outcome.message, outcome.ok ? 'success' : 'danger');
}

/** Estrae un numero dal `detail` di `ionChange`, che è tipizzato larghissimo. */
function numberFromEvent(event: CustomEvent<{ value?: unknown }>): number | null {
  const value = event.detail?.value;
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  const parsed = typeof value === 'string' ? Number.parseInt(value, 10) : Number.NaN;
  return Number.isFinite(parsed) ? parsed : null;
}

async function onToggle(event: CustomEvent<{ checked?: boolean }>): Promise<void> {
  const wanted = event.detail?.checked === true;
  // Nessun cambio reale (es. il toggle riallineato da noi): niente da salvare.
  if (wanted === pushEnabled.value) {
    return;
  }
  const outcome = await setPushEnabled(wanted);
  syncFormFromPreferences();
  await showToast(outcome.message, outcome.ok ? 'success' : 'danger');
}

async function onIntervalChange(event: CustomEvent<{ value?: unknown }>): Promise<void> {
  const next = numberFromEvent(event);
  if (next === null || next === preferences.value.intervalMinutes) {
    return;
  }
  await persist(next, quietStartValue.value, quietEndValue.value);
}

async function onQuietStartChange(event: CustomEvent<{ value?: unknown }>): Promise<void> {
  const next = numberFromEvent(event);
  if (next === null || next === quietStartValue.value) {
    return;
  }
  // Spegnere il silenzio azzera anche la fine: una fascia con un solo estremo
  // non è valutabile e il backend la scarterebbe comunque.
  const nextEnd = next === NO_QUIET ? NO_QUIET : quietEndValue.value;
  await persist(intervalMinutes.value, next, nextEnd);
}

async function onQuietEndChange(event: CustomEvent<{ value?: unknown }>): Promise<void> {
  const next = numberFromEvent(event);
  if (next === null || next === quietEndValue.value) {
    return;
  }
  await persist(intervalMinutes.value, quietStartValue.value, next);
}

async function onSendTest(): Promise<void> {
  const outcome = await sendTest();
  await showToast(outcome.message, outcome.ok ? 'success' : 'warning');
}

onMounted(loadSettings);
</script>

<style scoped>
/* Spazio in fondo: la pagina non ha bottom nav, ma serve comunque aria sotto
   l'ultimo controllo dentro la safe area di Android. */
.content-safe-area {
  --padding-bottom: calc(var(--space-6) + env(safe-area-inset-bottom));
}

.settings-group {
  margin-bottom: var(--space-4);
  padding: var(--space-2) 0;
  background: transparent;
}

.settings-group ion-item {
  --background: transparent;
  --inner-padding-top: var(--space-2);
  --inner-padding-bottom: var(--space-2);
  --min-height: var(--touch-target);
}

.input-icon {
  color: var(--peach);
  font-size: var(--font-lg);
}

.settings-group ion-label h2 {
  font-size: var(--font-md);
  font-weight: var(--font-weight-semibold);
  margin: 0;
}

.settings-group ion-label p {
  font-size: var(--font-sm);
  color: var(--text-muted, var(--subtext0));
  margin: var(--space-1) 0 0;
  white-space: normal;
}

ion-select {
  --highlight-color-focused: var(--peach);
  width: 100%;
}

/* Banner informativo/di avviso: bordo laterale colorato invece del fondo
   pieno, così resta leggibile su entrambi i temi. */
.notice {
  display: flex;
  align-items: flex-start;
  gap: var(--space-3);
  padding: var(--space-3) var(--space-4);
  margin-bottom: var(--space-4);
  border-radius: var(--radius-md);
  background-color: var(--glass-bg);
  border: 1px solid var(--glass-border);
  border-inline-start: 3px solid var(--overlay1);
}

.notice ion-icon {
  font-size: var(--font-lg);
  flex-shrink: 0;
  margin-top: 2px;
}

.notice--info {
  border-inline-start-color: var(--blue);
}

.notice--info ion-icon {
  color: var(--blue);
}

.notice--warning {
  border-inline-start-color: var(--peach);
}

.notice--warning ion-icon {
  color: var(--peach);
}

.notice-title {
  margin: 0;
  font-size: var(--font-base);
  font-weight: var(--font-weight-semibold);
  color: var(--text);
}

.notice-body {
  margin: var(--space-1) 0 0;
  font-size: var(--font-sm);
  color: var(--text-muted, var(--subtext0));
}

.summary-note {
  margin: calc(-1 * var(--space-2)) 0 var(--space-4);
  padding-inline: var(--space-4);
  font-size: var(--font-sm);
  color: var(--text-muted, var(--subtext0));
}

.actions {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-3);
}

.test-btn {
  --color: var(--peach);
  --border-color: var(--peach);
  --border-radius: var(--radius-pill);
  width: 100%;
  text-transform: none;
}

.device-count {
  display: inline-flex;
  align-items: center;
  gap: var(--space-2);
  margin: 0;
  font-size: var(--font-sm);
  color: var(--text-muted, var(--subtext0));
}

.state-block--error ion-icon {
  color: var(--red);
}
</style>
