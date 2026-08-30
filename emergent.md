# Chronogram — Specifica dei requisiti

Documento di specifica per la realizzazione, tramite intelligenza artificiale, di una riproduzione completa del sistema **Chronogram**: un'applicazione mobile per la registrazione delle attività quotidiane degli utenti, con un backend centralizzato per il collezionamento dei dati a fini di ricerca sull'uso del tempo (time-use study).

Il documento descrive **esclusivamente l'obiettivo finale in termini di requisiti funzionali e di comportamento**. Nessuna scelta tecnologica è prescritta: architettura, linguaggi, framework e infrastruttura sono liberi, purché il sistema risultante soddisfi quanto segue.

---

## 1. Obiettivo e contesto

- Il sistema serve a uno **studio di ricerca universitario sull'uso del tempo**: i partecipanti registrano le proprie attività quotidiane (cosa hanno fatto, per quanto tempo, dove, a che costo, con quale piacevolezza) e i ricercatori raccolgono ed esportano i dati in forma pseudonimizzata.
- Il sistema è composto da:
  1. un'**app per i partecipanti** (utilizzabile da smartphone, in particolare Android, e da browser) per registrare e consultare le attività;
  2. un **backend centralizzato** che raccoglie e conserva i dati di tutti i partecipanti, gestisce gli account e offre un'**area di amministrazione** per i ricercatori.
- La registrazione delle attività deve essere possibile sia **in tempo reale** (registro ciò che sto facendo ora) sia **retrospettiva** (registro o correggo attività di giorni passati).
- Lingua dell'interfaccia: **inglese**. Tema grafico: **scuro**, moderno e curato.

## 2. Attori

| Attore | Descrizione |
|---|---|
| **Partecipante (USER)** | Si registra autonomamente, registra le proprie attività, vede e modifica solo i propri dati. |
| **Amministratore (ADMIN)** | Ricercatore/gestore dello studio: approva e gestisce i partecipanti, consulta le metriche di partecipazione, esporta i dati. |
| **Amministratore di sistema integrato** | Unico account non auto-registrato, creato automaticamente al primo avvio del sistema da configurazione; non cancellabile e non declassabile. |

## 3. Account e autenticazione

### 3.1 Registrazione
- Auto-registrazione con: **Nome\***, **Cognome\***, **Indirizzo\***, Telefono, **Email\***, **Password\***, **Conferma password\***, Data di nascita (selettore, nessuna data futura, intervallo dal 1900 a oggi), Genere (Maschio / Femmina / Altro / Preferisco non specificare).
- **Regola di robustezza password** (identica lato app e lato backend): minimo 8 caratteri, massimo 72, con almeno una maiuscola, una minuscola, una cifra e un simbolo.
- La registrazione è protetta da una **verifica anti-bot invisibile**: se la verifica rifiuta la richiesta l'utente riceve un messaggio chiaro; se il servizio di verifica è irraggiungibile la registrazione è temporaneamente sospesa (si preferisce bloccare piuttosto che restare scoperti). In fondo alla schermata compare l'attribuzione del servizio con link a Privacy Policy e Termini di servizio.
- **Approvazione delle registrazioni**: gli account hanno uno stato di ciclo di vita — `PENDING` (in attesa di approvazione), `ACTIVE` (può accedere), `BLOCKED` (disabilitato, reversibile, dati conservati). Le email appartenenti a una **lista configurabile di domini fidati** (sottodomini inclusi, mai domini "somiglianti") diventano `ACTIVE` subito; tutte le altre nascono `PENDING` e sia il richiedente sia l'amministratore vengono avvisati via email. Esito `PENDING`: schermata dedicata "richiesta inviata" che spiega che l'account esiste ma non è ancora abilitato. Esito `ACTIVE`: si prosegue verso il login.

