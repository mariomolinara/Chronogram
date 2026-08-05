<template>
  <ion-page>
    <ion-header translucent>
      <ion-toolbar color="dark">
        <ion-title class="ion-text-center title-peach">Administration</ion-title>
        <ion-buttons slot="end">
          <ion-button class="tap-target" aria-label="Sign out" @click="confirmLogout">
            <ion-icon :icon="exitOutline" color="danger" />
          </ion-button>
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content :fullscreen="true" class="ion-padding content-safe-area">
      <div class="admin-container">

        <!-- Loading -->
        <div v-if="loading" class="state-block" role="status" aria-live="polite">
          <ion-spinner name="crescent" />
          <p>Loading statistics…</p>
        </div>

        <!-- Error -->
        <div v-else-if="error" class="state-block" role="alert">
          <ion-icon :icon="alertCircleOutline" />
          <p>{{ error }}</p>
          <ion-button fill="outline" size="small" @click="loadStats">Retry</ion-button>
        </div>

        <template v-else-if="stats">
          <!-- ── Partecipanti ────────────────────────────────────────── -->
          <!-- Prima delle metriche: se qualcuno aspetta una decisione, è la
               sola cosa in questa pagina che richiede un'azione. -->
          <section class="glass-card panel users-panel" aria-labelledby="users-heading">
            <div class="users-text">
              <h2 id="users-heading" class="section-title">
                Participants
                <span
                    v-if="pendingCount > 0"
                    class="pending-badge"
                    :aria-label="`${pendingCount} registrations waiting for approval`"
                >
                  <ion-icon :icon="hourglassOutline" aria-hidden="true" />
                  {{ pendingCount }} pending
                </span>
              </h2>
              <p class="panel-note users-note">
                {{ pendingCount > 0
                  ? 'Registrations are waiting for approval: until then those people cannot sign in.'
                  : 'Approve, block or remove participant accounts.' }}
              </p>
            </div>
            <ion-button :fill="pendingCount > 0 ? 'solid' : 'outline'" @click="goToUsers">
              <ion-icon slot="start" :icon="peopleOutline" />
              {{ pendingCount > 0 ? 'Review requests' : 'Manage participants' }}
            </ion-button>
          </section>

          <!-- ── Metriche ────────────────────────────────────────────── -->
          <section aria-labelledby="metrics-heading">
            <h2 id="metrics-heading" class="section-title">Overview</h2>
            <div class="metric-grid">
              <article class="glass-card metric">
                <p class="metric-value">{{ stats.totalUsers }}</p>
                <p class="metric-label">Registered users</p>
              </article>
              <article class="glass-card metric">
                <p class="metric-value">{{ stats.activeUsers }}</p>
                <p class="metric-label">Active (last {{ stats.activeWindowDays }} days)</p>
                <p class="metric-hint">At least one sign-in in the window</p>
              </article>
              <article class="glass-card metric">
                <p class="metric-value">{{ stats.regularUsers }}</p>
                <p class="metric-label">Regular ({{ stats.regularWindowDays }}-day streak)</p>
                <p class="metric-hint">Signed in on every one of the days</p>
              </article>
              <article class="glass-card metric">
                <p class="metric-value">{{ stats.totalActivities }}</p>
                <p class="metric-label">Activities collected</p>
              </article>
              <article class="glass-card metric">
                <p class="metric-value">{{ stats.activitiesLastWeek }}</p>
                <p class="metric-label">Activities in the last 7 days</p>
              </article>
            </div>
          </section>

          <!-- ── Distribuzione temporale ─────────────────────────────── -->
          <section class="glass-card panel" aria-labelledby="chart-heading">
            <h2 id="chart-heading" class="section-title">
              Activities per day
              <span class="section-subtitle">last {{ stats.dailyActivities.length }} days</span>
            </h2>

            <p v-if="maxDaily === 0" class="empty-note">
              No activity recorded in this window yet.
            </p>

            <div ref="chartHost" class="chart-wrapper" @pointerleave="hovered = null">
              <svg
                  class="chart"
                  :viewBox="`0 0 ${chartWidth} ${CHART_H}`"
                  preserveAspectRatio="none"
                  role="img"
                  :aria-label="chartSummary"
              >
                <!-- Griglia recessiva: solo tre riferimenti orizzontali -->
                <line
                    v-for="tick in yTicks"
                    :key="`grid-${tick.value}`"
                    class="grid-line"
                    x1="0" :y1="tick.y" :x2="chartWidth" :y2="tick.y"
                />
                <!-- Una barra per giorno, ancorata alla baseline -->
                <path
                    v-for="(bar, index) in bars"
                    :key="bar.day"
                    class="bar"
                    :class="{ 'bar--muted': hovered !== null && hovered !== index }"
                    :d="bar.path"
                    @pointerenter="hovered = index"
                />
              </svg>

              <!-- Etichette asse: solo primo, mediano e ultimo giorno -->
              <div class="x-axis">
                <span>{{ formatDay(bars[0].day) }}</span>
                <span>{{ formatDay(bars[Math.floor(bars.length / 2)].day) }}</span>
                <span>{{ formatDay(bars[bars.length - 1].day) }}</span>
              </div>

              <p class="chart-readout" aria-live="polite">
                <template v-if="hovered !== null">
                  <strong>{{ formatFullDay(bars[hovered].day) }}</strong>
                  — {{ bars[hovered].count }}
                  {{ bars[hovered].count === 1 ? 'activity' : 'activities' }}
                </template>
                <template v-else>
                  Peak: {{ maxDaily }} on {{ formatFullDay(peakDay) }}
                </template>
              </p>
            </div>

            <!-- Alternativa testuale al grafico (accessibilità) -->
            <details v-if="maxDaily > 0" class="table-view">
              <summary>View as table</summary>
              <table>
                <caption class="sr-only">Activities recorded per day</caption>
                <thead>
                <tr><th scope="col">Day</th><th scope="col">Activities</th></tr>
                </thead>
                <tbody>
                <tr v-for="bar in bars" :key="`row-${bar.day}`">
                  <td>{{ bar.day }}</td>
                  <td>{{ bar.count }}</td>
                </tr>
                </tbody>
              </table>
            </details>
          </section>

          <!-- ── Export ──────────────────────────────────────────────── -->
          <section class="glass-card panel" aria-labelledby="export-heading">
            <h2 id="export-heading" class="section-title">Export data</h2>
            <p class="panel-note">
              Activities are pseudonymised: they carry a numeric <code>user_id</code>, not an
              email. Join them with the users file on that column when identities are needed.
              The users file contains personal data — handle it accordingly.
            </p>
            <div class="export-actions">
              <ion-button
                  fill="outline"
                  :disabled="downloading !== null"
                  @click="download('activities')"
              >
                <ion-icon slot="start" :icon="downloadOutline" />
                {{ downloading === 'activities' ? 'Preparing…' : 'Activities CSV' }}
              </ion-button>
              <ion-button
                  fill="outline"
                  :disabled="downloading !== null"
                  @click="download('users')"
              >
                <ion-icon slot="start" :icon="downloadOutline" />
                {{ downloading === 'users' ? 'Preparing…' : 'Users CSV' }}
              </ion-button>
            </div>
          </section>

          <!-- ── Account ─────────────────────────────────────────────── -->
          <section class="glass-card panel" aria-labelledby="account-heading">
            <h2 id="account-heading" class="section-title">Administrator account</h2>
            <p class="panel-note">
              This is a built-in account: it cannot be deleted and its role cannot change.
              Only its email and password can be updated.
            </p>
            <ion-button fill="outline" @click="goToCredentials">
              <ion-icon slot="start" :icon="keyOutline" />
              Change email or password
            </ion-button>
          </section>
        </template>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonIcon, IonButton,
  IonButtons, IonSpinner, alertController, onIonViewWillEnter, toastController
} from '@ionic/vue';
import {
  alertCircleOutline, downloadOutline, exitOutline, hourglassOutline, keyOutline,
  peopleOutline
} from 'ionicons/icons';
import { api } from '@/composables/useApi';
import { useAuthStore } from '@/store/auth';

