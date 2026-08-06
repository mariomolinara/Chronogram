# Sviluppo Android di Chronogram con IntelliJ IDEA Ultimate

Guida per Windows 11, aggiornata al 6 agosto 2026. Riflette lo stato **reale**
della macchina di sviluppo dopo il setup automatico: quasi tutto è già
installato e configurato; i passi manuali sono solo quelli che richiedono la
GUI di IntelliJ.

---

## 1. Cosa è GIÀ installato e configurato (niente da fare)

| Componente | Dove / valore |
|---|---|
| JDK 21 (Amazon Corretto) | `C:\Users\mmoli\.jdks\corretto-21.0.10` (`JAVA_HOME` impostata) |
| Node.js 22 | requisito di Capacitor 8, già presente |
| Android SDK | `C:\Users\mmoli\AppData\Local\Android\Sdk` |
| — cmdline-tools 22.0 | `...\Sdk\cmdline-tools\latest\bin\sdkmanager.bat` |
| — platform android-36 (Android 16) | `...\Sdk\platforms\android-36` |
| — build-tools 36.0.0, platform-tools 37, emulator 37 | installati, licenze accettate |
| — system image Android 16 (google_apis, x86_64) | per l'emulatore |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | impostate a livello utente → **riaprire i terminali/IDE aperti prima del setup** |
| AVD `Pixel_A16` | Pixel 7 + Android 16, in `C:\Users\mmoli\.android\avd` |
| Progetto Android | Capacitor 8.5, AGP 8.13.0, Gradle wrapper 8.14.3, compile/targetSdk 36, minSdk 24, package `it.unicas.aidalab.chronogram` |

L'unica cosa da scaricare a mano è (eventualmente) l'aggiornamento di IntelliJ.

## 2. IntelliJ IDEA Ultimate: versione e plugin