### 3.2 Accesso
- **Login con email e password.** Messaggio identico per utente inesistente e password errata ("Invalid credentials"): il sistema non deve mai rivelare se un indirizzo è registrato. Lo **stato dell'account viene rivelato solo dopo la verifica della password**: account in attesa → messaggio "in attesa di approvazione"; account bloccato → messaggio "contatta l'amministratore".
- **Protezione anti forza bruta**: dopo 5 tentativi falliti l'account è bloccato per 15 minuti con messaggio dedicato; il contatore si azzera al login riuscito.
- **Accesso con Google** (visibile solo se configurato), valido sia come registrazione sia come login: al primo accesso l'account viene creato con nome, cognome ed email garantiti da Google; se esiste già un account locale con la stessa email i due vengono collegati e condividono gli stessi dati. Anche gli account Google seguono la regola di approvazione per dominio. Un account nato da Google non ha password locale finché non ne imposta una tramite il recupero password.
- **Sessione**: durata 24 ore, ricordata tra i riavvii dell'app; logout sempre con conferma. Il blocco di un account da parte dell'amministratore **invalida immediatamente le sessioni già attive**.
- Dopo il login: il partecipante atterra sul diario, l'amministratore sulla dashboard. Un utente non autenticato che apre un indirizzo protetto viene mandato al login e, dopo l'accesso, riportato alla destinazione originaria.

### 3.3 Recupero e cambio password
- **Password dimenticata**: si inserisce l'email; la risposta è sempre identica, che l'account esista o no. L'email inviata contiene un link di reset che **scade dopo 30 minuti**; una seconda richiesta entro **60 secondi** viene ignorata silenziosamente. Il reset con token non valido o scaduto produce un errore esplicito.
- **Cambio password** (utente autenticato): richiede la password attuale; la nuova deve rispettare la regola di robustezza ed essere diversa dall'attuale; tutti i campi hanno mostra/nascondi.
- L'amministratore integrato al primo accesso è **obbligato a cambiare la password** prima di poter fare qualsiasi altra cosa.

## 4. Il diario delle attività (cuore del sistema)

### 4.1 Modello dell'attività
Ogni attività registrata appartiene a un solo utente e comprende:

| Campo | Obbligatorio | Descrizione |
|---|---|---|
| Nome | sì | Testo libero che descrive l'attività. **Deve essere persistito.** |
| Data | sì | Giorno a cui l'attività si riferisce. Di default "oggi", ma **deve poter essere impostata/corretta** per consentire la registrazione retrospettiva. |
| Durata | sì | In minuti, da 1 a 1440. |
| Categoria | sì | Una dal catalogo predefinito (v. 4.2). |
| Ricorrenza | sì | **Routinaria (R)** o **Eccezionale (E)**, riferita alla singola attività. **Deve essere persistita.** |
| Piacevolezza | no | Scala intera da **−3 a +3** (default 0), regolata con controlli +/−. |
| Costo | no | In euro, ≥ 0; l'input accetta la virgola come separatore decimale. |
| Luogo | no | Uno tra: A casa / Al lavoro / Fuori / Altro. |
| Note | no | Testo libero aggiuntivo. **Deve essere persistito.** |
| Orario di inserimento | automatico | Registrato dal sistema e mostrato nella timeline. |

- L'appartenenza al proprietario è **sempre determinata dalla sessione**, mai da un identificativo inviato dal client. Un'attività altrui risponde come inesistente ("not found or access denied").
- Operazioni: creazione, modifica (parziale: i campi non toccati restano invariati), cancellazione con conferma, elenco per giorno.

### 4.2 Catalogo delle categorie
Catalogo **predefinito e non modificabile dagli utenti**, con almeno queste 7 categorie precaricate (ognuna con descrizione, indicatore "strumentale sì/no", indicatore "tipicamente routinaria sì/no" e un colore distintivo usato nella timeline):

| Categoria | Descrizione | Strumentale | Routinaria |
|---|---|---|---|
| Work | Attività lavorative | sì | sì |
| Study | Studio e apprendimento | sì | sì |
| Leisure | Tempo libero e relax | no | no |
| Exercise | Sport e attività fisica | no | sì |
| Food | Pasti e alimentazione | sì | sì |
| Hygiene | Cura personale e igiene | sì | sì |
| Commute | Spostamenti e pendolarismo | sì | sì |

