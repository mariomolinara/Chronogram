<!-- File: src/views/AddActivityPage.vue -->
<template>
  <ion-page>
    <!-- HEADER -->
    <ion-header>
      <ion-toolbar>
        <ion-title class="title-centered">
          <ion-icon :icon="clipboardOutline" class="title-icon" aria-hidden="true" /> New Activity
        </ion-title>
        <ion-note slot="end" class="date-note">{{ currentDate }}</ion-note>
      </ion-toolbar>
    </ion-header>

    <!-- CONTENT -->
    <ion-content :fullscreen="true" class="ion-padding content-safe">
      <div class="activity-container">

        <div class="form-wrapper">

          <!-- ── AI ENTRY POINT ─────────────────────────────────────────
               Sostituisce il FAB anonimo: l'affordance dichiara cosa fa,
               sta nel punto della pagina in cui l'utente decide "compilo a
               mano o mi faccio aiutare?" e non copre più i pulsanti in
               fondo alla form. -->
          <section class="ai-assist">
            <button
                ref="aiEntryButton"
                type="button"
                class="ai-assist-card"
                aria-haspopup="dialog"
                aria-label="Fill with AI: describe your activity and we fill the form fields for you"
                @click="openAIModal"
            >
              <span class="ai-assist-icon" aria-hidden="true">
                <ion-icon :icon="sparkles" />
              </span>
              <span class="ai-assist-copy">
                <span class="ai-assist-title">Fill with AI</span>
                <span class="ai-assist-sub">
                  Describe your activity in plain words — we fill the fields, you review and save.
                </span>
              </span>
              <ion-icon :icon="chevronForwardOutline" class="ai-assist-chevron" aria-hidden="true" />
            </button>

            <!-- Promemoria persistente: il toast è effimero, questo resta
                 finché l'utente non salva o lo chiude. -->
            <div v-if="showAiReview" class="ai-review">
              <ion-icon :icon="sparkles" class="ai-review-icon" aria-hidden="true" />
              <span class="ai-review-text">
                Filled by AI. Check the highlighted fields before saving.
              </span>
              <button
                  type="button"
                  class="ai-review-close tap-target"
                  aria-label="Dismiss the AI notice"
                  @click="dismissAiReview"
              >
                <ion-icon :icon="closeOutline" aria-hidden="true" />
              </button>
            </div>
          </section>

          <ion-list lines="none">
            <!-- Name -->
            <ion-item :class="[errorClass('name'), aiClass('name')]" class="glass-input">
              <ion-icon slot="start" :icon="clipboardOutline" class="input-icon" aria-hidden="true" />
              <ion-input
                  v-model="activity.name"
                  label="Name of Activity"
                  label-placement="floating"
                  :aria-label="'Activity Name'"
              />
            </ion-item>

            <!-- Duration -->
            <ion-item :class="[errorClass('durationMins'), aiClass('durationMins')]" class="glass-input">
              <ion-icon slot="start" :icon="timeOutline" class="input-icon" aria-hidden="true" />
              <ion-input
                  v-model.number="activity.durationMins"
                  type="number"
                  label="Duration (minutes)"
                  label-placement="floating"
                  min="1"
                  max="1440"
              />
              <ion-note slot="error">Enter 1-1440 minutes</ion-note>
            </ion-item>

            <!-- Details -->
            <ion-item :class="aiClass('details')" class="glass-input">
              <ion-icon slot="start" :icon="documentTextOutline" class="input-icon" aria-hidden="true" />
              <ion-textarea
                  v-model="activity.details"
                  :auto-grow="true"
                  :maxlength="400"
                  counter
                  label="Details"
                  label-placement="floating"
                  placeholder="Add extra notes..."
              />
            </ion-item>

            <!-- Type -->
            <ion-item :class="[errorClass('activityTypeId'), aiClass('activityTypeId')]" class="glass-input">
              <ion-icon slot="start" :icon="pricetagsOutline" class="input-icon" aria-hidden="true" />
              <ion-select
                  v-model="activity.activityTypeId"
                  label="Type of activity"
                  label-placement="floating"
                  interface="popover"
                  :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
              >
                <ion-select-option
                    v-for="type in activityTypes"
                    :key="type.activityTypeId"
                    :value="type.activityTypeId"
                >
                  {{ type.name }}
                </ion-select-option>
              </ion-select>
            </ion-item>

            <!-- Pleasantness -->
            <ion-item :class="aiClass('pleasantness')" class="glass-input">
              <ion-icon slot="start" :icon="happyOutline" class="input-icon" aria-hidden="true" />
              <div class="pleasantness-container">
                <ion-label id="pleasantness-label">Pleasantness</ion-label>
                <div class="stepper-wrapper">
                  <ion-button
                      fill="clear"
                      size="small"
                      aria-label="Decrease pleasantness"
                      @click="adjustPleasantness(-1)"
                  >
                    −
                  </ion-button>
                  <span
                      class="stepper-value"
                      role="status"
                      aria-labelledby="pleasantness-label"
                      :class="{
                        'positive': activity.pleasantness > 0,
                        'negative': activity.pleasantness < 0
                      }"
                  >
                    {{ activity.pleasantness }}
                  </span>
                  <ion-button
                      fill="clear"
                      size="small"
                      aria-label="Increase pleasantness"
                      @click="adjustPleasantness(1)"
                  >
                    +
                  </ion-button>
                </div>
              </div>
            </ion-item>

            <!-- Recurrence -->
            <ion-item :class="[errorClass('recurrence'), aiClass('recurrence')]" class="glass-input">
              <ion-icon slot="start" :icon="repeatOutline" class="input-icon" aria-hidden="true" />
              <ion-select
                  v-model="activity.recurrence"
                  label="Recurrence"
                  label-placement="floating"
                  interface="popover"
                  :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
              >
                <ion-select-option value="R">Routinary (R)</ion-select-option>
                <ion-select-option value="E">Exceptional (E)</ion-select-option>
              </ion-select>
            </ion-item>

            <!-- Cost -->
            <ion-item :class="aiClass('costEuro')" class="glass-input">
              <ion-icon slot="start" :icon="cashOutline" class="input-icon" aria-hidden="true" />
              <ion-input
                  v-model="activity.costEuro"
                  type="text"
                  inputmode="decimal"
                  label="Cost (€)"
                  label-placement="floating"
                  :aria-label="'Cost'"
              />
            </ion-item>

            <!-- Location -->
            <ion-item :class="aiClass('location')" class="glass-input">
              <ion-icon slot="start" :icon="locationOutline" class="input-icon" aria-hidden="true" />
              <ion-select
                  v-model="activity.location"
                  label="Location"
                  label-placement="floating"
                  interface="popover"
                  :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
              >
                <ion-select-option value="home">At home</ion-select-option>
                <ion-select-option value="work">At work</ion-select-option>
                <ion-select-option value="outside">Outside</ion-select-option>
                <ion-select-option value="other">Other</ion-select-option>
              </ion-select>
            </ion-item>
          </ion-list>

          <ion-grid class="ion-margin-top">
            <ion-row class="ion-justify-content-around">
              <ion-col size="5">
                <ion-button expand="block" class="pill-button gradient-outline" @click="router.back()">Cancel</ion-button>
              </ion-col>
              <ion-col size="5">
                <ion-button
                    expand="block"
                    :disabled="isLoading || hasErrors"
                    class="pill-button gradient-outline"
                    @click="saveActivity"
                >
                  Save Activity
                </ion-button>
              </ion-col>
            </ion-row>
          </ion-grid>
        </div>
      </div>

      <!-- ── AI MODAL ────────────────────────────────────────────────
           Pattern dichiarativo (:is-open + @didDismiss), come nel resto
           dell'app. -->
      <ion-modal
          class="ai-modal"
          :is-open="isModalOpen"
          @did-present="focusPrompt"
          @did-dismiss="closeAIModal"
      >
        <ion-header>
          <ion-toolbar>
            <ion-title>Fill with AI</ion-title>
            <ion-buttons slot="end">
              <ion-button aria-label="Close the AI assistant" @click="isModalOpen = false">Close</ion-button>
            </ion-buttons>
          </ion-toolbar>
        </ion-header>

        <ion-content class="ion-padding">
          <p class="ai-intro">
            Describe your activity in your own words: we fill in the form for you, then
            you review the fields and save.
          </p>

          <ion-item class="glass-input">
            <ion-icon slot="start" :icon="chatbubbleEllipsesOutline" class="input-icon" aria-hidden="true" />
            <ion-textarea
                ref="promptField"
                v-model="naturalInput"
                :auto-grow="true"
                :rows="4"
                :maxlength="400"
                counter
                label="Your description"
                label-placement="floating"
                placeholder="e.g. Gym session this morning, about 90 minutes, €12 for the day pass, felt great afterwards — at the sports centre."
            />
          </ion-item>

          <div class="ai-examples">
            <span id="ai-examples-label" class="ai-examples-label">Or start from an example</span>
            <div class="ai-examples-list" role="group" aria-labelledby="ai-examples-label">
              <button
                  v-for="example in aiExamples"
                  :key="example.label"
                  type="button"
                  class="ai-example"
                  @click="useExample(example.text)"
              >
                <ion-icon :icon="example.icon" aria-hidden="true" />
                <span>{{ example.label }}</span>
              </button>
            </div>
          </div>

          <p class="ai-hint">
            The more you mention — how long it took, what it cost, how you felt, where you
            were — the more fields we can fill.
          </p>

          <div
              v-if="aiNotice"
              class="ai-alert"
              :class="`ai-alert--${aiNotice.tone}`"
              role="alert"
          >
            <ion-icon
                :icon="aiNotice.tone === 'error' ? alertCircleOutline : informationCircleOutline"
                aria-hidden="true"
            />
            <span>{{ aiNotice.text }}</span>
          </div>
        </ion-content>

        <ion-footer class="ai-footer">
          <ion-toolbar>
            <div class="ai-actions">
              <ion-button fill="clear" @click="isModalOpen = false">Cancel</ion-button>
              <ion-button
                  class="pill-button peach-gradient"
                  :disabled="isExtracting || !naturalInput.trim()"
                  @click="handleMagicInput"
              >
                <ion-spinner v-if="isExtracting" slot="start" name="crescent" />
                <ion-icon v-else slot="start" :icon="sparkles" aria-hidden="true" />
                {{ isExtracting ? 'Reading your text…' : 'Fill the form' }}
              </ion-button>
            </div>
          </ion-toolbar>
        </ion-footer>
      </ion-modal>

      <ion-loading :is-open="isLoading" message="Saving activity..." />
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { reactive, ref, computed, onMounted, onUnmounted, nextTick } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons,
  IonButton, IonIcon, IonItem, IonLabel, IonInput, IonTextarea, IonSelect,
  IonSelectOption, IonNote, IonModal, IonSpinner, IonFooter,
  IonLoading, IonGrid, IonRow, IonCol, IonList
} from '@ionic/vue';

