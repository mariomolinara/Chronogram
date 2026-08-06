# Piano operativo — Pubblicazione di Chronogram sul Google Play Store

**Data del piano: 6 agosto 2026.** Basato sull'ispezione dello stato reale del repo
e sulla verifica dei requisiti Google Play correnti (fonti in fondo).

> **AGGIORNAMENTO — 6 agosto 2026 (sera).** Le voci tecniche più urgenti sono
> state RISOLTE dopo la stesura del piano; le sezioni segnate ✅ qui sotto vanno
> lette come storia, non come lavoro da fare:
>
> - ✅ **applicationId deciso e applicato: `it.unicas.aidalab.chronogram`**
>   (namespace, MainActivity, strings.xml inclusi). La §2.1 è superata.
> - ✅ **compileSdk/targetSdk 36, minSdk 24** — requisito Play del 31/08/2026
>   soddisfatto. Stack aggiornato a **Capacitor 8.5 + AGP 8.13.0 + Gradle
>   8.14.3** (AGP 9 non è supportato da Capacitor). Le §1.2 e §2.5 sono superate.
> - ✅ SDK platform 36, platform-tools, emulator e system image Android 16
>   installati; `ANDROID_HOME`/`ANDROID_SDK_ROOT` impostate; AVD `Pixel_A16`
>   creato. La §1 è completata.
> - ✅ `google-services.json` eliminato (era legato al vecchio package, Firebase
>   non in uso; procedura di riattivazione in `frontend/android/RELEASE.md`).
> - ✅ `RELEASE.md` corretto (§2.7 completata) e APK di debug collaudato con la
>   nuova identità (`aapt2 badging` verificato).
> - Restano attuali: firma/keystore (§3), account Play Console (§5), compliance
>   e listing (§6), testing (§7), rilascio (§8). L'utente ha scelto di fermarsi
>   all'APK per ora e valutare l'account Play Console in futuro.

---

## 0. Stato reale verificato del repo

### 0.1 Configurazione Capacitor

`frontend/capacitor.config.ts`

| Campo | Valore attuale | Giudizio |
|---|---|---|
| `appId` | `it.unicas.chronogram` | Valido; **decisione richiesta prima della prima pubblicazione** (vedi §2.1) |
| `appName` | `Chronogram` | OK (≤30 caratteri per il titolo Play) |
| `webDir` | `dist` | OK, coerente con Vite |
| `android.allowMixedContent` | `false` | OK per produzione |
| `plugins.StatusBar` | `overlaysWebView: false`, `style: 'DARK'`, `backgroundColor: '#ffffff'` | Da riverificare con targetSdk 36 (edge-to-edge forzato, §2.5) |
| `plugins.Keyboard` | `resize: 'native'`, `resizeOnFullScreen: true` | OK |
| `SplashScreen` | assente: `@capacitor/splash-screen` non installato | Accettabile: splash gestito nativamente da `AppTheme.NoActionBarLaunch` |
| `server` | non configurato | Corretto per la produzione (asset locali) |

Nessun blocco `server.url` residuo: buono — un `server.url` dimenticato è la causa
numero uno di app store-ready che mostrano schermo bianco.

### 0.2 Progetto Android

Il progetto Gradle esiste in `frontend/android` (con `gradlew`, `settings.gradle`,
`variables.gradle`, `app/`).

`frontend/android/variables.gradle`:

```
minSdkVersion = 23
compileSdkVersion = 35
targetSdkVersion = 35
androidxCoreVersion = '1.15.0'
coreSplashScreenVersion = '1.0.1'
androidxWebkitVersion = '1.12.1'
```

- Root `build.gradle`: AGP `8.7.2`, `google-services:4.4.2`.
- `gradle-wrapper.properties`: **Gradle 9.3.0**.
- `app/build.gradle`: `namespace`/`applicationId` `it.unicas.chronogram`,
  `versionCode 1`, `versionName "1.0"`.
- `signingConfigs.release` **già implementato** e ben fatto: legge in ordine
  `keystore.properties` → Gradle properties (`-P`) → variabili d'ambiente
  (`CHRONOGRAM_STORE_FILE`, `CHRONOGRAM_STORE_PASSWORD`, `CHRONOGRAM_KEY_ALIAS`,
  `CHRONOGRAM_KEY_PASSWORD`), con fallback a build non firmata se mancano le
  credenziali.
- `minifyEnabled false`, `shrinkResources false`: scelta deliberata e documentata
  (R8 può rimuovere classi dei plugin Capacitor caricate via reflection).
- `keystore.properties.example` esiste; **nessun `keystore.properties` né keystore
  presente** (corretto: git-ignored, ma vanno ancora creati).
- `frontend/android/RELEASE.md` esiste ma contiene **due affermazioni obsolete**:
  1. dice che il manifest ha `usesCleartextTraffic="true"` — falso, risolto con
     `network_security_config`;
  2. dice che target 35 è «conforme, nessun bump necessario» — non più vero dal
     31 agosto 2026 (§2.5).

### 0.3 Manifest e permessi

`frontend/android/app/src/main/AndroidManifest.xml`:

- **Un solo permesso**: `android.permission.INTERNET`. Ottimo per la review.
- `android:networkSecurityConfig="@xml/network_security_config"` presente:
  `base-config cleartextTrafficPermitted="false"` + eccezioni cleartext solo per
  host di sviluppo (`localhost`, `127.0.0.1`, `10.0.2.2`, `10.0.3.2`,
  `192.168.1.100`). Configurazione corretta e già pubblicabile.
- `android:allowBackup="true"` ← **da rivedere** (§2.6): l'app conserva un JWT in
  `@capacitor/preferences`.
- `FileProvider` standard Capacitor, `exported="false"`. Nessun `<queries>`,
  nessun `AD_ID`.

### 0.4 Asset nativi

`frontend/assets/` contiene `icon-foreground.png`, `icon-background.png`,
`splash.png`; le risorse native (`res/mipmap-*`, `drawable-*`) risultano già
generate con `@capacitor/assets`. Mancano l'**icona store 512×512** e la
**feature graphic 1024×500**, che `@capacitor/assets` non produce.

### 0.5 Rete e build web

- `frontend/.env.production`: `VITE_API_BASE_URL=https://devaidalab.unicas.it/chronogram`
  — corretto e già pronto (URL assoluto, necessario perché l'origine della WebView
  è `https://localhost`).
- Script npm: `build` (`vue-tsc && vite build`, base `/`) → **è questa la build per
  Capacitor**; `build:web` (base `/chronogram/`) è per il WAR.

### 0.6 Toolchain sulla macchina (verificata, Windows 11)

| Requisito | Stato reale | Esito |
|---|---|---|
| JDK 21 (richiesto da Capacitor 7) | Corretto 21.0.10, `JAVA_HOME` impostata | OK |
| Node ≥20 | v22.15.0 | OK |
| `ANDROID_HOME` / `ANDROID_SDK_ROOT` | non impostate | Compensato da `local.properties`; da impostare comunque (§1.3) |
| Platform SDK | solo `android-35` | **MANCA `android-36`** (§1.2) |
| Build-tools | 34.0.0, 35.0.0, 35.0.1, 36.0.0 | OK |
| `keytool` su PATH | assente | Usare `& "$env:JAVA_HOME\bin\keytool.exe"` |

### 0.7 Cancellazione account — bloccante di policy

Google richiede un percorso di cancellazione account funzionante (in-app + link
web) e lo verifica. Al momento dell'ispezione l'endpoint backend
`POST /api/profile/delete-account` era in corso di implementazione: **verificare
che sia deployato e funzionante end-to-end prima della submission** (§6.4).

### 0.8 Dati personali raccolti (base per il Data Safety form)

Da `UserProfile.java`: `name`, `surname`, `phone`, `gender`, `birthday`,
`address`, `notes`, `weeklyIncome`, `weeklyIncomeOther`, `weeklyHomeCost`; più
l'email (`UserAuth`) e i dati di attività/diario. **Attenzione**: `weekly_income`
e `weekly_home_cost` sono informazioni finanziarie e vanno dichiarate come tali;
il testo delle attività passa all'integrazione LLM e costituisce **condivisione
con terze parti** (§6.3).

---

## Blocchi critici, in ordine di urgenza

1. ✅ RISOLTO — **targetSdk 36 entro il 31 agosto 2026**: fatto (Capacitor 8 +
   AGP 8.13, compile/targetSdk 36).
2. **Endpoint di cancellazione account** funzionante in produzione (policy User
   Data): endpoint deployato; resta il link web pubblico di richiesta (§6.4).
3. **Privacy policy non pubblicata** su un URL pubblico (esiste solo come file locale).
4. **Nessun keystore di release** generato.
5. ✅ RISOLTO — **`applicationId`**: deciso e applicato `it.unicas.aidalab.chronogram`.
6. **Account developer**: senza account verificato non si carica nulla, ed è la
   voce con il lead time più lungo. Da avviare quando si deciderà di pubblicare.

---

## 1. Prerequisiti macchina (Windows 11)

### 1.1 JDK

Capacitor 7 richiede JDK 21 e Android Studio Ladybug (2024.2.1) o successivo.
Già soddisfatto (Corretto 21.0.10). Nota: per `compileSdk 36` serve Android
Studio ≥ Meerkat (2024.3.1 Patch 1) — non strettamente necessario per la build
da CLI, ma consigliato per l'AVD Android 16.

### 1.2 Android SDK — platform 36 (azione richiesta)

```powershell
$sdk = "C:\Users\mmoli\AppData\Local\Android\Sdk"
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "build-tools;36.0.0" "platform-tools"
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
```

Per il test su Android 16 (necessario per validare edge-to-edge e predictive back):

```powershell
& "$sdk\cmdline-tools\latest\bin\sdkmanager.bat" "system-images;android-36;google_apis;x86_64"
& "$sdk\cmdline-tools\latest\bin\avdmanager.bat" create avd -n Pixel_A16 -k "system-images;android-36;google_apis;x86_64" -d pixel_7
```

### 1.3 Variabili d'ambiente