### 4.3 Schermata principale (diario del giorno)
- Mostra il nome dell'utente, la data corrente e una **timeline verticale** delle attività del giorno: orario di inserimento, pallino colorato per categoria, scheda con nome dell'attività, categoria, luogo e costo.
- Tap su una scheda → modifica; icona cestino → cancellazione con conferma che cita l'orario ("Delete activity from HH:mm?").
- Tre stati espliciti: caricamento, errore con pulsante "Retry", vuoto con invito "Add your first activity".
- Barra inferiore con: Home, **pulsante centrale "+"** per una nuova attività, Impostazioni. In alto: accesso alla pagina About e Logout con conferma.
- **Navigazione tra i giorni**: l'utente deve poter spostarsi ai giorni precedenti (e tornare a oggi) per consultare, aggiungere e correggere attività passate — tramite un calendario o controlli equivalenti collegati al diario reale.

### 4.4 Inserimento e modifica di un'attività
Schermata unica "New Activity" (riusata precompilata per la modifica), con i campi nell'ordine: Nome\*, Durata\*, Note, Categoria\*, Piacevolezza (+/−), Ricorrenza\*, Costo (€), Luogo. Pulsanti "Cancel" e "Save"; al salvataggio un toast di conferma e ritorno al diario aggiornato.

Gestione degli errori di compilazione (vale per tutti i form del sistema):
- il pulsante di invio è **sempre premibile**; alla pressione compaiono gli errori inline sui campi mancanti/invalidi;
- il focus va al primo campo invalido e un toast riassume quanti campi correggere;
- gli errori si aggiornano man mano che l'utente corregge;
- i campi obbligatori sono contrassegnati con `*` e una legenda.

### 4.5 Compilazione assistita dall'intelligenza artificiale ("Fill with AI")
- In cima al form una scheda invita a **descrivere l'attività in parole semplici**: "we fill the fields, you review and save".
- Si apre una finestra con un'area di testo (con esempio segnaposto) e **tre esempi pronti cliccabili** (es. sessione in palestra con durata e costo; riunione di lavoro; spesa al supermercato).
- Il testo libero viene inviato al backend, che tramite un **assistente di intelligenza artificiale** estrae i campi strutturati: nome, durata in minuti, note, piacevolezza (−3…+3), categoria (risolta rispetto al catalogo, senza distinzione maiuscole/minuscole; se non corrisponde resta vuota), ricorrenza (solo R o E), costo, luogo (normalizzato con sinonimi: home/house→A casa; work/office→Al lavoro; outside/outdoors/out→Fuori; qualsiasi altro→Altro).
- Regole di applicazione al form: **un campo non estratto non azzera** quanto già scritto; la durata è accettata solo se > 0; la piacevolezza è limitata all'intervallo; il costo è ripulito da simboli di valuta e la virgola diventa punto.
- Tre esiti distinti, ciascuno col proprio comportamento:
  1. **campi estratti** → la finestra si chiude, i campi compilati vengono **evidenziati per alcuni secondi**, resta un **banner persistente "rivedi prima di salvare"** che riporta la frase usata (finché non si salva o lo si chiude), leggera vibrazione su mobile, toast differenziato a seconda che il form risulti completo o no;
  2. **nessun campo riconosciuto** → la finestra resta aperta col testo dell'utente e un suggerimento su come riformulare (dire cosa si è fatto, quanto è durato, quanto è costato, come ci si è sentiti);
  3. **servizio non disponibile** (non configurato, irraggiungibile, risposta inutilizzabile) → messaggio generico "assistente temporaneamente non disponibile, compila manualmente", senza mai esporre dettagli tecnici.
- L'estrazione **non salva mai nulla da sola**: compila soltanto il modulo, la conferma resta all'utente. La funzione è riservata agli utenti autenticati.

## 5. Profilo, impostazioni e account