import {
  sparkles, clipboardOutline, timeOutline, documentTextOutline,
  pricetagsOutline, happyOutline, repeatOutline, cashOutline, locationOutline,
  chevronForwardOutline, closeOutline, alertCircleOutline, informationCircleOutline,
  chatbubbleEllipsesOutline, barbellOutline, peopleOutline, cartOutline
} from 'ionicons/icons';

import { api } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';
import { useActivityStore } from '@/store/activityStore';

const router = useRouter();
const route = useRoute();
const { showToast } = useToast();

/* ---------- State ---------- */
const isLoading = ref(false);          // salvataggio (full-screen loader)
const isExtracting = ref(false);       // chiamata all'LLM (stato locale alla modale)
const isModalOpen = ref(false);
const naturalInput = ref('');
const activityTypes = ref<any[]>([]);
const currentDate = new Intl.DateTimeFormat('en-GB').format(new Date());

/** Messaggio inline nella modale: errore di rete oppure estrazione vuota. */
const aiNotice = ref<{ tone: 'error' | 'warning'; text: string } | null>(null);
/** Campi appena compilati dall'AI: guidano l'evidenziazione temporanea. */
const aiFilledFields = ref<string[]>([]);
/** Banner persistente "rivedi prima di salvare". */
const showAiReview = ref(false);

