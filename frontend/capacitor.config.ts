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
    }
    // NOTE: no SplashScreen config here because the @capacitor/splash-screen
    // plugin is NOT installed. The launch screen is handled natively by the
    // AppTheme.NoActionBarLaunch theme + generated splash resources. To manage
    // splash timing from JS, add @capacitor/splash-screen and configure it here.
  }
};

export default config;