```powershell
[Environment]::SetEnvironmentVariable("ANDROID_HOME", "C:\Users\mmoli\AppData\Local\Android\Sdk", "User")
[Environment]::SetEnvironmentVariable("ANDROID_SDK_ROOT", "C:\Users\mmoli\AppData\Local\Android\Sdk", "User")
# PATH: aggiungere %ANDROID_HOME%\platform-tools e %ANDROID_HOME%\cmdline-tools\latest\bin
```

Riaprire il terminale dopo la modifica. Diagnostica: `npx cap doctor` dal
`frontend/` (attesi Capacitor CLI/core/android 7.2.0, plugin 7.0.x).

---

## 2. Preparazione dell'app

### 2.1 `applicationId` — DECISIONE IRREVERSIBILE, da prendere subito

L'`applicationId` non è modificabile dopo la prima pubblicazione.

- **Opzione A — mantenere `it.unicas.chronogram`** (raccomandata se Chronogram è
  un'app a sé): reverse-DNS corretto, coerente con il backend, zero lavoro.
- **Opzione B — `it.unicas.aidalab.chronogram`** (raccomandata se AIDA Lab
  prevede altre app): crea un namespace di laboratorio.

Se esiste anche solo l'intenzione di pubblicare una seconda app AIDA Lab,
scegliere B **ora**. Procedura per l'Opzione B (`npx cap sync` NON rinomina il
package Java):

1. `capacitor.config.ts` → `appId`
2. `app/build.gradle` → `namespace` e `applicationId`
3. Spostare `MainActivity.java` nel nuovo package e aggiornare la riga `package`
4. Idem per i sorgenti di test
5. `npx cap sync android` + build pulita
6. Grep finale per riferimenti residui (il `FileProvider` usa `${applicationId}`:
   si adegua da sé)

### 2.2 `versionCode` / `versionName`

Attuali: `1` / `"1.0"` — vanno bene per il primo caricamento. Schema consigliato
dalla seconda release: `versionName` semantico (`1.0.1`) e
`versionCode = MAJOR*10000 + MINOR*100 + PATCH`. Un `versionCode` consumato non è
riutilizzabile mai più, nemmeno se la release viene scartata. Annotare lo schema
scelto in `RELEASE.md`.

### 2.3 Icona e splash definitivi

Risorse native già generate; rigenerare solo se l'artwork cambia:

```powershell
cd frontend
npx @capacitor/assets generate --android
```

Requisiti sorgente in `frontend/assets/`:

- `icon-foreground.png` — 1024×1024, soggetto entro il cerchio di sicurezza
  centrale di ~66% (le icone adaptive vengono ritagliate)
- `icon-background.png` — 1024×1024, tinta piatta o gradiente semplice
- `splash.png` — 2732×2732, logo centrato
- Consigliato `splash-dark.png` (l'app usa un tema scuro: uno splash chiaro
  produce un lampo bianco)

Le risorse generate in `res/` vanno committate. Da preparare a mano per la
scheda Play: icona store 512×512 e feature graphic 1024×500 (partendo da
`docs/Chronogram_Logo.png`).

### 2.4 Review dei permessi

Oggi solo `INTERNET`: situazione ideale, non aggiungere nulla. Dopo il primo
`bundleRelease` verificare i permessi effettivi dell'artefatto (il manifest
merger può iniettarne): ispezionare
`app/build/intermediates/merged_manifests/release/AndroidManifest.xml` o usare
`aapt2 dump`. Se comparisse `com.google.android.gms.permission.AD_ID`, va
dichiarato nel Data Safety form o rimosso con `tools:node="remove"`.

### 2.5 compileSdk / targetSdk 36 — l'intervento tecnico più delicato

**Requisito.** Dal **31 agosto 2026** nuove app e aggiornamenti devono targettare
**Android 16 (API 36)**; proroga richiedibile fino al 1° novembre 2026 dalla
pagina Policy status della Play Console. Considerati i tempi di verifica account
e review, **puntare direttamente ad API 36**.

**Vincolo tecnico.** `compileSdk 36` richiede **AGP ≥ 8.9.1**; il progetto è su
AGP 8.7.2. Inoltre la coppia attuale AGP 8.7.2 + Gradle 9.3.0 è fuori matrice
ufficiale.

**Opzione raccomandata: AGP 8.13 + Gradle 8.13** (coppia in matrice più
conservativa che supporta compileSdk 36):

- root `build.gradle`: `classpath 'com.android.tools.build:gradle:8.13.0'`
- `gradle-wrapper.properties`: `distributionUrl=...gradle-8.13-bin.zip`
- `variables.gradle`: `compileSdkVersion = 36`, `targetSdkVersion = 36`;
  lasciare `minSdkVersion = 23`
- Se la build chiede compileSdk più alto su qualche AndroidX, alzare per errore
  (non a scatola chiusa): `androidxCoreVersion '1.17.0'`,
  `androidxAppCompatVersion '1.7.1'`, `androidxWebkitVersion '1.14.0'`

(AGP 9.x è moderno ma più rischioso: breaking change, template Capacitor 7 non
validato. Da riservare a un secondo momento.)

