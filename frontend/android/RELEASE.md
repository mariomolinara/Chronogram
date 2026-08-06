# Chronogram — Android release & Play Store publishing

Guida operativa per firmare e pubblicare l'app Android
(`it.unicas.aidalab.chronogram`).
Tutti i comandi vanno lanciati dalla cartella `frontend/` salvo dove indicato.

> Nessun segreto vive nel repo. `keystore.properties`, `*.jks` e `*.keystore`
> sono in `.gitignore`. Non committarli mai.

---

## 0. Prerequisiti

- **Node 22+** e npm — requisito di Capacitor 8 (verificato: Node 22.15.0).
- **JDK 21** — Capacitor 8 compila con `sourceCompatibility`/
  `targetCompatibility` = 21 (verificato: Corretto 21.0.10).
- Android SDK con platform **`android-36`** e **build-tools 36.0.0** installati
  (compileSdk/targetSdk = 36).
- `cmdline-tools` installati in `<SDK>/cmdline-tools/latest/bin` per avere
  `sdkmanager` / `avdmanager`.
- Variabili `ANDROID_HOME` e `ANDROID_SDK_ROOT` (o file
  `android/local.properties` con `sdk.dir=...`) che puntino all'SDK. Su questa
  macchina l'SDK è in `C:\Users\<utente>\AppData\Local\Android\Sdk`.

Se `ANDROID_HOME` non è impostata, crea `frontend/android/local.properties`:

```
sdk.dir=C\:\\Users\\<utente>\\AppData\\Local\\Android\\Sdk
```

(`local.properties` è git-ignored.)

---

## 1. Generare il keystore di release (UNA VOLTA SOLA)

Il keystore firma **tutte** le release future: conservalo e fai backup sicuro.
Se lo perdi (e non usi Play App Signing) non potrai più aggiornare l'app.

```bash
keytool -genkeypair -v \
  -keystore chronogram-release.jks \
  -alias chronogram \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -storetype JKS
```

Su Windows PowerShell (una riga):

```powershell
keytool -genkeypair -v -keystore chronogram-release.jks -alias chronogram -keyalg RSA -keysize 2048 -validity 10000 -storetype JKS
```

Rispondi alle domande (nome, org "Università di Cassino", ecc.). Ti verrà
chiesta la **store password** e la **key password**.