interface DailyPoint {
  day: string;
  count: number;
}

interface AdminStats {
  totalUsers: number;
  activeUsers: number;
  regularUsers: number;
  totalActivities: number;
  activitiesLastWeek: number;
  activeWindowDays: number;
  regularWindowDays: number;
  dailyActivities: DailyPoint[];
}

const router = useRouter();
const auth = useAuthStore();

const stats = ref<AdminStats | null>(null);
const pendingCount = ref(0);
const loading = ref(true);
const error = ref<string | null>(null);
const downloading = ref<'activities' | 'users' | null>(null);
const hovered = ref<number | null>(null);

// Il viewBox segue la larghezza reale del contenitore invece di essere fisso: con
// una larghezza fissa e `preserveAspectRatio="none"` lo stiramento orizzontale
// deformerebbe gli arrotondamenti e allargherebbe i distacchi fra le barre. Così
// 2px di stacco e 4px di raggio restano 2 e 4 pixel a schermo.
const CHART_H = 160;
const BAR_GAP = 2;   // spaziatura fra barre adiacenti (2px di superficie)
const BAR_RADIUS = 4; // arrotondamento del solo estremo dei dati

const chartHost = ref<HTMLElement | null>(null);
const chartWidth = ref(600);
let resizeObserver: ResizeObserver | null = null;

