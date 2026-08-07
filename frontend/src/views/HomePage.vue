<template>
  <ion-page>
    <!-- Toolbar trasparente: serve solo ad ancorare il logout in alto a destra
         senza introdurre una barra opaca sopra `.user-info`. -->
    <ion-header class="home-header">
      <ion-toolbar class="home-toolbar">
        <ion-buttons slot="start">
          <ion-button class="tap-target" aria-label="About Chronogram" @click="goToAbout">
            <ion-icon :icon="informationCircleOutline" aria-hidden="true" />
          </ion-button>
        </ion-buttons>
        <ion-buttons slot="end">
          <ion-button class="tap-target logout-btn" aria-label="Sign out" @click="confirmLogout">
            <ion-icon :icon="logOutOutline" color="danger" aria-hidden="true" />
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <div class="user-info gradient-text">
        <ion-icon :icon="personCircleOutline" class="user-icon" aria-hidden="true" />
        <h2>{{ displayName }}</h2>
      </div>

      <div class="time-diary-page">
        <div class="current-date-display">
          <h3>{{ formattedCurrentDate }}</h3>
        </div>

        <div class="timeline-container">
          <transition-group name="activity" tag="div">
            <div
                v-for="a in activities"
                :key="a.activityId"
                class="activity-row"
                :style="{ '--stripe-color': categoryColors[a.activityTypeName] || 'var(--surface2)' }"
            >
              <div class="time-label">{{ formatTime(a.createdAt) }}</div>
              <div
                  class="timeline-dot"
                  :style="{ background: categoryColors[a.activityTypeName] || 'var(--surface2)' }"
              />
              <div class="timeline-line" />
              <ion-card
                  class="activity-bubble"
                  :style="{ borderLeftColor: categoryColors[a.activityTypeName] || 'var(--surface2)' }"
                  @click="editActivity(a)"
              >
                <ion-card-content>
                  <div class="action-buttons">
                    <ion-button fill="clear" size="small" @click.stop="confirmDelete(a.activityId)">
                      <ion-icon :icon="trashBinOutline" color="danger" />
                    </ion-button>
                  </div>

                  <strong>{{ a.activityTypeName }}</strong> {{ a.activityTypeDescription }}
                  <p v-if="a.details">{{ a.details }}</p>
                  <p v-if="a.location">Location: {{ a.location }}</p>
                  <p v-if="a.costEuro">Cost: €{{ a.costEuro }}</p>
                </ion-card-content>
              </ion-card>
            </div>
          </transition-group>
          <!--
            Loading state: sta DENTRO il contenuto, non è più un `ion-loading`
            a tutta pagina. Quello veniva aperto durante il montaggio della
            vista, prima che il custom element fosse idratato: `ion-loading`
            in quel caso rimanda la `present()` a un `requestAnimationFrame`
            (vedi `componentDidLoad`), e se nel frattempo la risposta è già
            arrivata la `dismiss()` non trova nulla da chiudere. L'overlay
            veniva presentato subito dopo e non lo chiudeva più nessuno:
            backdrop a tutto schermo e `body.backdrop-no-scroll` appesi per
            sempre, con l'app inutilizzabile. Un indicatore nel flusso non ha
            questa classe di problemi e non blocca comunque la pagina.
          -->
          <div v-if="isLoading" class="state-block" role="status" aria-live="polite">
            <ion-spinner name="crescent" aria-hidden="true" />
            <ion-text>Loading activities…</ion-text>
          </div>

          <!-- Error state -->
          <div v-else-if="loadError" class="state-block">
            <ion-icon :icon="alertCircleOutline" aria-hidden="true" />
            <ion-text>Couldn't load your activities.</ion-text>
            <ion-button fill="outline" size="small" @click="fetchActivities">Retry</ion-button>
          </div>

          <!-- Empty state -->
          <div v-else-if="activities.length === 0" class="state-block">
            <ion-icon :icon="calendarClearOutline" aria-hidden="true" />
            <ion-text>No activities for today.</ion-text>
            <ion-button fill="outline" size="small" @click="addActivity">Add your first activity</ion-button>
          </div>
        </div>

        <div class="bottom-icons-container">
          <!-- Home icon (current page, non-clickable) -->
          <div class="bottom-icon left home-icon" aria-current="page">
            <ion-icon :icon="homeOutline" aria-label="Home" />
          </div>

          <!-- Plus button (fixed position) -->
          <div class="bottom-icon center">
            <ion-fab-button class="add-fab-btn" aria-label="Add activity" @click="addActivity">
              <ion-icon :icon="addOutline" aria-hidden="true" />
            </ion-fab-button>
          </div>

          <!-- Settings icon (clickable) -->
          <div
              class="bottom-icon settings-icon"
              role="button"
              tabindex="0"
              aria-label="Settings"
              @click="navigateTab({ detail: { value: 'settings' } })"
              @keydown.enter="navigateTab({ detail: { value: 'settings' } })"
              @keydown.space.prevent="navigateTab({ detail: { value: 'settings' } })"
          >
            <ion-icon :icon="settingsOutline" aria-hidden="true" />
          </div>
        </div>
      </div>
    </ion-content>
    <ion-alert
        :is-open="showDeleteConfirm"
        header="Confirm Delete"
        :message="`Delete activity from ${deleteTime}?`"
        :buttons="alertButtons"
        @didDismiss="handleAlertDismiss"
    />
  </ion-page>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonButtons, IonContent, IonIcon, IonButton,
  IonCard, IonCardContent, IonFabButton,
  IonSpinner, IonText, IonAlert, alertController
} from '@ionic/vue';
import {
  homeOutline, settingsOutline, personCircleOutline, addOutline, trashBinOutline,
  alertCircleOutline, calendarClearOutline, logOutOutline, informationCircleOutline
} from 'ionicons/icons';
import dayjs from 'dayjs';
import { api } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';