- **Impostazioni**: intestazione con saluto e nome; voci: Modifica profilo, Cambia password, Notifiche, Supporto, About, Amministrazione (visibile solo agli ADMIN), Elimina account.
- **Modifica profilo**: Nome\*, Cognome\*, Indirizzo\*, Telefono, Email in sola lettura ("Email cannot be changed"), Data di nascita, Genere. Il profilo prevede anche campi socio-economici per lo studio — **reddito settimanale, altra fonte di reddito settimanale, costo settimanale dell'abitazione, note** — che devono essere raccoglibili dall'app ed esportati.
- **Notifiche**: pagina preferenze con interruttori per notifiche Email / Push / SMS; le preferenze devono essere **persistite** per usi futuri (l'invio effettivo di promemoria non è richiesto in questa versione).
- **Supporto**: introduzione, FAQ ad accordion (come aggiungere un'attività; come modificare un'attività passata; personalizzazione categorie) e un form di contatto (Oggetto\* max 150, Messaggio\* max 2000) che invia un'email al team di supporto.
- **About**: pagina con la prefazione del responsabile scientifico, lo scopo dello studio sull'uso del tempo, il team con ruoli e contatti. Lo stesso contenuto compare come **popup al primo avvio** con casella "non mostrare più" (unica preferenza locale persistita). La pagina About è accessibile anche **senza account** (dal login).
- **Eliminazione account** in due passi: (1) avviso di irreversibilità con l'elenco di ciò che sarà cancellato; (2) schermata "Before you go" che mostra l'email dell'account, propone **motivazioni facoltative a scelta multipla** (non uso l'app; alternativa migliore; troppa pubblicità; funzionalità mancanti; qualità dei contenuti; app difficile da navigare; altro), chiede una conferma finale esplicita e poi cancella definitivamente tutto (profilo, attività, storico accessi, token), inviando un'email di conferma con l'avvertenza "se non sei stato tu, la tua sessione è stata compromessa".

## 6. Area di amministrazione (backend di collezionamento dati)

### 6.1 Dashboard
- Pannello "Participants" con badge del numero di richieste **in attesa** e invito contestuale ("Review requests" se ce ne sono, altrimenti "Manage participants").
- **Metriche di partecipazione** (gli amministratori sono sempre esclusi dai conteggi):
  - utenti registrati;
  - **attivi**: almeno un accesso negli ultimi N giorni (default 10, configurabile);
  - **regolari**: accesso in **ognuno** degli ultimi M giorni (default 7, configurabile);
  - attività raccolte in totale e negli ultimi 7 giorni.
- **Grafico a barre "attività per giorno"** sugli ultimi 30 giorni (configurabile), con i giorni senza attività a zero, lettura puntuale al passaggio del puntatore e messaggio dedicato quando la finestra è vuota.
- Le metriche di attività/regolarità si basano su uno **storico degli accessi** registrato dal sistema (ogni login riuscito: utente + istante).

### 6.2 Gestione dei partecipanti
- Elenco con **ricerca libera** (email, nome, cognome), **filtro per stato** con contatori globali per stato, **paginazione** (25 per pagina, massimo 100).
- Per ogni riga: nome e cognome, email, **stato con icona + parola** (leggibile anche senza il colore), numero di attività registrate, data dell'ultima attività, data/ora di registrazione, ultimo accesso.
- **Azioni dipendenti dallo stato**: `PENDING` → Approva, Rifiuta, Elimina; `ACTIVE` → Blocca, Elimina; `BLOCKED` → Sblocca, Elimina. "Rifiuta" respinge la richiesta portando l'account a bloccato conservandone i dati, con testo di conferma distinto da "Blocca".
- Ogni azione apre un **dialogo di conferma** con l'email bersaglio, la spiegazione, l'indicazione di reversibilità e un **campo messaggio per l'utente**: facoltativo per approva/rifiuta/blocca/sblocca, **obbligatorio per l'eliminazione** (max 1000 caratteri). Il dialogo di eliminazione mostra in evidenza **quante attività verranno distrutte**.
- Ogni azione **notifica l'utente via email** (approvato con link per entrare / bloccato con "i tuoi dati non sono stati cancellati" / eliminato, riportando l'eventuale messaggio dell'amministratore). Un guasto della posta **non annulla mai** la decisione: viene registrato e l'azione resta valida.
- Gli amministratori, l'account di sistema e l'account di chi sta operando **non compaiono nella lista**: il back office non può auto-escludersi o restare senza accesso.
- L'eliminazione è irreversibile e cancella a cascata profilo, attività, storico accessi e token pendenti.
- **Credenziali amministratore**: pagina dedicata per cambiare email e/o password del proprio account admin (password attuale obbligatoria, almeno uno dei due nuovi valori); ruolo, stato e flag di sistema non sono modificabili.