const promptField = ref<any>(null);
const aiEntryButton = ref<HTMLButtonElement | null>(null);
let highlightTimer: ReturnType<typeof setTimeout> | null = null;

const activity = reactive({
  name: '',
  durationMins: null as number | null,
  details: '',
  pleasantness: 0,
  activityTypeId: null as number | null,
  recurrence: '' as 'R' | 'E' | '',
  costEuro: '',
  location: '' as 'home' | 'work' | 'outside' | 'other' | '',
});

/** Esempi pronti: mostrano il "livello di dettaglio" che l'AI sa sfruttare. */
const aiExamples = [
  {
    label: 'Gym',
    icon: barbellOutline,
    text: 'Gym session this morning, about 90 minutes, €12 for the day pass, felt great afterwards — at the sports centre.'
  },
  {
    label: 'Team meeting',
    icon: peopleOutline,
    text: 'Weekly team meeting at the office, 45 minutes, no cost, a bit tiring but useful.'
  },
  {
    label: 'Groceries',
    icon: cartOutline,
    text: 'Grocery shopping outside after work, 35 minutes, spent €48, nothing special.'
  }
];

/* ---------- Validation ---------- */
const hasErrors = computed(() =>
    !activity.name.trim() ||
    !activity.durationMins ||
    activity.durationMins <= 0 ||
    activity.durationMins > 1440 ||
    !activity.activityTypeId ||
    !activity.recurrence
);

