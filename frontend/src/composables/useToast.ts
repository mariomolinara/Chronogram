import { toastController } from '@ionic/vue';

/** Colori Ionic ammessi per il feedback: successo, errore, avviso. */
export type ToastColor = 'success' | 'danger' | 'warning';

/** Durata predefinita, allineata a quella già usata dalle viste. */
const DEFAULT_DURATION = 2500;

/**
 * Feedback effimero all'utente.
 *
 * Perché un controller e non `<ion-toast :is-open>`: con la variante
 * dichiarativa la presentazione parte prima che il custom element sia idratato,
 * l'animazione fallisce con `TypeError: Cannot read properties of null (reading
 * 'style')` e il toast NON viene mai mostrato. Il difetto era presente in tutte
 * le viste che davano feedback (login, registrazione, reset password, creazione
 * attività, home): l'utente non vedeva alcun messaggio, né di errore né di
 * conferma. `toastController.create()` attende il componente ed è la stessa via
 * già usata da `alertController` nel resto dell'app.
 */
export function useToast() {
    const showToast = async (
        message: string,
        color: ToastColor = 'danger',
        duration: number = DEFAULT_DURATION
    ): Promise<void> => {
        const toast = await toastController.create({ message, color, duration });
        await toast.present();
    };

    return { showToast };
}
