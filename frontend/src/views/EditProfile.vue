<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <ion-buttons slot="start">
          <ion-button @click="goBack">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" />
          </ion-button>
        </ion-buttons>
        <ion-title>Edit Profile</ion-title>
        <ion-buttons slot="end">
          <ion-icon :icon="personCircleOutline" class="profile-icon" />
        </ion-buttons>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <ion-list lines="none">
        <ion-item class="glass-input">
          <ion-input label="Name" label-placement="floating" autocomplete="given-name" type="text" v-model="name" />
        </ion-item>
        <ion-item class="glass-input">
          <ion-input label="Surname" label-placement="floating" autocomplete="family-name" type="text" v-model="surname" />
        </ion-item>
        <ion-item class="glass-input">
          <ion-input label="Phone" label-placement="floating" autocomplete="tel" type="tel" v-model="phone" />
        </ion-item>
        <ion-item class="glass-input">
          <ion-input label="Email" label-placement="floating" autocomplete="email" type="email" v-model="email" />
        </ion-item>
        <ion-item class="glass-input">
          <ion-input label="Password" label-placement="floating" type="password" v-model="password" />
        </ion-item>
        <ion-item
            class="glass-input"
            :class="{ 'item-has-value': !!selectedBirthday }"
            @click="openBirthdayModal"
            :detail="false"
            button
            aria-haspopup="dialog"
            :aria-expanded="isBirthdayOpen"
            :aria-label="birthdayAriaLabel"
        >
          <ion-label position="floating">Birthday</ion-label>
          <div class="custom-input-value">{{ formattedBirthday }}</div>
        </ion-item>
        <ion-item class="glass-input">
          <ion-select
              label="Gender"
              label-placement="floating"
              interface="popover"
              v-model="gender"
              :interface-options="{ cssClass: 'ion-dark catppuccin-select-overlay' }"
          >
            <ion-select-option value="male">Male</ion-select-option>
            <ion-select-option value="female">Female</ion-select-option>
            <ion-select-option value="other">Other</ion-select-option>
            <ion-select-option value="not_specified">Prefer not to say</ion-select-option>
          </ion-select>
        </ion-item>
      </ion-list>

      <ion-button expand="block" class="ion-margin-top">Save Changes</ion-button>

    </ion-content>

    <!--
      La modale sta FUORI da `ion-content` (vedi RegistrationPage.vue): dentro
      lo scroller lasciava il proprio `ion-backdrop` nel DOM del form e
      dipendeva dal contesto di posizionamento del contenuto.

      Stessa correzione di RegistrationPage.vue anche sul modo di aprirla:
      modale dichiarativa (`:is-open`) invece di `$el.present()/dismiss()`. Con
      la variante imperativa il primo tap veniva perso (custom element non
      ancora idratato) e la `dismiss()` chiamata dentro `ionChange` cadeva
      durante l'animazione di apertura, lasciando l'overlay smontato a metà
      (`show-modal` senza `overlay-hidden`, focus bloccato sulla modale):
      da lì in poi nessun campo della pagina era più editabile.
    -->
    <ion-modal
        class="birthday-modal"
        :is-open="isBirthdayOpen"
        :keep-contents-mounted="true"
        @didDismiss="isBirthdayOpen = false"
    >
      <!--
        `prefer-wheel` + `show-default-buttons`: si scorre l'anno fino al 1900
        senza uscire dalla dialog e il valore viene confermato SOLO con "Done".
        Prima ogni cambio di mese/anno emetteva `ionChange` e chiudeva tutto.
      -->
      <ion-datetime
          id="birthday-datetime"
          presentation="date"
          prefer-wheel
          :value="pickerValue"
          :min="MIN_BIRTHDAY"
          :max="MAX_BIRTHDAY"
          :show-default-buttons="true"
          done-text="Done"
          cancel-text="Cancel"
          aria-label="Date of birth"
          class="ion-dark"
          @ionChange="onBirthdaySelected"
          @ionCancel="isBirthdayOpen = false"
      />
    </ion-modal>
  </ion-page>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue';
import {
  IonPage, IonHeader, IonToolbar, IonTitle, IonContent, IonButtons, IonButton, IonIcon,
  IonItem, IonLabel, IonInput, IonSelect, IonSelectOption, IonDatetime, IonModal
} from '@ionic/vue';
import { useRouter } from 'vue-router';
import { personCircleOutline, arrowBackOutline } from 'ionicons/icons';
import dayjs from 'dayjs';

/** Intervallo ragionevole per una data di nascita: nessuna data futura. */
const MIN_BIRTHDAY = '1900-01-01';
const MAX_BIRTHDAY = dayjs().format('YYYY-MM-DD');
/** Anno di partenza della ruota quando il campo è ancora vuoto. */
const DEFAULT_BIRTHDAY = '2000-01-01';

const router = useRouter();

const name = ref('');
const surname = ref('');
const phone = ref('');
const email = ref('');
const password = ref('');
const gender = ref('');
const isBirthdayOpen = ref(false);
/** Valore confermato (vuoto finché l'utente non preme "Done"). */
const selectedBirthday = ref<string>();
/** Valore su cui è posizionata la ruota: non deve riempire il campo. */
const pickerValue = ref<string>(DEFAULT_BIRTHDAY);

const openBirthdayModal = () => {
  isBirthdayOpen.value = true;
};

/**
 * Con `show-default-buttons` `ionChange` scatta SOLO alla conferma ("Done"),
 * quindi qui la chiusura è voluta. Il valore si legge dall'evento e non dal
 * `v-model`: l'ordine dei due handler sullo stesso `ionChange` non è garantito.
 */
const onBirthdaySelected = (ev: CustomEvent<{ value?: string | string[] | null }>) => {
  const value = ev.detail?.value;
  const iso = Array.isArray(value) ? value[0] : value;

  if (iso) {
    pickerValue.value = iso;
    selectedBirthday.value = iso;
  }
  isBirthdayOpen.value = false;
};

const formattedBirthday = computed(() => {
  if (!selectedBirthday.value) return '';
  return new Date(selectedBirthday.value).toLocaleDateString('en-GB', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric'
  });
});

const birthdayAriaLabel = computed(() =>
    formattedBirthday.value ? `Birthday, ${formattedBirthday.value}` : 'Birthday, not set'
);

const goBack = () => {
  router.push({ name: 'Settings' });
};
</script>

<style scoped>
.profile-icon {
  font-size: var(--font-xl);
  color: var(--mauve);
}
ion-item.glass-input {
  --inner-padding-top: var(--space-2);
  --inner-padding-bottom: var(--space-2);
}
.custom-input-value {
  width: 100%;
  text-align: start;
  font-size: inherit;
  color: var(--ion-text-color);
  padding-top: var(--space-2);
  padding-bottom: var(--space-2);
  min-height: calc(1em + 16px);
}
/* La modale della data si adatta al picker: prima era un pannello quasi vuoto
   a tutta pagina con la ruota schiacciata in un angolo. */
ion-modal.birthday-modal {
  --width: fit-content;
  --min-width: 290px;
  --height: fit-content;
  --border-radius: var(--radius-lg, 16px);
  --box-shadow: 0 24px 48px rgba(0, 0, 0, 0.45);
}
ion-modal.birthday-modal ion-datetime {
  height: auto;
}
</style>