const errorClass = (field: keyof typeof activity) => ({
  'ion-invalid':
      (field === 'name' && !activity.name.trim()) ||
      (field === 'durationMins' && (
          !activity.durationMins ||
          activity.durationMins <= 0 ||
          activity.durationMins > 1440
      )) ||
      (field === 'activityTypeId' && !activity.activityTypeId) ||
      (field === 'recurrence' && !activity.recurrence)
});

/** Evidenziazione temporanea dei campi toccati dall'AI. */
const aiClass = (field: keyof typeof activity) => ({
  'ai-filled': aiFilledFields.value.includes(field)
});

const adjustPleasantness = (d: number) => {
  const v = activity.pleasantness + d;
  if (v >= -3 && v <= 3) activity.pleasantness = v;
};

/* ---------- AI modal ---------- */
const openAIModal = () => {
  aiNotice.value = null;
  isModalOpen.value = true;
};

const closeAIModal = () => {
  isModalOpen.value = false;
  // Il focus torna al punto d'ingresso: nessuna trappola di focus per chi
  // naviga da tastiera o con screen reader.
  nextTick(() => aiEntryButton.value?.focus());
};

/** Focus sulla textarea appena la modale è presentata. */
async function focusPrompt() {
  await nextTick();
  try {
    await promptField.value?.$el?.setFocus?.();
  } catch {
    /* setFocus non disponibile (test/jsdom): ininfluente */
  }
}

function useExample(text: string) {
  naturalInput.value = text;
  aiNotice.value = null;
  focusPrompt();
}

function dismissAiReview() {
  showAiReview.value = false;
  aiFilledFields.value = [];
}

/* ---------- Normalizzazione della risposta LLM ----------
   Il backend risponde SEMPRE 200: in caso di errore o di prompt non
   interpretabile ritorna un oggetto con tutti i campi null. Va quindi
   riconosciuta l'estrazione vuota, e i valori vanno riportati nel dominio
   ammesso dai controlli della form, altrimenti l'utente vedrebbe campi
   "non compilati" senza capirne il motivo. */

const LOCATION_SYNONYMS: Record<string, 'home' | 'work' | 'outside' | 'other'> = {
  home: 'home', house: 'home', 'at home': 'home',
  work: 'work', office: 'work', 'at work': 'work',
  outside: 'outside', outdoor: 'outside', outdoors: 'outside', out: 'outside',
  other: 'other'
};

function normalizeLocation(raw: unknown): 'home' | 'work' | 'outside' | 'other' | null {
  if (typeof raw !== 'string' || !raw.trim()) return null;
  return LOCATION_SYNONYMS[raw.trim().toLowerCase()] ?? 'other';
}

function normalizeCost(raw: unknown): string | null {
  if (raw === null || raw === undefined) return null;
  const cleaned = String(raw).replace(',', '.').replace(/[^0-9.]/g, '');
  return cleaned ? cleaned : null;
}

/**
 * Applica alla form solo i valori effettivamente estratti: un campo assente
 * nella risposta NON azzera quello che l'utente ha già scritto.
 * Ritorna l'elenco dei campi toccati.
 */