**Behaviour changes di API 36 da verificare sul dispositivo** — le due cose che
tipicamente rompono un'app Ionic al bump:

1. **Edge-to-edge obbligatorio** su Android 16 con targetSdk 36 (niente opt-out).
   Verificare che il contenuto non finisca sotto status/navigation bar
   (`StatusBar.overlaysWebView: false` interagisce con questo). Fix eventuali in
   CSS con `env(safe-area-inset-*)`.
2. **Predictive back** attivo per default: verificare il back di sistema con il
   router Ionic e la chiusura di modali/alert.

Test obbligatorio su emulatore Android 16 **e** su almeno un device fisico.

### 2.6 Hardening del manifest

1. **`android:allowBackup="false"`** — l'app conserva il JWT in
   `@capacitor/preferences`: con backup attivo il token può finire in
   `adb backup`/backup automatici. Alternativa: tenere `true` e aggiungere
   `android:dataExtractionRules` + `android:fullBackupContent` che escludano le
   preferences.
2. Spostare le eccezioni cleartext di `network_security_config.xml` in
   `app/src/debug/res/xml/` così la release non contiene alcuna eccezione
   (non richiesto da Google, ma elimina la domanda in caso di audit).

Non toccare `minifyEnabled false`: scelta consapevole e legittima.

### 2.7 Correzione di `RELEASE.md`

Correggere le due affermazioni obsolete (§0.2) e annotare lo schema versionCode.

---

## 3. Firma dell'applicazione

### 3.1 Play App Signing

Accettarlo: Google gestisce la chiave di firma dell'app, il keystore generato
localmente è solo la **upload key** — se si perde, si può chiederne il reset.
Senza Play App Signing, la perdita del keystore significa non poter mai più
aggiornare l'app.

### 3.2 Generazione dell'upload keystore (una volta sola, FUORI dal repo)

```powershell
New-Item -ItemType Directory -Force "C:\Users\mmoli\keys"
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v `
  -keystore "C:\Users\mmoli\keys\chronogram-upload.p12" `
  -alias chronogram-upload `
  -keyalg RSA -keysize 4096 -validity 10000 `
  -storetype PKCS12 `
  -dname "CN=Chronogram, OU=AIDA Lab, O=Universita degli Studi di Cassino e del Lazio Meridionale, L=Cassino, ST=FR, C=IT"
```

Scelte motivate: **PKCS12** (JKS è deprecato), **RSA 4096** (minimo Google 2048),
validità ~27 anni (Google richiede almeno fino al 22 ottobre 2033), stessa
password per store e key.

### 3.3 Credenziali (mai nel repo)

Copiare `keystore.properties.example` → `keystore.properties` con percorso
assoluto (forward slash):

```properties
storeFile=C:/Users/mmoli/keys/chronogram-upload.p12
storePassword=<password reale>
keyAlias=chronogram-upload
keyPassword=<password reale>
```

Verificare con `git check-ignore -v frontend/android/keystore.properties`; se
`*.p12` non è coperto dal `.gitignore`, **aggiungerlo**.

### 3.4 Custodia delle chiavi (responsabilità istituzionale)

1. Keystore + password nel password manager dell'ateneo/laboratorio (voce
   condivisa con almeno due persone).
2. Copia offline cifrata.
3. Fingerprint SHA-256 registrata in `RELEASE.md` (non è un segreto).
4. Documentare chi ha accesso a Play Console e keystore; **secondo Admin**
   sull'account Play (se l'unico account si disattiva, l'app diventa orfana).

---

## 4. Build dell'AAB di release

### 4.1 Sequenza esatta

```powershell
cd frontend
npm ci
npm run build          # NON build:web! base "/", API da .env.production

npx cap sync android   # da rieseguire dopo OGNI modifica al web

cd android
.\gradlew.bat clean
.\gradlew.bat bundleRelease
```

- `build:web` produrrebbe percorsi `/chronogram/...` e **schermo bianco
  nell'app**: è l'errore più probabile di tutta la procedura.
- `npm run build` esegue `vue-tsc`: un errore di tipo blocca la build.

### 4.2 Artefatti

| Artefatto | Percorso | Uso |
|---|---|---|
| **AAB firmato** | `app/build/outputs/bundle/release/app-release.aab` | Upload su Play Console |
| APK firmato | `app/build/outputs/apk/release/app-release.apk` (con `assembleRelease`) | Test su device, NON per lo store |

### 4.3 Verifiche prima dell'upload

```powershell
# La firma è realmente applicata? (senza credenziali Gradle produce un AAB
# NON firmato senza errore)
& "$env:JAVA_HOME\bin\jarsigner.exe" -verify -verbose -certs `
  app\build\outputs\bundle\release\app-release.aab | Select-Object -First 20

.\gradlew.bat assembleRelease
adb install -r app\build\outputs\apk\release\app-release.apk
```

Checklist funzionale **sull'APK di release** (non sul debug):

- login reale contro la produzione, anche a rete mobile (non solo Wi-Fi ateneo)
- registrazione, reset password, email in arrivo
- persistenza sessione dopo kill e riapertura
- funzione AI/LLM
- **flusso di cancellazione account end-to-end**
- rotazione, tastiera su form lunghi, back di sistema
- comportamento offline: errori sensati, nessun crash