Consiglio: metti `chronogram-release.jks` FUORI dal repo (es.
`C:\Users\<utente>\keys\`) e referenzialo con path assoluto in
`keystore.properties`.

---

## 2. Configurare le credenziali (senza committarle)

Copia l'esempio e compila i valori reali:

```bash
cp android/keystore.properties.example android/keystore.properties
```

`android/keystore.properties`:

```properties
storeFile=C:/Users/<utente>/keys/chronogram-release.jks
storePassword=********
keyAlias=chronogram
keyPassword=********
```

In alternativa (CI/CD) esporta variabili d'ambiente al posto del file:

```
CHRONOGRAM_STORE_FILE, CHRONOGRAM_STORE_PASSWORD,
CHRONOGRAM_KEY_ALIAS,  CHRONOGRAM_KEY_PASSWORD
```

`app/build.gradle` legge in ordine: `keystore.properties` → `-P` Gradle props →
env vars. Se nessuna credenziale è presente, la release resta **non firmata**
(build non pubblicabile ma non rotta).

---

## 3. Generare icone e splash (@capacitor/assets)

I sorgenti sono già in `frontend/assets/` (`icon-foreground.png`,
`icon-background.png`, `splash.png`). Per (ri)generare le risorse native:

```bash
npx @capacitor/assets generate --android
```

Questo popola `android/app/src/main/res/` (mipmap icone, drawable splash).
Committa le risorse generate (non sono segreti).

---

## 4. Build dell'AAB firmato

```bash
# 1. Build web (type-check + Vite)
npm run build

# 2. Copia i web asset e sincronizza i plugin nel progetto Android
npx cap sync android

# 3. Build del bundle di release firmato
cd android
./gradlew bundleRelease          # macOS/Linux
# .\gradlew.bat bundleRelease    # Windows PowerShell
```

Output firmato:
`android/app/build/outputs/bundle/release/app-release.aab`

Per un APK firmato (test su device, NON per lo store):

```bash
./gradlew assembleRelease
# -> android/app/build/outputs/apk/release/app-release.apk
```

Verifica la firma:

```bash
jarsigner -verify -verbose -certs app/build/outputs/apk/release/app-release.apk
# oppure, per l'AAB, controlla con: bundletool validate --bundle=...
```

---

## 5. Bump di versione (ad ogni release)

In `android/app/build.gradle`, `defaultConfig`:

- `versionCode` — intero, DEVE crescere ad ogni upload allo store.
- `versionName` — stringa mostrata all'utente (es. "1.0", "1.1").

Attuale: `versionCode 1`, `versionName "1.0"`.

**Schema consigliato dalla prossima release**: derivare il `versionCode` dal
`versionName` semantico con

```
versionCode = MAJOR * 10000 + MINOR * 100 + PATCH
```

Esempi: `1.0.0` → `10000`, `1.2.0` → `10200`, `2.0.3` → `20003`. Così il codice
cresce sempre in modo monotono, è ricavabile a mente dalla versione mostrata
all'utente e lascia spazio a 99 minor e 99 patch per major. Il `versionCode 1`
attuale resta valido come punto di partenza: la prossima release sarà `10100`
(`versionName "1.1.0"`) o `10001` (`versionName "1.0.1"`).

---

## 6. Play Console — primo caricamento

1. Crea l'app su https://play.google.com/console (nome, lingua, categoria).
2. **Play App Signing**: accetta che Google gestisca la chiave di firma finale
   (consigliato). Il tuo keystore diventa la *upload key*; se lo perdi puoi
   richiedere il reset della upload key.
3. Compila la scheda Store: descrizione, screenshot (phone + tablet), icona
   512×512, feature graphic 1024×500.
4. **Privacy policy**: usa l'URL pubblico di `docs/privacy_policy.html`.
5. Compila il **Data safety form** e il questionario contenuti/rating.
6. Crea una release (Internal testing → Closed → Production), carica
   `app-release.aab`, imposta le note di rilascio, invia per revisione.

---

## Note tecniche verificate su questo progetto

- `applicationId` / `namespace` = `it.unicas.aidalab.chronogram`.
- **Capacitor 8** (`@capacitor/core`, `cli`, `android` = 8.5.0). Capacitor 8
  richiede Node 22+ e JDK 21.
- `compileSdk`/`targetSdk` = **36**. Google Play richiede **target API 36 per
  tutti gli upload dal 31 agosto 2026**: il target 35 precedente NON era più
  conforme, il bump a 36 era obbligatorio ed è stato fatto.
- `minSdk` = **24** (alzato da 23: è il minimo del template Capacitor 8).
- **Cleartext traffic**: il manifest NON usa `usesCleartextTraffic`. Dichiara
  invece `android:networkSecurityConfig="@xml/network_security_config"`, dove il
  `base-config` ha `cleartextTrafficPermitted="false"` (produzione HTTPS-only) e
  un `domain-config` abilita l'HTTP in chiaro **solo** verso host di sviluppo
  (`localhost`, `127.0.0.1`, `10.0.2.2`, `10.0.3.2`, `192.168.1.100`). Per
  testare contro un'altra macchina della LAN, aggiungere il proprio IP a quel
  `domain-config`; non inserirvi mai domini di produzione.
- **Toolchain Gradle**: Android Gradle Plugin **8.13.0** con Gradle wrapper
  **8.14.3** — combinazione dentro la matrice di compatibilità ufficiale e
  allineata al template Capacitor 8. Nota: **AGP 9.x non è supportato da
  Capacitor** (nemmeno da Capacitor 8), quindi non alzare AGP oltre la serie 8.x
  finché Capacitor non lo dichiara supportato; per lo stesso motivo il wrapper
  va tenuto sulla serie 8.x e non su Gradle 9.
- **Kotlin**: il progetto è interamente Java — nessun plugin Capacitor in uso
  richiede Kotlin, quindi non c'è alcun Kotlin plugin/versione da gestire nei
  file Gradle. Se in futuro si aggiunge un plugin con sorgenti Kotlin, il
  template Capacitor 8 si aspetta Kotlin **2.2.20**.
- **Firebase / `google-services.json`**: il file è stato **rimosso**. Conteneva
  la registrazione del vecchio package `it.unicas.chronogram` e avrebbe rotto la
  build dopo la rinomina (il plugin google-services verifica che il
  `package_name` nel JSON combaci con l'`applicationId`). Nessuna funzionalità
  Firebase è in uso: non è installato alcun plugin di push notification. Il
  blocco `try/catch` in fondo a `app/build.gradle` gestisce l'assenza del file e
  semplicemente non applica il plugin.
  **Per riattivare Firebase in futuro**: registrare una nuova app Android con
  package `it.unicas.aidalab.chronogram` sulla console Firebase
  (https://console.firebase.google.com), scaricare il nuovo `google-services.json`
  e metterlo in `android/app/google-services.json`; poi installare il plugin di
  push (`npm i @capacitor/push-notifications`) e `npx cap sync android`.
- **Code shrinking**: `minifyEnabled`/`shrinkResources` lasciati **OFF** di
  proposito — R8 può rimuovere classi dei plugin Capacitor caricate via
  reflection. Abilitarli solo dopo aver aggiunto e testato `-keep` rules.

---

## Checklist manuale residua

- [ ] Generare il keystore di release (`keytool`) e fare backup sicuro.
- [ ] Creare `android/keystore.properties` con le credenziali reali.
- [ ] (Opzionale) `npx @capacitor/assets generate --android` per gli asset finali.
- [ ] Decidere l'endpoint backend HTTPS di produzione e togliere dal
      `network_security_config.xml` gli host di dev non più necessari.
- [ ] Adottare lo schema `versionCode` MAJOR*10000+MINOR*100+PATCH alla prossima
      release.
- [ ] Creare l'app su Play Console + Play App Signing.
- [ ] Preparare screenshot/icona store/feature graphic + privacy policy URL.
- [ ] Compilare Data safety form e content rating.
- [ ] Caricare l'AAB e inviare per revisione.