const maxDaily = computed(() =>
    stats.value ? Math.max(0, ...stats.value.dailyActivities.map((p) => p.count)) : 0
);

const peakDay = computed(() => {
  const points = stats.value?.dailyActivities ?? [];
  const peak = points.reduce<DailyPoint | null>(
      (best, point) => (best === null || point.count > best.count ? point : best), null);
  return peak?.day ?? '';
});

/**
 * Barre ancorate alla baseline con i due angoli superiori arrotondati. Un `rect`
 * con `rx` arrotonderebbe anche il fondo, staccando la barra dall'asse.
 */
const bars = computed(() => {
  const points = stats.value?.dailyActivities ?? [];
  const slot = chartWidth.value / Math.max(points.length, 1);
  const width = Math.max(slot - BAR_GAP, 1);
  const peak = maxDaily.value || 1;

  return points.map((point, index) => {
    const height = point.count === 0 ? 0 : Math.max((point.count / peak) * CHART_H, BAR_RADIUS);
    const x = index * slot;
    const y = CHART_H - height;
    const r = Math.min(BAR_RADIUS, width / 2, height);

    const path = height === 0
        ? ''
        : `M ${x} ${CHART_H} L ${x} ${y + r} A ${r} ${r} 0 0 1 ${x + r} ${y} `
        + `L ${x + width - r} ${y} A ${r} ${r} 0 0 1 ${x + width} ${y + r} `
        + `L ${x + width} ${CHART_H} Z`;

    return { day: point.day, count: point.count, path };
  });
});

const yTicks = computed(() => {
  const peak = maxDaily.value;
  if (peak === 0) {
    return [];
  }
  return [0.5, 1].map((fraction) => ({
    value: Math.round(peak * fraction),
    y: CHART_H - fraction * CHART_H
  }));
});

const chartSummary = computed(() => {
  const total = bars.value.reduce((sum, bar) => sum + bar.count, 0);
  return `Bar chart of activities per day over the last ${bars.value.length} days. `
      + `${total} in total, peaking at ${maxDaily.value} on ${peakDay.value}.`;
});

const formatDay = (iso: string) => iso.slice(5); // MM-DD
const formatFullDay = (iso: string) => iso;

async function loadStats() {
  loading.value = true;
  error.value = null;
  try {
    const { data } = await api.get<{ data: AdminStats }>('/api/admin/stats');
    stats.value = data.data;
  } catch {
    // Il 401 è già gestito dall'interceptor (logout + redirect); qui resta il
    // caso di rete/servizio non raggiungibile.
    error.value = 'Could not load statistics. Please try again.';
  } finally {
    loading.value = false;
  }
}

/**
 * Le statistiche non contano gli account in attesa: il totale arriva dalla
 * lista utenti, che lo porta con sé in `counts` indipendentemente dal filtro.
 * Si chiede una sola riga (`size=1`) perché qui serve solo il numero, e un
 * fallimento lascia semplicemente il badge assente: non deve rompere la pagina.
 */
async function loadPendingCount() {
  try {
    const { data } = await api.get<{ data: { counts: { pending: number } } }>(
        '/api/admin/users', { params: { size: 1 } }
    );
    pendingCount.value = data.data?.counts?.pending ?? 0;
  } catch {
    pendingCount.value = 0;
  }
}

const goToUsers = () => router.push({ name: 'AdminUsers' });

/**
 * Scarica un CSV. La richiesta passa dall'istanza axios autenticata, quindi non
 * si può usare un semplice link: il blob viene materializzato e rilasciato
 * subito dopo per non trattenere memoria.
 */