Opzionale: `bundletool build-apks --mode=universal` per verificare che l'AAB
generi APK installabili.

### 4.4 Opzionale: script npm dedicato

```json
"build:android": "vue-tsc && vite build && cap sync android"
```

---

## 5. Registrazione su Google Play Console

**Da avviare subito: è il percorso critico del progetto.**

### 5.1 Individuale o organizzazione → ORGANIZZAZIONE

Raccomandazione forte: account **organizzazione** intestato all'Università di
Cassino e del Lazio Meridionale. Ragioni in ordine di peso:

1. **Esenzione dal requisito dei 12 tester × 14 giorni** (che grava sugli
   account personali creati dopo il 13/11/2023): vale settimane di calendario.
2. **Continuità istituzionale**: l'app sopravvive alla persona che l'ha pubblicata.
3. **Obbligo di categoria**: Google impone l'account organizzazione per app di
   ambito sanitario/medico/di ricerca (oltre a finanza, VPN, governo). Se
   Chronogram è presentata come strumento di ricerca su salute/benessere, non è
   una preferenza ma un obbligo.
4. Nome sviluppatore mostrato agli utenti = l'ateneo.

Costo: **25 USD una tantum**. Controparte: serve un **numero D-U-N-S**.

### 5.2 D-U-N-S — verificare se esiste già (probabile)

Identificativo a 9 cifre di Dun & Bradstreet, obbligatorio per gli account
organizzazione. **Le università italiane ne hanno quasi sempre già uno** (Apple
Developer Program, progetti europei):

1. **Prima** di richiederne uno nuovo: chiedere all'amministrazione centrale /
   ufficio ricerca e usare il lookup D-U-N-S di Dun & Bradstreet.
2. Se non esiste: richiesta gratuita, tempi da pochi giorni a ~30 giorni lavorativi.
3. I dati su Play Console (nome legale, indirizzo, telefono) devono **coincidere
   esattamente** con il record D-U-N-S: è la causa più comune di rigetto.

### 5.3 Passi

