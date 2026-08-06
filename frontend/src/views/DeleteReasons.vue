<template>
  <ion-page>
    <ion-header>
      <ion-toolbar>
        <!-- Ultima pagina prima di un'azione irreversibile: la via d'uscita deve
             essere visibile, non affidata al gesto BACK di Android. -->
        <ion-buttons slot="start">
          <ion-button aria-label="Back to delete account" @click="goBack">
            <ion-icon slot="icon-only" :icon="arrowBackOutline" />
          </ion-button>
        </ion-buttons>
        <ion-title>Before you go</ion-title>
      </ion-toolbar>
    </ion-header>

    <ion-content class="ion-padding">
      <div class="feedback-wrapper">
        <!-- Copy onesto: aprendo questa pagina non è stato cancellato niente.
             Prima diceva "we're sending an email confirming your account is
             deleted" mentre l'account era ancora intatto. -->
        <p class="description">
          Your account has not been deleted yet. Tell us why you're leaving, then confirm
          below to erase your account for good.
        </p>

        <!-- L'email arriva dallo store auth (è l'identificativo dell'account).
             Se la sessione non la espone, la frase sparisce: un indirizzo
             inventato accanto a un pulsante distruttivo è peggio di nessun
             indirizzo. -->
        <p v-if="accountEmail" class="description">
          You are about to delete the account linked to <strong>{{ accountEmail }}</strong>.
        </p>

        <h3 class="reason-title">Why did you decide to leave this app?</h3>
        <p class="reason-hint">Optional — pick as many as you like.</p>

        <ion-list lines="none">
          <ion-item v-for="(reason, index) in reasons" :key="index" lines="none">
            <ion-checkbox v-model="reason.selected" slot="start" :aria-label="reason.text" />
            <ion-label class="ion-text-wrap">{{ reason.text }}</ion-label>
          </ion-item>
        </ion-list>

        <!-- Si disabilita solo mentre la richiesta è in volo: due tap non devono
             produrre due cancellazioni (la seconda troverebbe un account che non
             esiste più e mostrerebbe un errore a cancellazione riuscita). -->
        <ion-button
            expand="block"
            color="danger"
            class="delete-button"
            :disabled="isDeleting"
            @click="confirmDeletion"
        >
          <ion-spinner v-if="isDeleting" slot="start" name="crescent" />
          {{ isDeleting ? 'Deleting…' : 'Delete my account' }}
        </ion-button>

        <ion-button
            expand="block"
            fill="outline"
            class="cancel-button"
            :disabled="isDeleting"
            @click="keepAccount"
        >
          Keep my account
        </ion-button>
      </div>
    </ion-content>
  </ion-page>
</template>

<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  IonPage, IonHeader, IonToolbar, IonButtons, IonButton, IonIcon, IonTitle,
  IonContent, IonList, IonItem, IonLabel, IonCheckbox, IonSpinner, alertController
} from '@ionic/vue'
import { arrowBackOutline } from 'ionicons/icons'
import { apiErrorMessage } from '@/composables/useApi'
import { useToast } from '@/composables/useToast'
import { deleteAccount } from '@/composables/useProfile'
import { useAuthStore } from '@/store/auth'

const router = useRouter()
const auth = useAuthStore()
const { showToast } = useToast()

/**
 * Email dell'account: nello store `username` è l'indirizzo con cui si è entrati
 * (vedi la nota su `displayName` in `store/auth.ts`). Stringa vuota se manca,
 * così il template può semplicemente omettere la frase.
 */
const accountEmail = computed(() => auth.username?.trim() ?? '')

/** Cancellazione in volo: blocca il doppio invio e cambia l'etichetta. */
const isDeleting = ref(false)

const reasons = ref([
  { text: "I'm not using the app.", selected: false },
  { text: 'I found a better alternative.', selected: false },
  { text: 'The app contains too many ads.', selected: false },
  { text: "The app didn't have the features or functionality I was looking for.", selected: false },
  { text: "I'm not satisfied with the quality of content.", selected: false },
  { text: 'The app was difficult to navigate.', selected: false },
  { text: 'Other.', selected: false }
])

