# Chronogram — Manuale utente

> Versione italiana. English version: [USER_MANUAL.md](USER_MANUAL.md)

Chronogram è lo strumento di ricerca dell'Università degli Studi di Cassino e del
Lazio Meridionale per lo studio dell'uso del tempo. I partecipanti registrano le
proprie attività quotidiane — in tempo reale o a posteriori — e i dati raccolti
costituiscono la base empirica dello studio.

Questo manuale è diviso in due parti:

- [Parte 1 — Lato partecipante](#parte-1--lato-partecipante), per chi usa l'app
  per registrare le proprie attività;
- [Parte 2 — Lato amministratore](#parte-2--lato-amministratore), per chi gestisce
  i partecipanti e i dati dello studio.

> **Nota sulla lingua.** L'interfaccia dell'applicazione è interamente in inglese.
> In questo manuale le etichette dei pulsanti e dei campi sono riportate in
> inglese fra virgolette (es. «Save Activity»), così da poterle riconoscere a
> schermo.

---

## Indice

- [Come si accede](#come-si-accede)
- [Parte 1 — Lato partecipante](#parte-1--lato-partecipante)
  - [1.1 Registrazione](#11-registrazione)
  - [1.2 Approvazione dell'account](#12-approvazione-dellaccount)
  - [1.3 Accesso](#13-accesso)
  - [1.4 Password dimenticata](#14-password-dimenticata)
  - [1.5 La schermata Home](#15-la-schermata-home)
  - [1.6 Registrare una nuova attività](#16-registrare-una-nuova-attività)
  - [1.7 Compilazione assistita dall'AI](#17-compilazione-assistita-dallai)
  - [1.8 Modificare o eliminare un'attività](#18-modificare-o-eliminare-unattività)
  - [1.9 Impostazioni](#19-impostazioni)
  - [1.10 Assistenza](#110-assistenza)
  - [1.11 Uscire dall'app](#111-uscire-dallapp)
  - [1.12 Eliminare il proprio account](#112-eliminare-il-proprio-account)
- [Parte 2 — Lato amministratore](#parte-2--lato-amministratore)
  - [2.1 Il primo accesso](#21-il-primo-accesso)
  - [2.2 La dashboard](#22-la-dashboard)
  - [2.3 Gestione dei partecipanti](#23-gestione-dei-partecipanti)
  - [2.4 Le azioni sugli account](#24-le-azioni-sugli-account)
  - [2.5 Esportazione dei dati](#25-esportazione-dei-dati)
  - [2.6 Account amministratore](#26-account-amministratore)
- [Appendice A — Messaggi di errore ricorrenti](#appendice-a--messaggi-di-errore-ricorrenti)
- [Appendice B — Funzioni non ancora attive](#appendice-b--funzioni-non-ancora-attive)

---

## Come si accede

Chronogram è disponibile in due forme, con le stesse funzioni e gli stessi dati:

| Forma | Come si usa |
|---|---|
| **Applicazione web** | Si apre da browser all'indirizzo comunicato dal responsabile dello studio. |
| **App Android** | Si installa sul telefono (APK o Play Store) e si apre come qualunque altra app. |

L'account è lo stesso: si può registrare un'attività dal telefono e rileggerla dal
browser.

### Il popup di benvenuto

Al primo avvio compare una finestra **«About Chronogram»** con la premessa del
responsabile scientifico, l'elenco del gruppo di lavoro e i contatti. Si chiude con
**«OK»**. Se si spunta **«Don't show this on the next start-up»** prima di premere
OK, la finestra non ricompare agli avvii successivi. Le stesse informazioni restano
sempre consultabili dall'icona ⓘ in alto a sinistra nella Home.

---

# Parte 1 — Lato partecipante

## 1.1 Registrazione

Dalla schermata di accesso premere **«Sign Up»**. Il modulo di registrazione
chiede:

| Campo | Obbligatorio | Note |
|---|---|---|
| Name | Sì | Nome |
| Surname | Sì | Cognome |
| Address | Sì | Indirizzo |
| Phone | No | Telefono |
| Email | Sì | È anche il nome utente con cui si accede |
| Password | Sì | Vedi i requisiti qui sotto |
| Confirm password | Sì | Deve coincidere con la password |
| Birthday | No | Data di nascita, si sceglie dalla ruota e si conferma con «Done» |
| Gender | No | Male / Female / Other / Prefer not to say |

I campi contrassegnati con **\*** sono obbligatori.

**Requisiti della password:** almeno 8 caratteri, con almeno una maiuscola, una
minuscola, una cifra e un simbolo. L'icona a forma di occhio accanto al campo
permette di rileggere quanto digitato.

Il pulsante **«Register»** resta sempre premibile: se qualcosa manca, premendolo
compaiono i messaggi sotto i campi da correggere, invece di lasciare il pulsante
spento senza spiegazione.

### Registrazione con Google

In alternativa al modulo si può usare il pulsante **«Sign in with Google»**, se
attivo nella propria installazione. L'account viene creato al primo accesso
usando nome, cognome ed email verificati da Google: non serve scegliere una
password. Da quel momento si entra sempre con lo stesso pulsante.

> La registrazione con modulo è protetta da reCAPTCHA. Non richiede alcuna azione
> da parte dell'utente: è una verifica invisibile che avviene in background.

## 1.2 Approvazione dell'account

Non tutte le registrazioni sono immediatamente operative.

- **Indirizzi di un dominio di fiducia** (nella configurazione predefinita
  `unicas.it` e i suoi sottodomini, quindi anche `studentmail.unicas.it`) vengono
  **approvati automaticamente**: si può accedere subito.
- **Tutti gli altri indirizzi** creano un account **in attesa di approvazione**.
  Comparirà la schermata «Request sent», che spiega che:
  1. l'account è stato creato ma non può ancora accedere;
  2. un amministratore esamina la richiesta e si riceve una email con l'esito;
  3. dopo l'approvazione si entra con l'email e la password appena scelte.

Se si prova ad accedere prima dell'approvazione, il login viene rifiutato con un
messaggio che ne spiega il motivo.

## 1.3 Accesso

Nella schermata di accesso inserire **Email** e **Password** e premere
**«Login»**, oppure usare **«Sign in with Google»**.

Gli esiti negativi (credenziali errate, account in attesa di approvazione, account
bloccato) restano scritti in pagina sotto i campi finché non si riprova: non sono
solo un avviso che scompare dopo pochi secondi.

La sessione dura **24 ore**, dopodiché viene richiesto di nuovo l'accesso. La
sessione è conservata sul dispositivo, quindi chiudendo e riaprendo l'app entro le
24 ore non serve reinserire le credenziali.

## 1.4 Password dimenticata

1. Nella schermata di accesso premere **«Forgot Password?»**.
2. Inserire l'indirizzo email dell'account.
3. Il sistema risponde sempre con lo stesso messaggio — *«If your email address
   exists in our system, you will receive a password reset link»* — indipendentemente
   dal fatto che l'indirizzo esista o meno. È voluto: evita di rivelare a un
   estraneo quali indirizzi sono registrati.
4. Se l'account esiste, arriva una email con un link. **Il link vale 30 minuti.**
5. Aprendo il link si arriva alla pagina di reimpostazione: si sceglie la nuova
   password (stessi requisiti della registrazione) e la si conferma.

> Fra due richieste consecutive per lo stesso account deve trascorrere almeno un
> minuto. Una seconda richiesta immediata non genera una nuova email.

## 1.5 La schermata Home

La Home è il **diario della giornata corrente**. Mostra, dall'alto verso il basso:

- il proprio nome;
- la data di oggi;
- la **timeline** delle attività registrate oggi, ciascuna con l'orario di
  registrazione a sinistra, un punto colorato in base alla categoria e una scheda
  con il tipo di attività, gli eventuali dettagli, il luogo e il costo.

Se non c'è ancora nulla, compare «No activities for today» con un pulsante per
inserire la prima attività. Se il caricamento fallisce compare «Couldn't load your
activities» con un pulsante **«Retry»**.

**I comandi:**

| Posizione | Icona | Funzione |
|---|---|---|
| In alto a sinistra | ⓘ | Apre la pagina About |
| In alto a destra | ⏻ | Esce dall'account (chiede conferma) |
| In basso a sinistra | 🏠 | Home (pagina corrente) |
| In basso al centro | **+** | Registra una nuova attività |
| In basso a destra | ⚙ | Impostazioni |

## 1.6 Registrare una nuova attività

Premere il pulsante **+** al centro della barra inferiore della Home. Si apre il
modulo **«New Activity»**.

| Campo | Obbligatorio | Valori |
|---|---|---|
| **Name of Activity** | Sì | Testo libero: come si chiama l'attività |
| **Duration (minutes)** | Sì | Da 1 a 1440 minuti (1440 = 24 ore) |
| Details | No | Note aggiuntive, fino a 400 caratteri |
| **Type of activity** | Sì | Una delle categorie predefinite (vedi sotto) |
| Pleasantness | No | Da −3 a +3, con i pulsanti − e +. Ogni valore è mostrato con la parola corrispondente — Pain (−3), Sorrow (−2), Discomfort (−1), Boredom (0), Relief (+1), Pleasure (+2), Joy (+3). Predefinito Boredom (0) |
| **Recurrence** | Sì | *Routinary (R)* = abituale · *Exceptional (E)* = eccezionale |
| Cost (€) | No | Spesa sostenuta |
| Location | No | At home / At work / Outside / Other |

**Categorie disponibili:** Work (lavoro), Study (studio), Leisure (tempo libero),
Exercise (sport e attività fisica), Food (pasti), Hygiene (cura della persona),
Commute (spostamenti).

Al termine premere **«Save Activity»**. Se manca qualcosa, la pressione del
pulsante evidenzia i campi da correggere e porta il cursore sul primo di essi.
Con **«Cancel»** si torna indietro senza salvare.

L'attività viene registrata con la data e l'ora del salvataggio e compare subito
nella timeline della Home.

## 1.7 Compilazione assistita dall'AI

In cima al modulo «New Activity» c'è il riquadro **«Fill with AI»**. Serve a
descrivere l'attività a parole proprie e lasciare che i campi si compilino da soli.

**Come si usa:**

1. Premere **«Fill with AI»**: si apre una finestra con una casella di testo.
2. Descrivere l'attività in linguaggio naturale. Più dettagli si danno — quanto è
   durata, quanto è costata, come ci si è sentiti, dove si era — più campi
   verranno compilati.
   > *Esempio:* «Gym session this morning, about 90 minutes, €12 for the day pass,
   > felt great afterwards — at the sports centre.»
3. In alternativa si può partire da uno dei tre esempi pronti (**Gym**,
   **Team meeting**, **Groceries**) e modificarlo.
4. Premere **«Fill the form»**.
5. La finestra si chiude e i campi compilati dall'AI restano evidenziati per
   qualche secondo. In cima al modulo compare un promemoria — *«Filled by AI.
   Check the highlighted fields before saving»* — che riporta anche la frase
   inviata, così da poterla confrontare con quello che è stato compilato.
6. **Rileggere e correggere**, poi premere **«Save Activity»**.

> **L'AI non salva nulla.** Compila soltanto i campi: il salvataggio resta un gesto
> esplicito dell'utente, che è sempre responsabile di ciò che viene registrato.
> Un campo già compilato a mano non viene cancellato se l'AI non ha nulla da dire
> su quel punto.

**Se non funziona:**

- *«We couldn't recognise any activity details in that text»* — la frase non
  conteneva informazioni utilizzabili. La finestra resta aperta con il testo
  scritto: si può correggerlo invece di ripartire da zero.
- *«The AI assistant is unavailable right now»* — problema tecnico o servizio non
  configurato. Si può chiudere la finestra e compilare il modulo a mano: è sempre
  possibile.

## 1.8 Modificare o eliminare un'attività

Dalla timeline della Home:

- **Modificare:** toccare la scheda dell'attività. Si riapre il modulo con i valori
  già compilati; dopo le correzioni premere di nuovo **«Save Activity»**.
- **Eliminare:** premere l'icona del cestino in alto a destra sulla scheda. Viene
  chiesta conferma («Delete activity from HH:MM?»). L'eliminazione è definitiva.

## 1.9 Impostazioni

Si aprono con l'icona ⚙ in basso a destra nella Home. In cima compare il proprio
nome, con un'icona a matita per modificare il profilo. Le voci disponibili:

| Voce | Cosa fa |
|---|---|
| **Edit Profile** | Nome, cognome, indirizzo, telefono, data di nascita, genere. L'**email non è modificabile**: identifica l'account. |
| **Change Password** | Richiede la password attuale, la nuova e la sua conferma. La nuova password deve rispettare gli stessi requisiti della registrazione. |
| **Notifications** | Preferenze di notifica (vedi [Appendice B](#appendice-b--funzioni-non-ancora-attive)). |
| **Support** | Domande frequenti e modulo per contattare l'assistenza. |
| **About** | Informazioni sul progetto, sul gruppo di lavoro e contatti. |
| **Administration** | *Solo per gli amministratori:* apre il back-office. |
| **Delete Account** | Avvia la cancellazione definitiva dell'account. |

In fondo alla pagina ci sono le icone 🏠 (Home), ⏻ (uscita) e ⚙ (pagina corrente).

## 1.10 Assistenza

**Settings → Support**. La pagina contiene:

- una sezione di **domande frequenti** che si aprono a fisarmonica;
- un modulo **«Still stuck? Help is a message away»** con due campi obbligatori:
  - **Subject** — l'oggetto, massimo 150 caratteri;
  - **Message** — la descrizione del problema, massimo 2000 caratteri.

Premendo **«Send Message»** il messaggio viene recapitato via email alla casella
di assistenza dello studio. Conviene indicare cosa si stava facendo, cosa ci si
aspettava e cosa è successo invece.

## 1.11 Uscire dall'app

L'icona ⏻ è disponibile sia in alto a destra nella Home sia al centro della barra
inferiore delle Impostazioni. Viene chiesta conferma («Sign out?»). L'uscita
cancella la sessione dal dispositivo; i dati registrati restano sul server e si
ritrovano al successivo accesso.

## 1.12 Eliminare il proprio account

> ⚠️ **Operazione irreversibile.** Vengono cancellati definitivamente il profilo,
> le preferenze e **tutte le attività registrate**. Non è possibile recuperarli.

1. **Settings → Delete Account**. La prima schermata spiega le conseguenze.
   Premere **«Delete account»** per proseguire o **«Go back»** per annullare.
2. Si apre la schermata **«Before you go»**. A questo punto **non è ancora stato
   cancellato niente**: viene mostrato l'indirizzo email dell'account che si sta
   per eliminare e viene chiesto — facoltativamente — perché si lascia lo studio.
   Si possono selezionare più motivi o nessuno.
3. Premere **«Delete my account»** per confermare, oppure **«Keep my account»**
   per tornare indietro senza cancellare nulla.

---

# Parte 2 — Lato amministratore

L'area di amministrazione è riservata agli account con ruolo **ADMIN**. Vi si
accede automaticamente subito dopo il login, oppure dalla voce
**Settings → Administration**.

> L'account amministratore è **integrato nel sistema**: viene creato al primo avvio
> del server a partire dalla configurazione dell'installazione. Non può essere
> eliminato e il suo ruolo non può essere cambiato; se ne possono modificare
> soltanto l'email e la password.

## 2.1 Il primo accesso

Al primo accesso l'account amministratore usa ancora la password con cui è stato
creato. Il sistema **impone di sostituirla**: si viene portati alla pagina
«Administrator account» e nessun'altra pagina è raggiungibile finché non si sceglie
una nuova password.

Compilare **«Current password»** (quella di provisioning), la **nuova password**
(almeno 8 caratteri) e la sua conferma, quindi premere **«Save changes»**.

## 2.2 La dashboard

La pagina **«Administration»** è divisa in quattro sezioni.

### Participants

È la prima sezione della pagina perché è l'unica che può richiedere una decisione.
Se ci sono registrazioni in attesa compare un contatore **«N pending»** e il
pulsante diventa **«Review requests»**; altrimenti resta **«Manage participants»**.

### Overview — le metriche

| Metrica | Significato |
|---|---|
| **Registered users** | Numero totale di account registrati |
| **Active (ultimi N giorni)** | Account con almeno un accesso nella finestra (predefinito: 10 giorni) |
| **Regular (streak di N giorni)** | Account che hanno effettuato l'accesso in **ognuno** dei giorni della finestra (predefinito: 7 giorni) |
| **Activities collected** | Totale delle attività registrate da tutti i partecipanti |
| **Activities in the last 7 days** | Attività registrate nell'ultima settimana |

Le finestre temporali sono configurabili lato server.

### Activities per day

Grafico a barre delle attività registrate giorno per giorno (predefinito: ultimi 30
giorni). Passando il puntatore su una barra si legge il dettaglio del giorno; senza
puntatore la didascalia riporta il picco. La voce **«View as table»** apre gli
stessi dati in forma di tabella — utile per copiarli o per leggerli con uno screen
reader.

Se nella finestra non è stata registrata alcuna attività, il grafico è sostituito
dalla frase «No activity recorded in this window yet».

### Export data e Administrator account

Vedi [§ 2.5](#25-esportazione-dei-dati) e [§ 2.6](#26-account-amministratore).

## 2.3 Gestione dei partecipanti

Dalla dashboard, il pulsante della sezione *Participants* apre la pagina
**«Participants»**.

### La coda delle richieste in attesa

Se ci sono registrazioni da valutare, in cima alla pagina compare un riquadro
evidenziato che ne indica il numero e ricorda che **nessuno di quegli utenti può
accedere finché il suo account non è approvato**. Il pulsante **«Review them»**
filtra la lista sui soli account in attesa.

### Ricerca e filtri

- **Barra di ricerca:** cerca per email, nome o cognome.
- **Filtri di stato:** *All*, *Pending*, *Active*, *Blocked*. Ciascuno riporta il
  numero di account nello stato corrispondente; il conteggio dei *Pending* è
  evidenziato quando è maggiore di zero.

### La scheda di ogni partecipante

Ogni riga della lista riporta:

- **nome e cognome** (o «Name not provided» se il profilo è incompleto) ed
  **email**;
- lo **stato** dell'account, indicato con icona *e* parola — mai con il solo colore:
  - ⏳ **Pending** — in attesa di approvazione, non può accedere;
  - ✓ **Active** — attivo, può accedere;
  - 🔒 **Blocked** — bloccato, non può accedere;
- **Activities** — quante attività ha registrato;
- **Last activity** — la data dell'ultima attività registrata;
- **Registered** — quando si è registrato;
- **Last sign-in** — l'ultimo accesso.

La lista è paginata (25 account per pagina) e si naviga con **«Previous»** e
**«Next»**.

> Gli account amministratore non compaiono in questa lista: sono esclusi a monte,
> quindi ogni riga visibile è azionabile.

## 2.4 Le azioni sugli account

Le azioni disponibili dipendono dallo stato dell'account:

| Stato | Azioni |
|---|---|
| **Pending** | Approve · Reject · Delete |
| **Active** | Block · Delete |
| **Blocked** | Unblock · Delete |

Ogni azione apre una finestra di conferma che spiega la conseguenza, permette di
scrivere un **messaggio per l'utente** e mostra **l'anteprima esatta dell'email**
che verrà inviata.

| Azione | Effetto | Messaggio | Reversibile |
|---|---|---|---|
| **Approve** | L'account può accedere immediatamente. | Facoltativo | — |
| **Reject** | La richiesta è respinta: la persona è avvisata via email e non potrà accedere. **Nulla viene cancellato**: l'account resta in elenco come *Blocked*. | Facoltativo, ma **fortemente consigliato**: è l'unica spiegazione che la persona riceve. | Sì — si sblocca dal filtro *Blocked* |
| **Block** | La persona non può più accedere. I dati sono conservati. | Facoltativo | Sì — con *Unblock* |
| **Unblock** | La persona può accedere di nuovo. | Facoltativo | — |
| **Delete** | Cancella l'account **e tutte le attività registrate**. | **Obbligatorio** | **No** |

**Sulle email.** Ogni azione invia una notifica automatica all'interessato. Il
messaggio libero, se scritto, viene accodato al testo standard. Poiché *Reject* è
tecnicamente un blocco, l'email che parte è quella di blocco: **è proprio nel
messaggio libero che va spiegato che si trattava di un rifiuto della richiesta**.

**Sulla cancellazione.** È l'unica azione con messaggio obbligatorio: prima che
tutte le attività registrate da una persona spariscano, quella persona ha diritto a
una spiegazione. La finestra di conferma indica esattamente quante attività verranno
distrutte insieme all'account.

Se un'azione fallisce, la finestra resta aperta con il testo già scritto, così da
poter correggere e riprovare senza ricominciare.

## 2.5 Esportazione dei dati

Nella sezione **«Export data»** della dashboard si scaricano due file CSV.

### Activities CSV (`chronogram-activities.csv`)

Dati **pseudonimizzati**: identificano il partecipante con un `user_id` numerico,
non con l'email.

```
activity_id, user_id, activity_date, activity_type,
duration_mins, pleasantness, location, cost_euro, created_at
```

### Users CSV (`chronogram-users.csv`)

**Contiene dati personali: va trattato di conseguenza.**

```
user_id, email, name, surname, phone, gender, birthday, address,
weekly_income, weekly_income_other, weekly_home_cost, notes,
registered_at, last_login, account_status
```

### Come si uniscono

I due file si mettono in relazione sulla colonna **`user_id`**, presente in
entrambi. Finché si lavora sulle sole attività non serve mai il file degli utenti:
è la ragione per cui l'esportazione è divisa in due.

> I file sono codificati in UTF-8 con BOM, così Excel su Windows apre correttamente
> nomi e indirizzi con caratteri accentati.

## 2.6 Account amministratore

Dalla sezione **«Administrator account»** della dashboard si aprono le impostazioni
delle proprie credenziali. Si possono cambiare l'email, la password, o entrambe.

1. Inserire la **password attuale** (sempre obbligatoria: è ciò che dimostra che si
   è il legittimo titolare dell'account).
2. Compilare la **nuova email**, la **nuova password**, o entrambe. Lasciando un
   campo vuoto il valore corrente resta invariato.
3. Premere **«Save changes»**.

> Se si cambia l'email, alla conferma viene richiesto di **rieffettuare l'accesso**
> con il nuovo indirizzo.

La modifica è definitiva e sopravvive ai riavvii del server: la configurazione
iniziale dell'installazione non sovrascrive più i valori aggiornati da questa
pagina.

---

## Appendice A — Messaggi di errore ricorrenti

| Messaggio | Significato | Cosa fare |
|---|---|---|
| *Registration received. An administrator has to approve your account…* | Registrazione riuscita ma in attesa di approvazione. | Attendere l'email con l'esito. |
| *If your email address exists in our system, you will receive a password reset link.* | Risposta standard alla richiesta di reimpostazione — non conferma né smentisce l'esistenza dell'account. | Controllare la posta, anche nello spam. Il link vale 30 minuti. |
| *Your account has been blocked…* | L'accesso è stato sospeso da un amministratore, oppure la richiesta di registrazione è stata respinta. I dati non sono stati cancellati. | Rispondere all'amministratore che ha inviato la notifica. |
| *Duration must be between 1 and 1440 minutes* | Durata fuori intervallo (1 minuto – 24 ore). | Correggere il valore. |
| *Password must be at least 8 characters with uppercase, lowercase, number and symbol* | La password non rispetta i requisiti. | Scegliere una password conforme. |
| *Couldn't load your activities.* | Il server non ha risposto. | Premere **«Retry»**; se persiste, verificare la connessione. |
| *The AI assistant is unavailable right now.* | Il servizio di compilazione assistita non è raggiungibile o non è configurato. | Compilare il modulo manualmente. |

---

## Appendice B — Funzioni non ancora attive

Per trasparenza, alcune parti dell'interfaccia sono presenti ma non ancora
operative:

- **Notifications** (Settings → Notifications) — i tre interruttori (email, push,
  SMS) sono visibili ma le preferenze **non vengono salvate**: si azzerano
  riaprendo la pagina. Non regolano alcun invio.
- **Calendar** — esiste una schermata calendario, ma non è collegata a nessun
  pulsante dell'app e mostra dati dimostrativi, non le proprie attività. La
  consultazione avviene dalla timeline della Home.
- **Ricerca nelle FAQ** (Settings → Support) — la barra di ricerca è presente ma
  non filtra ancora le domande. Le FAQ si consultano scorrendole.
- **Personalizzazione delle categorie** — le categorie di attività sono predefinite
  e non modificabili dall'utente.

---

## Contatti

- **Contatto tecnico:** [m.molinara@unicas.it](mailto:m.molinara@unicas.it)
- **Contatto scientifico:** [nistico@unicas.it](mailto:nistico@unicas.it)
- **Università degli Studi di Cassino e del Lazio Meridionale** — [www.unicas.it](https://www.unicas.it)