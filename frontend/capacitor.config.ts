import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'it.unicas.aidalab.chronogram',
  appName: 'Chronogram',
  webDir: 'dist',
  // Production hardening: keep the WebView content confined to the app's own
  // scheme and forbid cleartext navigations from the WebView itself.
  android: {
    allowMixedContent: false
  },
  plugins: {
    // StatusBar plugin (@capacitor/status-bar) — installed.
    // `overlaysWebView: false` reserves space for the status bar so the app
    // content is not drawn underneath it (avoids notch/overlap issues).
    StatusBar: {
      overlaysWebView: false,
      style: 'DARK',
      backgroundColor: '#ffffff'
    },
    // Keyboard plugin (@capacitor/keyboard) — installed.
    // `resize: 'native'` lets Android resize the WebView when the keyboard
    // shows, keeping focused inputs visible without a manual scroll fix.
    Keyboard: {
      resize: 'native' as any,
      resizeOnFullScreen: true
    },
    // LocalNotifications plugin (@capacitor/local-notifications) — promemoria
    // periodici schedulati sul device da `useLocalReminders.ts`.
    //
    // `smallIcon` e obbligatorio in pratica: senza, il plugin ricade su
    // `ic_launcher` e da Android 5 il sistema usa solo il canale alpha della
    // smallIcon come maschera, quindi un'icona a colori diventa una silhouette
    // piena — il famigerato quadrato bianco in barra di stato. Il valore e il
    // nome della risorsa drawable senza estensione:
    // android/app/src/main/res/drawable/ic_stat_notify.xml (glifo orologio
    // bianco su trasparente).
    //
    // `iconColor` e la tinta applicata dal sistema all'icona e al nome app nel
    // drawer: mauve Catppuccin Latte, lo stesso `--ion-color-primary` del tema.
    LocalNotifications: {
      smallIcon: 'ic_stat_notify',
      iconColor: '#8839EF'
    }
    // NOTE: no SplashScreen config here because the @capacitor/splash-screen
    // plugin is NOT installed. The launch screen is handled natively by the
    // AppTheme.NoActionBarLaunch theme + generated splash resources. To manage
    // splash timing from JS, add @capacitor/splash-screen and configure it here.
  }
};

export default config;