function applyExtraction(data: any): string[] {
  if (!data || typeof data !== 'object') return [];
  const filled: string[] = [];

  const name = typeof data.name === 'string' ? data.name.trim() : '';
  if (name) { activity.name = name; filled.push('name'); }

  const duration = Number(data.durationMins ?? data.duration);
  if (Number.isFinite(duration) && duration > 0) {
    activity.durationMins = Math.round(duration);
    filled.push('durationMins');
  }

  const details = typeof data.details === 'string' ? data.details.trim() : '';
  if (details) { activity.details = details; filled.push('details'); }

  const pleasantness = Number(data.pleasantness);
  if (data.pleasantness !== null && data.pleasantness !== undefined && Number.isFinite(pleasantness)) {
    activity.pleasantness = Math.max(-3, Math.min(3, Math.round(pleasantness)));
    filled.push('pleasantness');
  }

  const typeId = Number(data.activityTypeId);
  if (Number.isFinite(typeId) && typeId > 0) {
    activity.activityTypeId = typeId;
    filled.push('activityTypeId');
  }

  const recurrence = typeof data.recurrence === 'string' ? data.recurrence.trim().toUpperCase() : '';
  if (recurrence === 'R' || recurrence === 'E') {
    activity.recurrence = recurrence;
    filled.push('recurrence');
  }

  const cost = normalizeCost(data.costEuro);
  if (cost) { activity.costEuro = cost; filled.push('costEuro'); }

  const location = normalizeLocation(data.location);
  if (location) { activity.location = location; filled.push('location'); }

  return filled;
}

function markAiFilled(fields: string[]) {
  if (highlightTimer) clearTimeout(highlightTimer);
  aiFilledFields.value = fields;
  showAiReview.value = true;
  // L'evidenziazione è un richiamo, non uno stato permanente: sparisce da
  // sola, il banner resta finché l'utente non lo chiude o salva.
  highlightTimer = setTimeout(() => { aiFilledFields.value = []; }, 8000);
}

/** Micro-feedback tattile su Android; sul web è un no-op silenzioso. */
async function hapticFeedback() {
  try {
    const { Haptics, ImpactStyle } = await import('@capacitor/haptics');
    await Haptics.impact({ style: ImpactStyle.Light });
  } catch {
    /* plugin non disponibile: ininfluente */
  }
}

async function handleMagicInput() {
  const prompt = naturalInput.value.trim();
  if (!prompt || isExtracting.value) return;

  isExtracting.value = true;
  aiNotice.value = null;

  try {
    const { data } = await api.post('/api/llm/prompt', { prompt });
    const filled = applyExtraction(data);

    if (filled.length === 0) {
      // Estrazione vuota: la modale resta aperta col testo dell'utente, che
      // può correggerlo invece di ripartire da zero.
      //
      // Il testo NON dà per scontato che la colpa sia della frase: oggi il
      // backend restituisce 200 con tutti i campi nulli anche quando la
      // chiamata al provider fallisce (chiave assente, modello non
      // autorizzato), quindi da qui i due casi sono indistinguibili. Vedi la
      // nota per il backend: finché `LlmService` non segnala il guasto con uno
      // stato d'errore, un'indisponibilità del servizio arriva all'utente come
      // "non ho capito la tua frase".
      aiNotice.value = {
        tone: 'warning',
        text: 'We couldn\'t get any activity details out of that text. Try saying what you did, '
            + 'how long it took, what it cost and how you felt — if it keeps happening the '
            + 'assistant may be unavailable and you can fill the form yourself.'
      };
      return;
    }

    markAiFilled(filled);
    naturalInput.value = '';
    isModalOpen.value = false;
    void hapticFeedback();

    showToast(
        hasErrors.value
            ? 'AI filled some fields — complete the rest and save'
            : 'Fields filled by AI — check them and save',
        hasErrors.value ? 'warning' : 'success'
    );
  } catch (err: any) {
    aiNotice.value = {
      tone: 'error',
      text: err.response?.data?.message
          || err.response?.data?.error
          || 'The AI assistant is unavailable right now. Try again, or close this and fill the form yourself.'
    };
  } finally {
    isExtracting.value = false;
  }
}

/* ---------- Save ---------- */
const isEdit = computed(() => !!route.query.id);

async function saveActivity() {
  if (hasErrors.value) {
    showToast('Please fill required fields correctly', 'danger');
    return;
  }

  isLoading.value = true;

  try {
    const payload = {
      activityId: isEdit.value ? Number(route.query.id) : undefined,
      ...activity
    };

    const endpoint = isEdit.value
        ? '/api/activities/update'
        : '/api/activities/create';

    const { data } = await api.post(endpoint, payload);

    if (!data.success) throw new Error(data.message || 'Failed to save');

    showToast('Activity saved successfully!', 'success');
    dismissAiReview();

    const activityStore = useActivityStore();
    activityStore.needsRefresh = true;

    setTimeout(() => router.push('/home'), 1500);

  } catch (err: any) {
    const message = err.response?.data?.message || err.message || 'Save failed';
    showToast(message, 'danger');
  } finally {
    isLoading.value = false;
  }
}

