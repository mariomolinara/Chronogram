# Chronogram — Android release & Play Store publishing

Guida operativa per firmare e pubblicare l'app Android (`it.unicas.chronogram`).
Tutti i comandi vanno lanciati dalla cartella `frontend/` salvo dove indicato.

> Nessun segreto vive nel repo. `keystore.properties`, `*.jks` e `*.keystore`
> sono in `.gitignore`. Non committarli mai.

---

## 0. Prerequisiti

- Node 18+ e npm (verificato: Node 22).
- JDK 17+ (verificato: Corretto 21).
- Android SDK con platform `android-35` e build-tools 35 installati.
- Variabile `ANDROID_HOME` (o file `android/local.properties` con
  `sdk.dir=...`) che punti all'SDK. Su questa macchina l'SDK è in
  `C:\Users\<utente>\AppData\Local\Android\Sdk`.

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

- `applicationId` = `it.unicas.chronogram`.
- `compileSdk`/`targetSdk` = **35** — conforme al requisito Play Store attuale
  (nuove app/aggiornamenti richiedono target API 35). OK, nessun bump necessario.
- `minSdk` = 23.
- **Cleartext traffic**: `AndroidManifest.xml` ha `usesCleartextTraffic="true"`.
  In produzione l'app DEVE parlare col backend via **HTTPS**. Prima della
  pubblicazione: rimuovere `usesCleartextTraffic="true"` (o usare un
  `network_security_config.xml` che consenta cleartext solo verso host di dev).
  Non modificato qui per non rompere il flusso di sviluppo attuale — da
  coordinare con back-end/front-end quando l'endpoint prod HTTPS è definito.
- **Gradle wrapper**: `gradle-wrapper.properties` punta a Gradle **9.3.0** con
  Android Gradle Plugin **8.7.2**. Questa combinazione NON è nella matrice di
  compatibilità ufficiale (AGP 8.7 è testato fino a Gradle 8.9), ma è stata
  verificata su questa macchina: `gradlew :app:help` e
  `gradlew :app:bundleRelease --dry-run` completano con BUILD SUCCESSFUL (solo
  warning di deprecation). Se un giorno un `bundleRelease` reale fallisse per
  incompatibilità, riportare il wrapper a `gradle-8.9-bin.zip` o alzare AGP.
- **Code shrinking**: `minifyEnabled`/`shrinkResources` lasciati **OFF** di
  proposito — R8 può rimuovere classi dei plugin Capacitor caricate via
  reflection. Abilitarli solo dopo aver aggiunto e testato `-keep` rules.

---

## Checklist manuale residua

- [ ] Generare il keystore di release (`keytool`) e fare backup sicuro.
- [ ] Creare `android/keystore.properties` con le credenziali reali.
- [ ] (Opzionale) `npx @capacitor/assets generate --android` per gli asset finali.
- [ ] Decidere l'endpoint backend HTTPS di produzione e rimuovere il cleartext.
- [ ] Verificare compatibilità Gradle wrapper ↔ AGP prima del `bundleRelease`.
- [ ] Creare l'app su Play Console + Play App Signing.
- [ ] Preparare screenshot/icona store/feature graphic + privacy policy URL.
- [ ] Compilare Data safety form e content rating.
- [ ] Caricare l'AAB e inviare per revisione.