### 6.3 Esportazione dei dati
Due file CSV scaricabili dalla dashboard, entrambi codificati in modo che i caratteri accentati siano letti correttamente dai fogli di calcolo:

1. **Attività** — **pseudonimizzato**: identificativo numerico dell'utente, mai l'email. Colonne: identificativo attività, identificativo utente, data, categoria, nome dell'attività, durata in minuti, piacevolezza, ricorrenza, luogo, costo, note, istante di creazione.
2. **Utenti** — dati anagrafici e di account, da congiungere alle attività tramite l'identificativo utente: identificativo, email, nome, cognome, telefono, genere, data di nascita, indirizzo, reddito settimanale, altra fonte di reddito, costo settimanale dell'abitazione, note, data di registrazione, ultimo accesso, stato dell'account.

L'interfaccia deve avvertire esplicitamente che il file utenti contiene dati personali e che quello delle attività è pseudonimizzato.

## 7. Email inviate dal sistema

Il sistema invia email transazionali per: reset password (link con scadenza); registrazione ricevuta in attesa di approvazione; account approvato (con link per accedere); account bloccato; account eliminato dall'amministratore; account eliminato su richiesta dell'utente; avviso all'amministratore di una nuova registrazione in attesa (attivabile/disattivabile da configurazione).

## 8. Requisiti trasversali

- **Riservatezza**: ogni partecipante vede e modifica esclusivamente i propri dati; l'identità è sempre derivata dalla sessione autenticata. Le password sono conservate solo in forma irreversibilmente cifrata. Nessun messaggio del sistema deve permettere di scoprire se un'email è registrata.
- **Configurabilità** (senza modifiche al codice): domini fidati per l'auto-approvazione, credenziali iniziali dell'amministratore integrato, finestre temporali delle metriche, parametri del servizio email, del servizio anti-bot, dell'accesso Google e dell'assistente di intelligenza artificiale (tutte le integrazioni esterne devono degradare con grazia se non configurate).
- **Accessibilità**: stati comunicati con icona + testo oltre che con il colore; aree tattili di dimensione adeguata; navigazione da tastiera sugli elementi principali; messaggi di errore vicini ai campi; contrasto adeguato al tema scuro.
- **Robustezza**: ogni lista/pagina dati ha tre stati espliciti (caricamento, errore con possibilità di riprovare, vuoto con azione suggerita); i guasti dei servizi esterni (email, anti-bot, assistente AI) producono messaggi comprensibili senza mai esporre dettagli tecnici.
- **Integrità dei dati**: schema dei dati versionato e migrabile automaticamente; timestamp di creazione/aggiornamento su tutte le entità principali; cancellazioni a cascata coerenti.
- **Piattaforme**: l'app deve funzionare su smartphone Android (installabile) e da browser desktop/mobile con la stessa base funzionale.

## 9. Criteri di accettazione (sintesi)

1. Un nuovo utente con email di dominio fidato si registra, accede subito e registra un'attività completa (tutti i campi, inclusi nome, note e ricorrenza) che ritrova nel diario e, dopo logout/login, ancora intatta.
2. Un utente con email esterna resta `PENDING`, non può accedere (messaggio dedicato dopo password corretta), riceve email all'approvazione e da quel momento accede.
3. L'utente può spostarsi a un giorno passato, aggiungere lì un'attività e correggerne una esistente.
4. La descrizione "Ho fatto 90 minuti di palestra stamattina, 12 € di ingresso, mi sono sentito benissimo" precompila durata, costo, piacevolezza positiva, categoria Exercise e luogo; nulla viene salvato finché l'utente non preme Save.
5. Cinque login falliti bloccano l'account per 15 minuti; il blocco amministrativo di un account estromette immediatamente una sessione già aperta.
6. L'amministratore integrato al primo accesso è forzato al cambio password; approva un utente in attesa (che riceve l'email); esporta i due CSV e le attività risultano pseudonimizzate.
7. L'eliminazione dell'account da parte dell'utente rimuove ogni suo dato e invia l'email di conferma; l'eliminazione da parte dell'admin richiede un messaggio obbligatorio.
