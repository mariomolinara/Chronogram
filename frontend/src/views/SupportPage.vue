<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <!-- Go back to Settings -->
        <ion-buttons slot="start">
          <ion-button @click="goTo('Settings')">
            <ion-icon :icon="settingsOutline" slot="icon-only" />
          </ion-button>
        </ion-buttons>
        <ion-title>Support</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">

      <!-- Intro with support explanation -->
      <ion-card class="support-intro">
        <ion-card-header>
          <ion-card-title class="section-title">
            We're here to help you with anything and everything on Chronogram
          </ion-card-title>
        </ion-card-header>
        <ion-card-content>
          <p>
            At Chronogram, we want your experience to get better every day. You can share your concern or check the frequently asked questions below.
          </p>
        </ion-card-content>
      </ion-card>

      <!-- Search bar (non-functional for now) -->
      <ion-item class="search-item">
        <ion-icon :icon="searchOutline" slot="start" color="medium"></ion-icon>
        <ion-input placeholder="Search help" clear-input></ion-input>
      </ion-item>

      <!-- FAQ with accordion -->
      <ion-accordion-group>
        <ion-accordion v-for="(item, index) in faq" :key="index" :value="item.q">
          <ion-item slot="header" color="dark">
            <ion-label>{{ item.q }}</ion-label>
          </ion-item>
          <div class="ion-padding" slot="content">
            {{ item.a }}
          </div>
        </ion-accordion>
      </ion-accordion-group>

      <!-- Message submission area -->
      <ion-card class="message-box">
        <ion-card-header>
          <ion-card-title class="section-title">
            Still stuck? Help is a message away
          </ion-card-title>
        </ion-card-header>
        <ion-card-content>
          <FormLegend />

          <ion-item :class="fieldClass('subject')" data-field="subject">
            <ion-label position="stacked">Subject <RequiredMark /></ion-label>
            <ion-input
                v-model="subject"
                placeholder="Enter subject"
                required
                :maxlength="SUBJECT_MAX_LENGTH"
                :counter="true"
                :aria-invalid="!!errorFor('subject')"
            ></ion-input>
          </ion-item>
          <FieldError :message="errorFor('subject')" />

          <ion-item :class="fieldClass('message')" data-field="message">
            <ion-label position="stacked">Message <RequiredMark /></ion-label>
            <ion-textarea
                v-model="message"
                placeholder="Describe your issue"
                auto-grow
                :maxlength="MESSAGE_MAX_LENGTH"
                :counter="true"
                :aria-invalid="!!errorFor('message')"
            ></ion-textarea>
          </ion-item>
          <FieldError :message="errorFor('message')" />

          <!-- Premibile anche a form incompleto: è la pressione a dire cosa
               manca. Si disabilita solo mentre l'invio è in volo. -->
          <ion-button
              expand="block"
              class="send-button"
              :disabled="isSending"
              @click="sendMessage"
          >
            <ion-spinner v-if="isSending" slot="start" name="crescent" />
            {{ isSending ? 'Sending…' : 'Send Message' }}
          </ion-button>
        </ion-card-content>
      </ion-card>

    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonItem,
  IonInput, IonTextarea, IonButton, IonLabel, IonCard, IonCardContent,
  IonCardHeader, IonCardTitle, IonIcon, IonAccordionGroup, IonAccordion,
  IonButtons, IonSpinner
} from '@ionic/vue';
import { searchOutline, settingsOutline } from 'ionicons/icons';
import { ref } from 'vue';
import { useRouter } from 'vue-router';
import { api, apiErrorMessage } from '@/composables/useApi';
import { useToast } from '@/composables/useToast';
import {
  collectErrors, errorSummary, isBlank, useFormValidation
} from '@/composables/useValidation';
import RequiredMark from '@/components/RequiredMark.vue';
import FieldError from '@/components/FieldError.vue';
import FormLegend from '@/components/FormLegend.vue';

const router = useRouter();
const { showToast } = useToast();