const selectedReasons = computed(() =>
    reasons.value.filter((reason) => reason.selected).map((reason) => reason.text)
)

/**
 * Conferma definitiva prima di chiamare il backend.
 *
 * L'alert non è una formalità: da qui in poi non c'è annullamento, e il pulsante
 * sta nella stessa pagina di sette checkbox innocue, dove un tap può partire per
 * sbaglio. Il testo dice cosa succede, non "Are you sure?".
 *
 * `void performDeletion()` e non un handler `async`: Ionic tiene l'alert aperto
 * finché l'handler non restituisce un valore, e una promise lo lascerebbe
 * presentato sopra alla pagina per tutta la durata della richiesta.
 */
async function confirmDeletion(): Promise<void> {
  const alert = await alertController.create({
    header: 'Delete my account?',
    message: 'This cannot be undone. Your profile, activities and history will be permanently erased.',
    buttons: [
      { text: 'Cancel', role: 'cancel' },
      {
        text: 'Delete',
        role: 'destructive',
        handler: () => {
          void performDeletion()
        }
      }
    ]
  })
  await alert.present()
}

/**
 * Cancella l'account e chiude la sessione.
 *
 * Ordine obbligato: prima la chiamata (serve il token, che l'interceptor prende
 * dallo store), poi il logout. Invertirli lascerebbe la richiesta senza
 * `Authorization` e il backend risponderebbe 401.
 */
async function performDeletion(): Promise<void> {
  if (isDeleting.value) {
    return
  }
  isDeleting.value = true

  try {
    await deleteAccount(selectedReasons.value)

    // `logout()` dello store è l'unico posto che sa da cosa è fatta una sessione
    // (token in memoria, chiavi in `@capacitor/preferences`, stato Pinia) e
    // chiude con un `router.replace` verso il Login: ripulire a mano qui
    // significherebbe dimenticarsi un pezzo alla prossima modifica.
    await auth.logout()

    // Rete di sicurezza: se quel redirect fosse stato annullato da un guard, la
    // pagina resterebbe montata su un account che non esiste più. `replace` e
    // non `push` — non c'è nulla a cui tornare indietro.
    if (router.currentRoute.value.name !== 'Login') {
      await router.replace({ name: 'Login' })
    }

    // Il toast è un overlay del documento, non della vista: sopravvive alla
    // navigazione e si vede sulla pagina di login, dove l'utente atterra.
    await showToast('Your account has been deleted.', 'success')
  } catch (err: unknown) {
    // Fallimento = l'account è ancora là: la sessione NON va chiusa, altrimenti
    // l'utente si ritrova fuori con un account intatto e nessuna spiegazione.
    await showToast(
        apiErrorMessage(err, 'Could not delete your account. Please try again.'),
        'danger'
    )
  } finally {
    isDeleting.value = false
  }
}

/**
 * Torna alla pagina di avvertimento.
 *
 * Per nome e non con `router.back()`: aprendo `/delete-reasons` come deep link
 * (o dopo un reload) la cronologia non contiene la pagina precedente e il gesto
 * porterebbe fuori dall'app invece che indietro nel flusso.
 */
const goBack = () => {
  router.push({ name: 'DeleteAccount' })
}

/** Uscita completa dal flusso: si abbandona la cancellazione, non un passo. */
const keepAccount = () => {
  router.push({ name: 'Settings' })
}
</script>

<style scoped>
.feedback-wrapper {
  display: flex;
  flex-direction: column;
  gap: var(--space-4);
  margin-top: var(--space-6);
  max-width: 450px;
  margin-inline: auto;
}

.description {
  font-size: var(--font-base);
  color: var(--subtext0);
  margin: 0;
}

.reason-title {
  font-weight: var(--font-weight-bold);
  margin-top: var(--space-4);
  margin-bottom: 0;
  font-size: var(--font-md);
}

.reason-hint {
  margin: 0;
  font-size: var(--font-sm);
  color: var(--subtext0);
}

.delete-button {
  margin-top: var(--space-5);
  font-weight: var(--font-weight-bold);
  --border-radius: var(--radius-md);
}

.cancel-button {
  margin-top: var(--space-2);
}
</style>