import { watch } from 'vue';
import { useActivityStore } from '@/store/activityStore';
import { useAuthStore } from '@/store/auth';

/* ---------- State ---------- */
const router = useRouter();
const auth = useAuthStore();
const showDeleteConfirm = ref(false);
const pendingDeleteId = ref<number | null>(null);
const deleteTime = ref('');

const alertButtons = ref([
  { text: 'Cancel', role: 'cancel' },
  { text: 'Delete', handler: () => {
      if (pendingDeleteId.value !== null) {
        deleteActivity(pendingDeleteId.value);
      }
    }
  }
]);

const currentDate = ref(dayjs());

interface Activity {
  activityId: number;
  activityDate: string;
  durationMins: number;
  pleasantness: number;
  location: string;
  costEuro: string;
  userId: number;
  activityTypeId: number;
  createdAt: string;
  updatedAt: string;
  activityTypeName: string;
  activityTypeDescription: string;
  isInstrumental: boolean;
  isRoutinary: boolean;
  details?: string;
}

const activities = ref<Activity[]>([]);
const isLoading = ref(false);
const loadError = ref(false);

const activityStore = useActivityStore();
const { showToast } = useToast();

/* ---------- Computed ---------- */
const formattedCurrentDate = computed(() => {
  return currentDate.value.format('MMMM D, YYYY');
});

/**
 * Identificativo dell'utente autenticato. Lo store espone già la forma
 * presentabile; qui resta solo il fallback per la sessione non ancora
 * ripristinata (mai il vecchio placeholder "User name").
 */
const displayName = computed(() => auth.displayName || '—');

/* ---------- Methods ---------- */
const navigateTab = (event: { detail: { value: string } }) => {
  const tab = event.detail.value;
  if (tab === 'home') router.push({ name: 'Home' });
  else if (tab === 'settings') router.push({ name: 'Settings' });
};

function formatTime(isoTimestamp: string): string {
  return dayjs(isoTimestamp).format('HH:mm');
}

const categoryColors: Record<string, string> = {
  'Work': 'var(--mauve)',
  'Study': 'var(--blue)',
  'Leisure': 'var(--green)',
  'Exercise': 'var(--sky)',
  'Food': 'var(--peach)',
  'Hygiene': 'var(--teal)',
  'Commute': 'var(--mauve)',
  'Default': 'var(--surface2)'
};