/* ---------- Lifecycle ---------- */
onMounted(async () => {
  try {
    const res = await api.post('/api/activities/types');
    const data = res.data;
    activityTypes.value = Array.isArray(data) ? data : data.data || [];
  } catch (err) {
    console.error('Failed to fetch activity types:', err);
    showToast('Failed to load activity types', 'danger');
  }
});

onMounted(() => {
  const q = route.query;
  if (q.id) {
    activity.name = (q.name as string) || '';
    activity.durationMins = q.durationMins ? Number(q.durationMins) : null;
    activity.details = (q.details as string) || '';
    activity.pleasantness = q.pleasantness ? Number(q.pleasantness) : 0;
    activity.activityTypeId = q.activityTypeId ? Number(q.activityTypeId) : null;
    activity.recurrence = (q.recurrence as 'R' | 'E') || '';
    activity.costEuro = (q.costEuro as string) || '';
    activity.location = (q.location as 'home' | 'work' | 'outside' | 'other') || '';
  }
});

onUnmounted(() => {
  if (highlightTimer) clearTimeout(highlightTimer);
});
</script>


<style scoped>
/* Header */
.title-centered{display:flex;align-items:center;justify-content:center;gap:var(--space-1)}
.title-icon{font-size:1.1rem;color:var(--peach)}
.date-note{font-size:var(--font-sm);color:var(--subtext0);padding-inline-end:var(--space-2)}

/* Inputs / selects */
ion-input,ion-textarea,ion-select{--highlight-color-focused:var(--peach);--border-color:var(--surface2);--border-radius:var(--radius-md)}
ion-item.item-has-focus{--border-color:var(--peach)}
ion-select::part(placeholder){color:var(--overlay1)}

/* Separators */
ion-item{--inner-border-width:0;position:relative;padding-bottom:6px}
ion-item::after{
  content:'';position:absolute;left:0;right:0;bottom:0;height:1px;
  background:color-mix(in srgb,var(--mauve) 30%,transparent);opacity:.9
}
ion-item.item-has-focus::after{background:var(--peach);opacity:1}

/* ╭──────────────────────────────────────────────╮
   │ AI entry point                                │
   ╰──────────────────────────────────────────────╯ */
.ai-assist{margin-bottom:var(--space-5)}

.ai-assist-card{
  display:flex;align-items:center;gap:var(--space-3);
  width:100%;min-height:var(--touch-target);
  padding:var(--space-3) var(--space-4);
  text-align:start;cursor:pointer;
  border:1px solid color-mix(in srgb,var(--peach) 35%,var(--glass-border));
  border-radius:var(--radius-lg);
  background:linear-gradient(
      120deg,
      color-mix(in srgb,var(--peach) 12%,transparent),
      color-mix(in srgb,var(--mauve) 14%,transparent)
  );
  backdrop-filter:blur(var(--glass-blur));
  -webkit-backdrop-filter:blur(var(--glass-blur));
  box-shadow:var(--shadow-sm);
  transition:box-shadow .2s ease,transform .2s ease;
}
.ai-assist-card:hover{box-shadow:var(--shadow-glow)}
.ai-assist-card:active{transform:scale(.99)}
.ai-assist-card:focus-visible{outline:2px solid var(--peach);outline-offset:2px}

.ai-assist-icon{
  display:flex;align-items:center;justify-content:center;flex:0 0 auto;
  width:40px;height:40px;border-radius:50%;
  background:var(--gradient-peach-mauve);color:var(--crust);
}
.ai-assist-icon ion-icon{font-size:20px}

.ai-assist-copy{display:flex;flex-direction:column;gap:2px;min-width:0}
.ai-assist-title{font-size:var(--font-md);font-weight:var(--font-weight-semibold);color:var(--text)}
.ai-assist-sub{font-size:var(--font-sm);line-height:1.35;color:var(--subtext0)}
.ai-assist-chevron{flex:0 0 auto;font-size:18px;color:var(--peach)}