1. Account Google **istituzionale dedicato** (casella di laboratorio, non
   l'indirizzo personale del docente) → owner dell'account developer.
2. `play.google.com/console` → account tipo Organizzazione.
3. Nome legale, D-U-N-S, indirizzo, telefono, sito, email di contatto pubblica.
4. 25 USD (carta idealmente istituzionale).
5. Verifica identità/organizzazione: da pochi giorni a 2-3 settimane (le regole
   di verifica si sono inasprite nel 2026: non comprimere questa voce).
6. Ad account verificato: **secondo utente con ruolo Admin**.

---

## 6. Requisiti di listing e policy

### 6.1 Privacy policy pubblica — obbligatoria

`docs/privacy_policy.html` esiste ma non è raggiungibile pubblicamente. Serve un
URL pubblico, senza login, in HTTPS, che citi l'app.

**Soluzione più economica**: copiare il file in `frontend/public/privacy-policy.html`
(Vite copia `public/` nella root di `dist/`) → con la build web diventa

```
https://devaidalab.unicas.it/chronogram/privacy-policy.html
```

Zero modifiche a nginx. Idem per `terms_of_service.html`. Verificare che nessuna
regola nginx o filtro Spring richieda il JWT su quel path.

**Contenuto da aggiornare prima di pubblicare**:

- elenco puntuale dei dati raccolti (email, anagrafica, indirizzo, telefono,
  data di nascita, genere, note, dati economici, attività/diario)
- finalità e base giuridica GDPR (titolare = ateneo; per la ricerca: consenso o
  interesse pubblico/ricerca scientifica)
- **condivisione con terze parti: il provider LLM riceve il testo delle
  attività** — dichiarazione obbligatoria, oggi verosimilmente assente
- conservazione e procedura di cancellazione (riferimento alla funzione in-app)
- titolare del trattamento e contatto DPO
- data di ultimo aggiornamento

Il contenuto legale va validato dal DPO/ufficio privacy: non è una decisione tecnica.

### 6.2 App access — spesso dimenticato, causa certa di rigetto

Chronogram richiede login e la registrazione passa da un'approvazione admin: un
revisore Google che si registra da solo resta bloccato e rigetta l'app.

In **App content → App access**: credenziali di un **account demo già approvato e
attivo**, con istruzioni. Vincoli: valido a tempo indeterminato, popolato con
dati di esempio realistici, niente 2FA (o documentata), non l'account personale
del docente.

### 6.3 Data Safety form

Dichiarazioni proposte sulla base dei dati reali nel codice:

| Categoria Play | Dato | Raccolto | Condiviso | Perché |
|---|---|---|---|---|
| Personal info → Name | name, surname | Sì | No | Funzionalità app, account |
| Personal info → Email address | email | Sì | No | Funzionalità app, account, comunicazioni |
| Personal info → Address | address | Sì | No | Funzionalità app / ricerca |
| Personal info → Phone number | phone | Sì | No | Funzionalità app |
| Personal info → Gender | gender | Sì | No | Ricerca / analytics |
| Personal info → Date of birth | birthday | Sì | No | Ricerca / analytics |
| Personal info → Other info | notes, attività/diario | Sì | **Sì** (provider LLM) | Funzionalità app |
| Financial info → Other financial info | weeklyIncome, weeklyIncomeOther, weeklyHomeCost | Sì | No | Ricerca / analytics |
| App activity → Other actions | uso dell'app, attività | Sì | **Sì** (provider LLM) | Funzionalità app |

**Punto critico: la condivisione con il provider LLM.** Il testo delle attività
inviato all'LLM è condivisione con terze parti e va dichiarata: una
dichiarazione Data Safety inesatta è una violazione seria (sospensione).
Verificare con il backend cosa esattamente viene inviato, dove risiedono i
server del provider e se esiste un DPA. È anche un tema GDPR.

Security practices: **encrypted in transit: Sì** (HTTPS forzato, verificabile);
**users can request deletion: Sì** (subordinato all'endpoint funzionante).

Verificare se serve la dichiarazione **Health apps**: se il diario raccoglie
dati su salute/benessere, la categoria va dichiarata (e vale l'obbligo di
account organizzazione). Decisione da prendere consapevolmente, non per omissione.

### 6.4 Cancellazione account — bloccante

La User Data policy richiede **sia** un percorso di cancellazione **in-app**
**sia** un **link web** per richiedere la cancellazione (da inserire nel Data
Safety form). La cancellazione deve rimuovere account e tutti i dati.

- Percorso in-app: implementato (UI + endpoint `POST /api/profile/delete-account`)
  — **verificare end-to-end sull'APK di release contro la produzione**.
- **Link web di cancellazione: da creare** — pagina statica pubblica (es.
  `/chronogram/delete-account.html`, servita come la privacy policy) che spiega
  la procedura in-app e dà un contatto email per chi non ha più accesso all'app.
  Deve essere raggiungibile **senza login**.

### 6.5 Asset e testi della scheda

| Elemento | Requisito | Stato |
|---|---|---|
| Titolo | max 30 caratteri | "Chronogram" (10) — OK |
| Descrizione breve | max 80 caratteri | Da scrivere |
| Descrizione completa | max 4000 caratteri | Da scrivere (menzionare AIDA Lab / UniCas) |
| Icona store | 512×512, PNG 32-bit con alpha, <1 MB | Da produrre |
| Feature graphic | esattamente 1024×500, senza trasparenza | Da produrre |
| Screenshot telefono | minimo 2, fino a 8; lato 320–3840 px; ratio max 2:1 | Da produrre |
| Screenshot tablet 7"/10" | solo se si dichiara supporto tablet | Decidere |
| Video promo | opzionale | — |

Screenshot: catturarli sull'APK di release con dati demo realistici
(`adb exec-out screencap -p > shot1.png`); coprire home/timeline, inserimento
attività, funzione AI, profilo, impostazioni. Categoria: Productivity o
Lifestyle (evitare Medical se non necessario: scrutinio maggiore).

### 6.6 Content rating (IARC)

Esito atteso: PEGI 3 / Everyone. Attenzione a due domande:

- contenuti generati dagli utenti condivisi con altri: **no** (diario privato —
  verificare che non esistano funzioni social)
- **funzionalità di AI generativa: sì** — il questionario ora lo chiede;
  descrivere le salvaguardie contro output inappropriati

### 6.7 Target audience, ads e altre dichiarazioni

- **Target audience: 18+** (o secondo protocollo di ricerca). Sotto i 13 anni
  scatta la Families policy (requisiti pesanti). Dati raccolti (reddito,
  indirizzo, nascita) → 18+ è la scelta corretta.
- Ads: no (vero, nessuna SDK pubblicitaria). News/Government/Financial
  features: no. Advertising ID: nessun uso (verificare dopo la build, §2.4).

### 6.8 16 KB page size

Requisito per app che targettano Android 15+: riguarda il **codice nativo**.
Chronogram è WebView Capacitor senza librerie native proprie → con altissima
probabilità già conforme. Verifica a costo zero dopo la build:

```powershell
& "$env:JAVA_HOME\bin\jar.exe" tf app\build\outputs\bundle\release\app-release.aab | Select-String "\.so$"
```

Output vuoto = nulla da fare.

---

## 7. Testing

### 7.1 Requisito 12 tester × 14 giorni — non si applica alle organizzazioni

Si applica solo agli **account personali** creati dopo il 13/11/2023; gli account
organizzazione ne sono **esenti**. È l'argomento più forte a favore dell'account
organizzazione (§5.1). Se si ripiegasse su un account personale: 12 tester reali
installati e opted-in per 14 giorni consecutivi immediatamente precedenti la
domanda di produzione.

### 7.2 Percorso di test consigliato (anche se esenti)

1. **Internal testing** (fino a 100 tester, attivo in minuti): primo AAB qui.
   Valida installazione da Play, firma di Play App Signing (diversa dall'APK
   locale!) e produce il pre-launch report. Verificare il rendering su Android 16.
2. **Closed testing** (1-2 settimane, ~10-20 tester del laboratorio): test
   funzionale, incluse cancellazione account e sessione a lungo termine
   (scadenza JWT).
3. **Open testing**: facoltativo (solo se serve reclutare partecipanti su larga
   scala).
4. **Production** con rollout graduale (§8).

Esaminare sempre il **pre-launch report** (crash, accessibilità, ANR su device
reali; per una WebView segnala tipicamente contrasto e target touch).

### 7.3 Distribuzione ai tester

Mailing list Google Group (es. `chronogram-testers@googlegroups.com`) invece di
indirizzi singoli.

---

## 8. Rilascio e manutenzione

### 8.1 Primo rilascio in produzione

1. Tutte le sezioni App content verdi (Play blocca finché una è incompleta).
2. Release su Production con `app-release.aab`.
3. Countries: ragionevole partire con la sola Italia, allargabile poi.
4. Release notes in italiano e inglese.
5. **Staged rollout**: 20% → 50% → 100% in alcuni giorni monitorando Android
   vitals (crash rate, ANR). Un rollout parziale si può fermare, il 100% no.
6. Prima review per un account nuovo: fino a 7 giorni o più; mettere in conto
   un ciclo di rigetto e correzione.

### 8.2 Aggiornamenti

Ad ogni release: bump `versionCode`/`versionName` → `npm run build` →
`npx cap sync android` → `bundleRelease` → internal → produzione con staged
rollout → annotare in `RELEASE.md`.

Adempimenti ricorrenti:

- **target API level**: aggiornamento annuale (agosto). Promemoria a
  **giugno 2027** per API 37.
- Ri-verifica del Data Safety form quando il backend cambia i dati raccolti.
- Rinnovo credenziali dell'account demo di App access.

### 8.3 Crash reporting

- **Android vitals** (gratuito, zero integrazione): il minimo indispensabile,
  sufficiente per iniziare. Non copre gli errori JS nella WebView.
- **Sentry** (`@sentry/capacitor`): cattura anche errori JS/Vue (la maggioranza
  dei problemi reali di un'app Ionic), ma modifica Data Safety form e privacy
  policy. Valutare dopo il primo mese.
- **Firebase Crashlytics**: `app/build.gradle` applica già condizionalmente
  `google-services` se trova `google-services.json`; stesse implicazioni privacy.

---

## 9. Checklist finale, responsabilità e stima tempi

### 9.1 Cosa fa lo sviluppatore

**Fase A — Blocchi tecnici (2-4 giorni effettivi)**

- [ ] Decidere `applicationId` (irreversibile, §2.1)
- [ ] Installare `platforms;android-36`, impostare `ANDROID_HOME` (§1.2-1.3)
- [ ] Bump AGP → 8.13, Gradle → 8.13, compile/targetSdk → 36 (§2.5)
- [ ] Build pulita + test su emulatore Android 16 e device fisico: edge-to-edge,
      safe area, predictive back, tastiera (§2.5)
- [ ] Keystore PKCS12 fuori dal repo + `keystore.properties` (§3.2-3.3)
- [ ] `*.p12` in `.gitignore` (§3.3)
- [ ] `allowBackup="false"` o regole di esclusione (§2.6)
- [ ] Eccezioni cleartext in `app/src/debug` (opzionale, §2.6)
- [ ] Aggiornare `RELEASE.md` (§2.7)

**Fase B — Contenuti e asset (1-2 giorni)**

- [ ] Artwork finale + `@capacitor/assets`; `splash-dark.png` (§2.3)
- [ ] Icona store 512×512 e feature graphic 1024×500 (§6.5)
- [ ] Minimo 2 screenshot telefono da APK release con dati demo (§6.5)
- [ ] Descrizioni breve/completa IT+EN (§6.5)

**Fase C — Compliance (in parte lato backend)**

- [ ] Verifica end-to-end della cancellazione account in produzione (§6.4)
- [ ] Pagina web pubblica di richiesta cancellazione (§6.4)
- [ ] Privacy policy e ToS pubblicate via `frontend/public/`; accesso senza JWT
      verificato (§6.1)
- [ ] Privacy policy aggiornata: dati, base giuridica GDPR, **condivisione con
      il provider LLM**, titolare/DPO (§6.1)
- [ ] Accertare cosa viene inviato all'LLM; DPA con il provider (§6.3)
- [ ] Account demo permanente pre-approvato per App access (§6.2)

**Fase D — Play Console**

- [ ] Creare l'app, accettare Play App Signing
- [ ] Data Safety form (tabella §6.3)
- [ ] Content rating IARC, dichiarando l'AI generativa (§6.6)
- [ ] Target audience 18+, no ads, App access con credenziali demo (§6.7, §6.2)
- [ ] Build AAB + verifica firma con `jarsigner -verify` (§4)
- [ ] Internal testing → pre-launch report → closed testing → produzione al 20% (§7-8)

### 9.2 Cosa richiede l'ateneo (non risolvibile dallo sviluppatore)

| Voce | Chi | Note |
|---|---|---|
| **Numero D-U-N-S** | Amministrazione centrale / ufficio ricerca | Verificare prima se esiste già (molto probabile). Se no: gratuito, fino a ~30 gg lavorativi |
| **Carta per i 25 USD** | Amministrazione / fondi di progetto | Una tantum, idealmente carta istituzionale |
| **Dati legali dell'organizzazione** | Amministrazione | Devono coincidere esattamente col record D-U-N-S |
| **Documenti per la verifica** | Amministrazione + docente | Documenti istituzionali + documento del referente |
| **Account Google istituzionale dedicato** | Servizi IT | Owner dell'account developer, non un indirizzo personale |
| **Validazione privacy policy** | DPO / ufficio legale | GDPR, base giuridica, condivisione con LLM |
| **Approvazione protocollo di ricerca** | Comitato etico (se previsto) | L'approvazione etica precede la raccolta dati |
| **Custodia istituzionale delle chiavi** | Laboratorio / Servizi IT | Password manager condiviso, secondo Admin su Play (§3.4) |

### 9.3 Stima tempi

| Fase | Durata | Parallelizzabile |
|---|---|---|
| Account organizzazione, D-U-N-S già esistente | 1 g compilazione + 3-15 gg lavorativi di verifica | Sì — **avviare subito** |
| Account, se il D-U-N-S va richiesto | + fino a 30 gg lavorativi | Sì |
| Fase A (API 36, keystore, hardening) | 2-4 giorni | Sì |
| Fase B (asset, testi) | 1-2 giorni | Sì |
| Fase C (compliance) | 2-5 giorni | Sì, ma blocco duro |
| Fase D (Play Console) | 1-2 giorni | Solo ad account verificato |
| Internal testing + pre-launch report | 2-3 giorni | Solo ad account verificato |
| Closed testing (consigliato) | 7-14 giorni | — |
| Review prima release | fino a 7 giorni, oltre se rigetto | — |
| Staged rollout | 3-7 giorni | — |

**Totale realistico: 4-6 settimane** con D-U-N-S esistente; **8-10 settimane** se
va richiesto. Il lavoro tecnico (~1 settimana) non è il collo di bottiglia: lo
sono la verifica dell'account e la compliance.

### 9.4 Le tre cose da fare subito

1. **Avviare la pratica dell'account organizzazione**: chiedere
   all'amministrazione se il D-U-N-S di UniCas esiste. È l'unica voce che non si
   può accelerare dopo, e domina il calendario.
2. **Verificare la cancellazione account end-to-end in produzione** e creare la
   pagina web pubblica di richiesta cancellazione.
3. **Decidere l'`applicationId`**: dopo la prima pubblicazione non si torna
   indietro.

---

## Osservazioni di merito

**Il livello nativo è in condizioni migliori del previsto**: signing config già
implementata senza segreti nel repo, network security config corretta HTTPS-only,
un solo permesso, `.env.production` già puntato alla produzione, asset nativi
generati, `RELEASE.md` esistente. Il lavoro mobile residuo è nell'ordine di una
settimana.

**Il rischio reale non è tecnico ma di calendario e compliance**: verifica
dell'account developer (fuori dal controllo dello sviluppatore) e adempimenti
privacy/policy. Il bump ad API 36 è urgente per data ma è mezza giornata di
lavoro più il test.

**Due punti da non sottovalutare**: la condivisione dei dati di attività con il
provider LLM va accertata e dichiarata (una dichiarazione Data Safety inesatta è
una violazione seria); `RELEASE.md` contiene affermazioni non più vere che
porterebbero a pubblicare col target sbagliato.

---

## Fonti

- [Target API level requirements — Play Console Help](https://support.google.com/googleplay/android-developer/answer/11926878?hl=en)
- [App testing requirements for new personal developer accounts — Play Console Help](https://support.google.com/googleplay/android-developer/answer/14151465?hl=en)
- [Choose a developer account type — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13634885?hl=en)
- [Required information to create a Play Console developer account — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13628312?hl=en)
- [App account deletion requirements — Play Console Help](https://support.google.com/googleplay/android-developer/answer/13327111?hl=en)
- [About Android Gradle plugin (compatibilità AGP/Gradle/API) — Android Developers](https://developer.android.com/build/releases/about-agp)
- [16 KB page size compatibility — Android Developers Blog](https://android-developers.googleblog.com/2025/05/prepare-play-apps-for-devices-with-16kb-page-size.html)
- [Support 16 KB page sizes — Android Developers](https://developer.android.com/guide/practices/page-sizes)
- [Updating to Capacitor 7.0](https://capacitorjs.com/docs/updating/7-0)
- [The Google Play 12-Tester Rule, Explained (2026) — Choicely](https://www.choicely.com/blog/google-play-12-tester-rule)
- [Google Play screenshot sizes 2026 — App Radar](https://appradar.com/blog/android-app-screenshot-sizes-and-guidelines-for-google-play)
- [Google Play screenshot/listing guidelines — Choicely](https://www.choicely.com/tutorials/google-play-app-store-guidelines-screenshots-listings)
- [Google identity verification rules for Android developers — Biometric Update](https://www.biometricupdate.com/202508/google-unveils-identity-verification-rules-for-android-app-developers)