async function download(kind: 'activities' | 'users') {
  downloading.value = kind;
  try {
    const response = await api.get(`/api/admin/export/${kind}.csv`, { responseType: 'blob' });
    const url = URL.createObjectURL(new Blob([response.data], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `chronogram-${kind}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    URL.revokeObjectURL(url);
  } catch {
    const toast = await toastController.create({
      message: 'Export failed. Please try again.',
      duration: 3000,
      color: 'danger'
    });
    await toast.present();
  } finally {
    downloading.value = null;
  }
}

const goToCredentials = () => router.push({ name: 'AdminCredentials' });

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

onMounted(async () => {
  await Promise.all([loadStats(), loadPendingCount()]);
  // Il wrapper esiste solo dopo che i dati sono arrivati (v-else sul loading).
  await nextTick();
  if (chartHost.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver(([entry]) => {
      chartWidth.value = Math.max(Math.round(entry.contentRect.width), 120);
    });
    resizeObserver.observe(chartHost.value);
  }
});

/**
 * Ionic tiene la pagina montata quando si naviga verso la lista utenti: senza
 * questo, tornando indietro dopo aver approvato una richiesta il badge
 * mostrerebbe ancora il conteggio di prima. `onMounted` non basta.
 */
onIonViewWillEnter(() => {
  if (stats.value) {
    void loadPendingCount();
  }
});

onUnmounted(() => {
  resizeObserver?.disconnect();
  resizeObserver = null;
});
</script>

<style scoped>
.admin-container {
  /* Più largo delle pagine mobile: la dashboard si consulta da desktop. */
  max-width: 1200px;
  margin-inline: auto;
  padding-bottom: var(--space-6);
  display: flex;
  flex-direction: column;
  gap: var(--space-5);
}

.section-title {
  font-size: var(--font-lg);
  font-weight: var(--font-weight-semibold);
  color: var(--text);
  margin: 0 0 var(--space-3);
  display: flex;
  align-items: baseline;
  gap: var(--space-2);
  flex-wrap: wrap;
}

.section-subtitle {
  font-size: var(--font-sm);
  font-weight: var(--font-weight-medium);
  color: var(--subtext0);
}

/* ── Partecipanti ─────────────────────────────────────────── */

.users-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-3);
  flex-wrap: wrap;
}

.users-note {
  margin-bottom: 0;
}

/* Il badge non affida il significato al solo colore: porta parola e icona,
   le stesse della coda in AdminUsersPage. */
.pending-badge {
  display: inline-flex;
  align-items: center;
  gap: var(--space-1);
  font-size: var(--font-xs);
  font-weight: var(--font-weight-semibold);
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--peach);
  border: 1px solid var(--peach);
  border-radius: var(--radius-pill);
  padding: var(--space-1) var(--space-3);
}

/* ── Metriche ─────────────────────────────────────────────── */

.metric-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(170px, 1fr));
  gap: var(--space-3);
}

.metric {
  padding: var(--space-4);
}

.metric-value {
  font-size: var(--font-2xl);
  font-weight: var(--font-weight-bold);
  /* Il numero resta in inchiostro di testo: il colore non veicola dati qui. */
  color: var(--text);
  margin: 0;
  line-height: 1.1;
}

.metric-label {
  font-size: var(--font-base);
  color: var(--subtext1);
  margin: var(--space-2) 0 0;
}

.metric-hint {
  font-size: var(--font-xs);
  color: var(--overlay1);
  margin: var(--space-1) 0 0;
}

/* ── Pannelli ─────────────────────────────────────────────── */

.panel {
  padding: var(--space-4);
}

.panel-note {
  font-size: var(--font-sm);
  color: var(--subtext0);
  margin: 0 0 var(--space-4);
  line-height: 1.5;
}

.panel-note code {
  font-size: var(--font-xs);
  background: var(--surface1);
  border-radius: var(--radius-sm);
  padding: 0 var(--space-1);
}

.empty-note {
  font-size: var(--font-base);
  color: var(--subtext0);
  margin: 0;
}

/* ── Grafico ──────────────────────────────────────────────── */

.chart-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-2);
}

.chart {
  width: 100%;
  height: 160px;
  display: block;
  overflow: visible;
}

.grid-line {
  stroke: var(--surface1);
  stroke-width: 1;
  vector-effect: non-scaling-stroke;
}

.bar {
  fill: var(--mauve);
  transition: opacity 120ms ease;
}

/* L'evidenziazione attenua le altre barre invece di ricolorare quella attiva:
   la tinta continua a significare "attività", non "selezione". */
.bar--muted {
  opacity: 0.35;
}

.x-axis {
  display: flex;
  justify-content: space-between;
  font-size: var(--font-xs);
  color: var(--overlay1);
}

.chart-readout {
  font-size: var(--font-sm);
  color: var(--subtext0);
  margin: 0;
  min-height: 1.2em;
}

.table-view {
  margin-top: var(--space-4);
  font-size: var(--font-sm);
  color: var(--subtext0);
}

.table-view summary {
  cursor: pointer;
  min-height: var(--touch-target);
  display: flex;
  align-items: center;
}

.table-view table {
  width: 100%;
  border-collapse: collapse;
  margin-top: var(--space-2);
}

.table-view th,
.table-view td {
  text-align: left;
  padding: var(--space-1) var(--space-2);
  border-bottom: 1px solid var(--surface1);
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

/* ── Export ───────────────────────────────────────────────── */

.export-actions {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-3);
}
</style>