/* Promemoria di revisione post-compilazione */
.ai-review{
  display:flex;align-items:center;gap:var(--space-2);
  margin-top:var(--space-3);padding:var(--space-2) var(--space-3);
  border:1px solid color-mix(in srgb,var(--mauve) 45%,transparent);
  border-radius:var(--radius-md);
  background:color-mix(in srgb,var(--mauve) 12%,transparent);
}
.ai-review-icon{flex:0 0 auto;font-size:16px;color:var(--mauve)}
.ai-review-text{flex:1;font-size:var(--font-sm);line-height:1.35;color:var(--text)}
.ai-review-close{
  display:flex;align-items:center;justify-content:center;flex:0 0 auto;
  background:transparent;border:0;cursor:pointer;color:var(--subtext0);
  border-radius:var(--radius-md);
}
.ai-review-close ion-icon{font-size:18px}
.ai-review-close:focus-visible{outline:2px solid var(--peach);outline-offset:2px}

/* Campi appena compilati dall'AI */
ion-item.glass-input.ai-filled{
  border-color:var(--mauve);
  box-shadow:var(--shadow-glow);
  animation:ai-fill-in .5s ease-out;
}
@keyframes ai-fill-in{
  from{background:color-mix(in srgb,var(--mauve) 22%,transparent)}
  to{background:transparent}
}
@media (prefers-reduced-motion:reduce){
  ion-item.glass-input.ai-filled{animation:none}
  .ai-assist-card{transition:none}
}

/* ╭──────────────────────────────────────────────╮
   │ AI modal                                      │
   ╰──────────────────────────────────────────────╯ */
.ai-modal{--border-radius:var(--radius-lg);--max-width:560px}

.ai-intro{
  margin:0 0 var(--space-4);
  font-size:var(--font-base);line-height:1.45;color:var(--subtext1)
}

.ai-examples{margin-top:var(--space-2)}
.ai-examples-label{
  display:block;margin-bottom:var(--space-2);
  font-size:var(--font-xs);letter-spacing:.04em;text-transform:uppercase;
  color:var(--subtext0)
}
.ai-examples-list{display:flex;flex-wrap:wrap;gap:var(--space-2)}
.ai-example{
  display:inline-flex;align-items:center;gap:var(--space-1);
  min-height:36px;padding:var(--space-2) var(--space-3);
  font-size:var(--font-sm);font-weight:var(--font-weight-medium);
  color:var(--text);cursor:pointer;
  background:var(--glass-bg);
  border:1px solid var(--glass-border);
  border-radius:var(--radius-pill);
}
.ai-example ion-icon{font-size:16px;color:var(--peach)}
.ai-example:hover{border-color:var(--peach);color:var(--peach)}
.ai-example:focus-visible{outline:2px solid var(--peach);outline-offset:2px}

.ai-hint{
  margin:var(--space-4) 0 0;
  font-size:var(--font-sm);line-height:1.4;color:var(--subtext0)
}

.ai-alert{
  display:flex;align-items:flex-start;gap:var(--space-2);
  margin-top:var(--space-4);padding:var(--space-3);
  border-radius:var(--radius-md);
  font-size:var(--font-sm);line-height:1.4;
}
.ai-alert ion-icon{flex:0 0 auto;font-size:18px;margin-top:1px}
.ai-alert--error{
  color:var(--red);
  border:1px solid color-mix(in srgb,var(--red) 45%,transparent);
  background:color-mix(in srgb,var(--red) 10%,transparent);
}
.ai-alert--warning{
  color:var(--yellow);
  border:1px solid color-mix(in srgb,var(--yellow) 45%,transparent);
  background:color-mix(in srgb,var(--yellow) 10%,transparent);
}

.ai-footer ion-toolbar{--background:transparent;--border-width:0}
.ai-actions{
  display:flex;align-items:center;justify-content:flex-end;gap:var(--space-2);
  padding:var(--space-2) var(--space-4) calc(var(--space-2) + env(safe-area-inset-bottom));
}

/* Pleasantness */
.pleasantness-container{
  display:flex;justify-content:space-between;align-items:center;
  width:100%;padding:var(--space-2) 0
}
.stepper-wrapper{display:flex;align-items:center;gap:var(--space-1)}
.stepper-value{
  min-width:28px;text-align:center;
  font-weight:var(--font-weight-bold);font-size:1.1rem
}
.positive{color:var(--ion-color-success)}
.negative{color:var(--ion-color-danger)}

/* Safe scroll padding (il FAB non c'è più: basta lo spazio di sicurezza) */
.content-safe{padding-bottom:calc(var(--space-6) + env(safe-area-inset-bottom))}
</style>