watch(() => activityStore.needsRefresh, (shouldRefresh) => {
  if (shouldRefresh) {
    fetchActivities();                 // 🔁 richiama la lista aggiornata
    activityStore.needsRefresh = false;
  }
});

function editActivity(activity: Activity) {
  router.push({
    name: 'AddActivity',
    query: {
      id: activity.activityId.toString(),
      name: activity.activityTypeName,
      durationMins: activity.durationMins?.toString() || '',
      details: activity.details || '',
      pleasantness: activity.pleasantness.toString(),
      activityTypeId: activity.activityTypeId.toString(),
      recurrence: activity.isRoutinary ? 'R' : 'E',
      costEuro: activity.costEuro || '',
      location: activity.location || ''
    }
  });
}

function handleAlertDismiss() {
  showDeleteConfirm.value = false;
  pendingDeleteId.value = null;
}

async function deleteActivity(activityId: number) {
  isLoading.value = true;
  try {
    const { data } = await api.post(`/api/activities/delete`, {
      activityId: activityId
    });

    if (data?.success) {
      showToast('Activity deleted', 'success');
      activities.value = activities.value.filter(a => a.activityId !== activityId);

      // Focus management after successful deletion
      setTimeout(() => {
        const safeElement = document.querySelector('.add-fab-btn') as HTMLElement;
        if (safeElement) safeElement.focus();
      }, 100);
    } else {
      throw new Error(data?.message || 'Failed to delete activity');
    }
  } catch (err: any) {
    console.error('Delete error:', err);
    showToast(err.message || 'Delete failed', 'danger');
  } finally {
    isLoading.value = false;
    pendingDeleteId.value = null;
    showDeleteConfirm.value = false;
  }
}

function confirmDelete(activityId: number) {
  const activity = activities.value.find(a => a.activityId === activityId);
  if (!activity) return;

  pendingDeleteId.value = activityId;
  deleteTime.value = formatTime(activity.createdAt);
  showDeleteConfirm.value = true;

  // Immediately move focus to the alert's first button
  setTimeout(() => {
    const firstAlertButton = document.querySelector('.alert-button:first-child') as HTMLElement;
    firstAlertButton?.focus();
  }, 50);
}

async function fetchActivities() {
  isLoading.value = true;
  loadError.value = false;
  activities.value = [];

  // Nessun userId nel payload: il backend risolve l'utente dal principal JWT.
  const activityDate = currentDate.value.format('YYYY-MM-DD');

  try {
    const { data } = await api.post('/api/activities/list', { activityDate });

    if (!data?.success) {
      throw new Error(data?.message || 'Failed to fetch activities');
    }

    activities.value = data.data || [];
  } catch (err: any) {
    console.error('Activity fetch error:', err);
    loadError.value = true;
    const message = err.response?.data?.message || err.message || 'Unexpected error';
    showToast(message, 'danger');
  } finally {
    isLoading.value = false;
  }
}

function addActivity() {
  router.push({ name: 'AddActivity' });
}

function goToAbout() {
  router.push({ name: 'About' });
}

/**
 * Stesso meccanismo (e stesso wording) della dashboard admin: conferma via
 * `alertController` e poi `logout()` dello store, che ripulisce la sessione
 * persistita e riporta al login.
 */
async function confirmLogout() {
  const alert = await alertController.create({
    header: 'Sign out?',
    buttons: [
      { text: 'Cancel', role: 'cancel' },
      { text: 'Sign out', role: 'destructive', handler: () => auth.logout() }
    ]
  });
  await alert.present();
}

/* ---------- Lifecycle Hooks ---------- */
onMounted(() => {
  document.documentElement.setAttribute('data-theme', 'mocha');
  fetchActivities();
});
</script>

<style scoped>
/* Header: solo il logout, su fondo trasparente e senza bordo, così la Home
   mantiene l'aspetto immersivo che aveva con l'header vuoto. */