1. **Aggiorna IntelliJ all'ultima versione** (Help → Check for Updates, o
   [JetBrains Toolbox](https://www.jetbrains.com/toolbox-app/)). Serve una
   versione 2025.2 o successiva: il supporto ad AGP 8.13 nel plugin Android di
   JetBrains segue le release recenti; con l'ultima versione non ci sono
   sorprese.
2. **Attiva il plugin Android** (è incluso in Ultimate, non va scaricato):
   `File → Settings → Plugins → Installed` → cerca **Android** → spunta →
   riavvia. Insieme a lui si attivano Android Design Tools / Device Manager.
3. Utili ma opzionali: il plugin **Vue.js** (per la parte web, di solito già
   attivo in Ultimate) e **Ionic** non serve: il lavoro Ionic è normale codice
   Vue/TypeScript.

## 3. Configurare l'SDK Android nell'IDE

1. `File → Project Structure → Platform Settings → SDKs` → `+` → **Android SDK**
   → seleziona `C:\Users\mmoli\AppData\Local\Android\Sdk`.
2. Come "Build target" scegli **API 36** (Android 16). Java SDK associato:
   Corretto 21.
3. Se l'IDE chiede di indicare l'SDK in un altro punto:
   `Settings → Languages & Frameworks → Android SDK` → stesso percorso.

## 4. Aprire il progetto

Il repo contiene tre "progetti" logici: backend Maven, frontend web
(npm/Vite) e progetto Android Gradle. Con IntelliJ conviene così:

1. **Finestra principale**: apri la cartella del repo
   (`...\Chronogram_SergioNistico\Chronogram`) come fai già per backend e
   frontend web.
2. **Progetto Android**: nella stessa finestra, apri il pannello **Gradle**
   (barra laterale destra) → `+` (Link Gradle Project) → seleziona
   `frontend\android\build.gradle`. IntelliJ importa il progetto Gradle e
   compaiono i moduli `app`, `capacitor-android`, `capacitor-cordova-android-plugins`
   e i moduli dei plugin.
   - In alternativa (più leggera): `File → Open` su `frontend\android` in una
     **finestra separata**, da usare solo quando lavori sul nativo.
3. **Gradle JVM**: `Settings → Build, Execution, Deployment → Build Tools →
   Gradle` → *Gradle JVM* = **Corretto 21** (o `JAVA_HOME`). *Use Gradle from*:
   `gradle-wrapper.properties` (default — il wrapper 8.14.3 è quello giusto,
   non sostituirlo con un Gradle locale).
4. Alla prima sincronizzazione Gradle scarica le dipendenze: qualche minuto.

## 5. Emulatore e dispositivo fisico

- **Emulatore**: `Tools → Device Manager` (o l'icona del telefono nella
  toolbar) → l'AVD **Pixel_A16** è già pronto → ▶ per avviarlo.
  Il primo avvio è lento (cold boot); i successivi usano lo snapshot.
- **Dispositivo fisico** (consigliato per il collaudo vero): sul telefono
  attiva *Opzioni sviluppatore → Debug USB*, collega via USB e accetta il
  prompt. Verifica con `adb devices` (adb è in
  `%ANDROID_HOME%\platform-tools`, ora nel PATH dei nuovi terminali).

## 6. Il ciclo di sviluppo (importante)

L'app Android è una WebView che impacchetta la build **web** di produzione.
Il codice Vue NON si ricarica da solo nell'APK: va ricostruito e sincronizzato.

**Dopo ogni modifica al codice Vue/TS che vuoi vedere nell'app Android:**

```powershell
cd C:\Users\mmoli\Desktop\Ricerca\AIDALab\Chronogram_SergioNistico\Chronogram\frontend
npm run build          # build web di produzione (base "/", API → devaidalab)
npx cap sync android   # copia dist/ nel progetto Android e allinea i plugin
```

poi Run/Debug da IntelliJ (o `.\gradlew.bat installDebug` da `frontend\android`).

- **NON usare `npm run build:web` per l'app**: quella build ha base
  `/chronogram/` (per il WAR) e nell'APK produce schermo bianco.
- Per lo sviluppo web quotidiano continua con `npm run dev` nel browser: è il
  ciclo rapido. L'APK serve per collaudare il comportamento nativo.
- Comodo: in IntelliJ puoi creare una run configuration **npm** per `build` e
  una **Shell Script** per `npx cap sync android`, oppure una sola
  configurazione che le concatena, da lanciare prima della run Android.

## 7. Eseguire e debuggare l'app da IntelliJ

1. `Run → Edit Configurations` → `+` → **Android App** → *Module*: **app**.
2. Seleziona il device (AVD Pixel_A16 o il telefono) e premi ▶ / 🐞.
3. **Logcat**: pannello dedicato (View → Tool Windows → Logcat) — filtra per
   `it.unicas.aidalab.chronogram` o per tag `Capacitor`.
4. **Debug del codice web dentro l'app**: con l'app in esecuzione (build
   debug), apri **Chrome** → `chrome://inspect` → sotto *Remote Target* compare
   la WebView di Chronogram → *inspect* apre i DevTools completi (console,
   network, sorgenti TS). È lo strumento principale per il 90% dei problemi,
   che sono lato web.

## 8. Produrre l'APK

### APK di debug (installabile subito, per test)

```powershell
cd C:\Users\mmoli\Desktop\Ricerca\AIDALab\Chronogram_SergioNistico\Chronogram\frontend
npm run build ; npx cap sync android
cd android
.\gradlew.bat assembleDebug
```

Output: `frontend\android\app\build\outputs\apk\debug\app-debug.apk` (~8 MB).
Installazione diretta: `adb install -r app\build\outputs\apk\debug\app-debug.apk`.

L'APK di debug è firmato con la debug key automatica: va bene per qualsiasi
test su emulatore e device propri, non per la distribuzione.

### APK/AAB di release (quando servirà distribuire)

Serve prima il keystore (una volta sola — vedi `frontend\android\RELEASE.md` e
la §3 di `docs\PIANO_PLAY_STORE.md`):

```powershell
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore "C:\Users\mmoli\keys\chronogram-upload.p12" `
  -alias chronogram-upload -keyalg RSA -keysize 4096 -validity 10000 `
  -storetype PKCS12
```

poi `frontend\android\keystore.properties` (da `keystore.properties.example`,
git-ignored) e:

```powershell
.\gradlew.bat assembleRelease   # APK firmato per distribuzione diretta
.\gradlew.bat bundleRelease     # AAB per il Play Store (in futuro)
```

Senza keystore la release build produce un APK **non firmato** (non
installabile): è il fallback voluto, non un errore.

## 9. Verifica finale del setup (5 minuti)

1. Nuovo terminale: `sdkmanager --list_installed` → devono comparire
   platform-tools, platforms;android-36, build-tools;36.0.0, emulator,
   system-images android-36.
2. `adb --version` → 37.x.
3. IntelliJ: sync Gradle di `frontend\android` senza errori.
4. Avvia Pixel_A16 dal Device Manager, Run ▶ dell'app → l'app si apre e il
   login parla con `https://devaidalab.unicas.it/chronogram`.
5. `chrome://inspect` vede la WebView.

## 10. Problemi noti / avvertenze

- **`SDK location not found`**: il terminale/IDE era aperto prima del setup
  delle variabili d'ambiente → riavvialo. In ogni caso
  `frontend\android\local.properties` contiene già `sdk.dir` corretto.
- **L'app installata col vecchio package** (`it.unicas.chronogram`) non viene
  aggiornata dal nuovo APK: è un'altra identità. Disinstallala dai device di
  test.
- **Emulatore lento al primo avvio**: normale (cold boot + hypervisor). Se
  l'emulatore non parte, verifica che la virtualizzazione sia attiva
  (Windows: "Piattaforma Windows Hypervisor" nelle funzionalità opzionali).
- **Warning Gradle "Deprecated features ... incompatible with Gradle 9.0"**:
  atteso, viene da AGP/plugin; Gradle 9 non è un'opzione finché Capacitor non
  supporta AGP 9. Non toccare il wrapper.
- **Aggiornamenti futuri della toolchain**: la coppia da rispettare è quella
  documentata da Capacitor (oggi: AGP 8.13 + Gradle 8.14.3). Non accettare le
  proposte automatiche dell'IDE di "upgrade AGP" oltre la versione supportata
  da Capacitor.