// Redirect to Settings
function goTo(name: string) {
  router.push({ name });
}

/** Limiti di `SupportMessageRequest` lato backend: la UI li rispecchia. */
const SUBJECT_MAX_LENGTH = 150;
const MESSAGE_MAX_LENGTH = 2000;

// Inputs for user message
const subject = ref('');
const message = ref('');
/** Invio in corso: il pulsante non deve poter partire due volte. */
const isSending = ref(false);

// FAQ sample questions and answers
const faq = [
  {
    q: 'How do I add a new activity?',
    a: 'You can add a new activity by going to the Home page and tapping the + button.'
  },
  {
    q: 'Can I edit a past activity?',
    a: 'Yes, open the Calendar, select the date, and tap the activity to edit it.'
  },
  {
    q: 'Is there a way to customize my categories?',
    a: 'Currently categories are predefined, but customization will be available soon.'
  }
];

/* ---------- validazione ---------- */
const REQUIRED_ORDER = ['subject', 'message'] as const;
type RequiredField = (typeof REQUIRED_ORDER)[number];

const { errors, errorFor, fieldClass, validateOnSubmit, reset: resetValidation } =
    useFormValidation<RequiredField>(() => collectErrors<RequiredField>([
      {
        field: 'subject',
        invalid: isBlank(subject.value),
        message: 'Subject is required: summarise the issue in a few words'
      },
      {
        field: 'subject',
        invalid: subject.value.trim().length > SUBJECT_MAX_LENGTH,
        message: `Subject must be ${SUBJECT_MAX_LENGTH} characters or fewer`
      },
      {
        field: 'message',
        invalid: isBlank(message.value),
        message: 'Message is required: describe what happened and what you expected'
      },
      {
        field: 'message',
        invalid: message.value.trim().length > MESSAGE_MAX_LENGTH,
        message: `Message must be ${MESSAGE_MAX_LENGTH} characters or fewer`
      }
    ]), REQUIRED_ORDER);

/**
 * Invia la richiesta di assistenza.
 *
 * I limiti sono già imposti dai `maxlength` dei campi: le regole qui sopra
 * coprono il caso in cui il testo arrivi da un incolla programmatico o da un
 * autofill, dove `maxlength` non tronca sempre.
 */
async function sendMessage() {
  if (!(await validateOnSubmit())) {
    await showToast(errorSummary(errors.value), 'danger');
    return;
  }

  isSending.value = true;
  try {
    await api.post('/api/support/messages', {
      subject: subject.value.trim(),
      message: message.value.trim()
    });

    subject.value = '';
    message.value = '';
    resetValidation();

    await showToast('Your message has been sent!', 'success');
  } catch (err: unknown) {
    // Il backend scrive già un messaggio per l'utente finale: si preferisce
    // quello a qualsiasi testo generico.
    await showToast(
        apiErrorMessage(err, 'Could not send your message. Please try again.'),
        'danger'
    );
  } finally {
    isSending.value = false;
  }
}
</script>

<style scoped>
/* Card base styling */
.support-intro,
.message-box {
  background-color: var(--surface0);
  border-radius: var(--radius-md);
  color: var(--text);
}

/* Title text (highlighted) */
.section-title {
  color: var(--mauve);
  font-size: var(--font-md);
  font-weight: var(--font-weight-bold);
}

/* Paragraph description */
.support-intro p {
  color: var(--subtext0);
  font-size: var(--font-base);
}

/* Search bar styling */
.search-item {
  background: var(--surface0);
  margin-bottom: var(--space-4);
  border-radius: var(--radius-sm);
}

/* FAQ section spacing */
ion-accordion-group {
  margin-top: var(--space-4);
  margin-bottom: var(--space-6);
}

/* Button styled like Chronogram */
.send-button {
  margin-top: var(--space-4);
  --background: var(--gradient-pink-mauve);
  --color: var(--crust);
  font-weight: var(--font-weight-bold);
  --border-radius: var(--radius-md);
}
</style>