.home-header {
  box-shadow: none;
}
.home-header::after {
  /* Ionic (modalità md) disegna qui la linea di separazione dell'header. */
  display: none;
}
.home-toolbar {
  --background: transparent;
  --border-width: 0;
  --min-height: 48px;
  --padding-end: var(--space-2);
}
.logout-btn {
  --padding-start: var(--space-2);
  --padding-end: var(--space-2);
  --color: var(--red);
  --border-radius: 50%;
}
.logout-btn ion-icon {
  font-size: var(--font-lg);
}

/* User Info */
.user-info {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: var(--space-2);
  margin-bottom: var(--space-5);
}
.user-icon {
  font-size: var(--font-2xl);
  color: var(--ion-color-primary);
}

/* Current Date Display */
.current-date-display {
  text-align: center;
  margin-bottom: var(--space-5);
}
.current-date-display h3 {
  margin: 0;
  font-size: var(--font-lg);
  color: var(--text);
}

/* Timeline */
.timeline-container { padding: 0 0 64px 0; }
.activity-row {
  position: relative;
  display: flex;
  align-items: flex-start;
  margin-bottom: 18px;
  --stripe-color: var(--surface2);
}
.time-label {
  width: 50px;
  text-align: right;
  font-size:.75rem;
  color: var(--overlay1);
  margin-right: 14px;
}
.timeline-dot {
  position: absolute;
  left: 50px;
  top: 6px;
  width: 8px; height: 8px;
  border-radius: 50%;
  background: var(--stripe-color);
}
.timeline-line {
  position: absolute;
  left: 53.5px;
  top: 18px; bottom: -18px;
  width: 1px;
  background: var(--surface2);
}
.activity-bubble {
  flex: 1;
  margin-left: 64px;
  --background: var(--glass-bg-subtle);
  backdrop-filter: blur(10px);
  border: 1px solid var(--glass-border);
  border-left: 4px solid var(--stripe-color);
  border-radius: var(--radius-lg) var(--radius-lg) 6px var(--radius-lg);
}
.activity-bubble:hover {
  background: linear-gradient(
      120deg,
      color-mix(in srgb, var(--peach) 15%, transparent),
      color-mix(in srgb, var(--mauve) 15%, transparent)
  );
  box-shadow: 0 0 12px -2px var(--mauve);
}

/* Botones Home y Settings */
.bottom-icons-container {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 var(--space-6) calc(var(--space-5) + env(safe-area-inset-bottom));
  z-index: 100;
}

.bottom-icon {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: transparent;
  cursor: pointer;
}

.bottom-icon ion-icon {
  font-size: 28px;
}

/* Home icon - special color, no click */
.home-icon {
  pointer-events: none;
  color: var(--peach);
}

/* Settings icon - clickable, highlight */
.settings-icon ion-icon {
  color: var(--overlay1); /* Original color */
}

/* Settings button */
.bottom-icon.right ion-button {
  --padding-start: 0;
  --padding-end: 0;
  min-width: 44px;
  min-height: 44px;
  color: var(--overlay1); /* Original color */
}
.bottom-icon.right ion-icon {
  font-size: 28px; /* Increased size */
}

/* Central plus button */
.add-fab-btn {
  --background: var(--gradient-pink-mauve);
  --color: var(--crust);
  width: 60px;
  height: 60px;
  min-width: 60px;
  min-height: 60px;
  padding: 0;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4px 14px -4px var(--mauve);
}

.add-fab-btn ion-icon {
  font-size: 28px;
}

/* Adjust timeline padding to avoid overlap */
.timeline-container {
  padding-bottom: 100px;
}

.action-buttons {
  position: absolute;
  top: var(--space-2);
  right: var(--space-2);
  display: flex;
  gap: var(--space-1);
  z-index: 10;
}

.action-buttons ion-button {
  --padding-start: 4px;
  --padding-end: 4px;
  --padding-top: 4px;
  --padding-bottom: 4px;
  min-width: var(--touch-target);
  min-height: var(--touch-target);
  margin: 0;
}

.activity-bubble {
  position: relative; /* Needed for absolute positioning of buttons */
}
/* Adjust card content padding to make space for buttons */
ion-card-content {
  position: relative;
  padding-right: 40px !important;
}

</style>