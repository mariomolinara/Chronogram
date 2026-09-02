# Chronogram — User Manual

> English version. Versione italiana: [MANUALE_UTENTE.md](MANUALE_UTENTE.md)

Chronogram is the research tool of the University of Cassino and Southern Lazio for
the study of time use. Participants record their daily activities — in real time or
retrospectively — and the data collected forms the empirical ground on which the
study stands.

This manual has two parts:

- [Part 1 — For participants](#part-1--for-participants), for anyone using the app
  to record their own activities;
- [Part 2 — For administrators](#part-2--for-administrators), for whoever manages
  participants and study data.

---

## Contents

- [Getting in](#getting-in)
- [Part 1 — For participants](#part-1--for-participants)
  - [1.1 Registration](#11-registration)
  - [1.2 Account approval](#12-account-approval)
  - [1.3 Signing in](#13-signing-in)
  - [1.4 Forgotten password](#14-forgotten-password)
  - [1.5 The Home screen](#15-the-home-screen)
  - [1.6 Recording a new activity](#16-recording-a-new-activity)
  - [1.7 Filling the form with AI](#17-filling-the-form-with-ai)
  - [1.8 Editing or deleting an activity](#18-editing-or-deleting-an-activity)
  - [1.9 Settings](#19-settings)
  - [1.10 Support](#110-support)
  - [1.11 Signing out](#111-signing-out)
  - [1.12 Deleting your account](#112-deleting-your-account)
- [Part 2 — For administrators](#part-2--for-administrators)
  - [2.1 First sign-in](#21-first-sign-in)
  - [2.2 The dashboard](#22-the-dashboard)
  - [2.3 Managing participants](#23-managing-participants)
  - [2.4 Actions on an account](#24-actions-on-an-account)
  - [2.5 Exporting data](#25-exporting-data)
  - [2.6 The administrator account](#26-the-administrator-account)
- [Appendix A — Common messages](#appendix-a--common-messages)
- [Appendix B — Not yet active](#appendix-b--not-yet-active)

---

## Getting in

Chronogram comes in two forms, with the same features and the same data:

| Form | How to use it |
|---|---|
| **Web application** | Open it in a browser at the address given to you by the study coordinator. |
| **Android app** | Install it on your phone (APK or Play Store) and open it like any other app. |

The account is the same in both: record an activity on your phone and you can read
it back in the browser.

### The welcome pop-up

On first launch an **About Chronogram** window appears, with the foreword by the
scientific lead, the team and the contacts. Close it with **OK**. Tick **Don't show
this on the next start-up** before pressing OK and it will not come back. The same
information is always available from the ⓘ icon at the top left of the Home screen.

---

# Part 1 — For participants

## 1.1 Registration

From the sign-in screen press **Sign Up**. The registration form asks for:

| Field | Required | Notes |
|---|---|---|
| Name | Yes | |
| Surname | Yes | |
| Address | Yes | |
| Phone | No | |
| Email | Yes | This is also the username you sign in with |
| Password | Yes | See the requirements below |
| Confirm password | Yes | Must match the password |
| Birthday | No | Picked from the wheel, confirmed with **Done** |
| Gender | No | Male / Female / Other / Prefer not to say |

Fields marked with **\*** are required.

**Password requirements:** at least 8 characters, with at least one uppercase
letter, one lowercase letter, a digit and a symbol. The eye icon next to the field
reveals what you have typed.

The **Register** button always stays pressable: if something is missing, pressing it
shows the messages under the fields that need fixing, rather than leaving a dead
button with no explanation.

### Registering with Google

Instead of the form you can use **Sign in with Google**, where it is enabled. The
account is created on first use from the name, surname and email verified by Google
— you never choose a password. From then on you always sign in with the same button.

> Form registration is protected by reCAPTCHA. It needs nothing from you: the check
> is invisible and runs in the background.

## 1.2 Account approval

Not every registration is immediately usable.

- **Addresses on a trusted domain** (by default `unicas.it` and its sub-domains, so
  the student domain `studentmail.unicas.it` is covered too) are **approved
  automatically**: you can sign in at once.
- **Every other address** creates an account **waiting for approval**. You land on a
  *Request sent* screen, which explains that:
  1. your account has been created but cannot sign in yet;
  2. an administrator reviews the request and you receive an email with the decision;
  3. after approval you sign in with the email and password you just chose.

Trying to sign in before approval is refused, with a message explaining why.

## 1.3 Signing in

On the sign-in screen enter **Email** and **Password** and press **Login**, or use
**Sign in with Google**.

Refusals (wrong credentials, account awaiting approval, account blocked) stay
written on the page under the fields until you try again — they are not just a
notice that vanishes after a few seconds.

A session lasts **24 hours**, after which you are asked to sign in again. The
session is stored on the device, so closing and reopening the app within those 24
hours does not require your credentials again.

## 1.4 Forgotten password

1. On the sign-in screen press **Forgot Password?**.
2. Enter your account email address.
3. The system always answers with the same message — *«If your email address exists
   in our system, you will receive a password reset link»* — whether or not the
   address exists. This is deliberate: it stops a stranger from discovering which
   addresses are registered.
4. If the account exists, an email arrives with a link. **The link is valid for 30
   minutes.**
5. Opening the link takes you to the reset page, where you choose a new password
   (same requirements as at registration) and confirm it.

> At least one minute must pass between two requests for the same account. A second
> immediate request produces no new email.

## 1.5 The Home screen

Home is **today's diary**. From top to bottom it shows:

- your name;
- today's date;
- the **timeline** of the activities recorded today, each with the time it was
  recorded on the left, a coloured dot for its category, and a card with the
  activity type, any details, the location and the cost.

If there is nothing yet you see «No activities for today» and a button to add your
first one. If loading fails you see «Couldn't load your activities» with a **Retry**
button.

**The controls:**

| Position | Icon | What it does |
|---|---|---|
| Top left | ⓘ | Opens the About page |
| Top right | ⏻ | Signs out (asks for confirmation) |
| Bottom left | 🏠 | Home (current page) |
| Bottom centre | **+** | Record a new activity |
| Bottom right | ⚙ | Settings |

## 1.6 Recording a new activity

Press the **+** button in the middle of the bottom bar on Home. The **New Activity**
form opens.

| Field | Required | Values |
|---|---|---|
| **Name of Activity** | Yes | Free text: what the activity is called |
| **Duration (minutes)** | Yes | From 1 to 1440 minutes (1440 = 24 hours) |
| Details | No | Extra notes, up to 400 characters |
| **Type of activity** | Yes | One of the predefined categories (see below) |
| Pleasantness | No | From −3 to +3, with the − and + buttons. Defaults to 0 |
| **Recurrence** | Yes | *Routinary (R)* = habitual · *Exceptional (E)* = one-off |
| Cost (€) | No | What it cost |
| Location | No | At home / At work / Outside / Other |

**Available categories:** Work, Study, Leisure, Exercise, Food, Hygiene, Commute.

When you are done press **Save Activity**. If something is missing, pressing the
button highlights the fields to fix and moves the cursor to the first of them.
**Cancel** goes back without saving.

The activity is recorded with the date and time of saving and appears immediately in
the Home timeline.

## 1.7 Filling the form with AI

At the top of the New Activity form there is a **Fill with AI** card. It lets you
describe the activity in your own words and have the fields filled in for you.

**How to use it:**

1. Press **Fill with AI**: a window opens with a text box.
2. Describe the activity in plain language. The more you mention — how long it took,
   what it cost, how you felt, where you were — the more fields can be filled.
   > *Example:* «Gym session this morning, about 90 minutes, €12 for the day pass,
   > felt great afterwards — at the sports centre.»
3. Or start from one of the three ready-made examples (**Gym**, **Team meeting**,
   **Groceries**) and edit it.
4. Press **Fill the form**.
5. The window closes and the fields the AI filled stay highlighted for a few
   seconds. A reminder appears at the top of the form — *«Filled by AI. Check the
   highlighted fields before saving»* — carrying the sentence you sent, so you can
   compare it against what was filled in.
6. **Read it over and correct it**, then press **Save Activity**.

> **The AI never saves anything.** It only fills the fields: saving stays an explicit
> act by you, and you remain responsible for what gets recorded. A field you already
> filled by hand is not wiped if the AI has nothing to say about it.

**If it doesn't work:**

- *«We couldn't recognise any activity details in that text»* — the sentence held
  nothing usable. The window stays open with your text so you can fix it rather than
  start over.
- *«The AI assistant is unavailable right now»* — a technical fault, or the service
  is not configured on this installation. Close the window and fill the form
  yourself: that always works.

## 1.8 Editing or deleting an activity

From the Home timeline:

- **To edit:** tap the activity card. The form reopens with the values already
  filled in; after your corrections press **Save Activity** again.
- **To delete:** press the bin icon at the top right of the card. You are asked to
  confirm («Delete activity from HH:MM?»). Deletion is permanent.

## 1.9 Settings

Open them with the ⚙ icon at the bottom right of Home. Your name is at the top, with
a pencil icon to edit your profile. The entries are:

| Entry | What it does |
|---|---|
| **Edit Profile** | Name, surname, address, phone, date of birth, gender. The **email cannot be changed**: it identifies the account. |
| **Change Password** | Asks for your current password, the new one and its confirmation. The new password must meet the same requirements as at registration. |
| **Notifications** | Notification preferences (see [Appendix B](#appendix-b--not-yet-active)). |
| **Support** | Frequently asked questions and a form to contact support. |
| **About** | Information about the project, the team and the contacts. |
| **Administration** | *Administrators only:* opens the back office. |
| **Delete Account** | Starts the permanent deletion of your account. |

At the bottom of the page are the 🏠 (Home), ⏻ (sign out) and ⚙ (current page) icons.

## 1.10 Support

**Settings → Support.** The page contains:

- a **frequently asked questions** section that opens accordion-style;
- a **Still stuck? Help is a message away** form with two required fields:
  - **Subject** — up to 150 characters;
  - **Message** — the description of the problem, up to 2000 characters.

Pressing **Send Message** delivers it by email to the study's support mailbox. It
helps to say what you were doing, what you expected and what happened instead.

## 1.11 Signing out

The ⏻ icon is at the top right of Home and in the middle of the bottom bar in
Settings. You are asked to confirm («Sign out?»). Signing out clears the session
from the device; your recorded data stays on the server and is there when you sign
back in.

## 1.12 Deleting your account

> ⚠️ **This cannot be undone.** Your profile, your preferences and **every activity
> you recorded** are permanently erased. Nothing can be recovered.

1. **Settings → Delete Account.** The first screen explains the consequences. Press
   **Delete account** to go on, or **Go back** to cancel.
2. The **Before you go** screen opens. At this point **nothing has been deleted
   yet**: it shows the email address of the account about to be erased and asks —
   optionally — why you are leaving. Tick as many reasons as you like, or none.
3. Press **Delete my account** to confirm, or **Keep my account** to go back without
   deleting anything.

---

# Part 2 — For administrators

The administration area is reserved to accounts with the **ADMIN** role. You land on
it automatically right after signing in, or reach it from
**Settings → Administration**.

> The administrator account is **built in**: it is created at the server's first
> start from the installation configuration. It cannot be deleted and its role
> cannot change; only its email and password can be updated.

## 2.1 First sign-in

On first sign-in the administrator account still uses the password it was created
with. The system **requires you to replace it**: you are taken to the *Administrator
account* page, and no other page is reachable until you choose a new password.

Fill in **Current password** (the provisioning one), the **new password** (at least 8
characters) and its confirmation, then press **Save changes**.

## 2.2 The dashboard

The **Administration** page has four sections.

### Participants

This comes first because it is the only section that may need a decision. If
registrations are waiting you see an **N pending** counter and the button reads
**Review requests**; otherwise it reads **Manage participants**.

### Overview — the metrics

| Metric | Meaning |
|---|---|
| **Registered users** | Total number of registered accounts |
| **Active (last N days)** | Accounts with at least one sign-in in the window (default: 10 days) |
| **Regular (N-day streak)** | Accounts that signed in on **every one** of the days in the window (default: 7 days) |
| **Activities collected** | Total activities recorded by all participants |
| **Activities in the last 7 days** | Activities recorded in the past week |

The time windows are configurable server-side.

### Activities per day

A bar chart of activities recorded day by day (default: last 30 days). Hovering a bar
reads out that day's detail; with no pointer the caption reports the peak. **View as
table** opens the same data in table form — useful for copying, and for reading with
a screen reader.

If nothing was recorded in the window, the chart is replaced by «No activity recorded
in this window yet».

### Export data and Administrator account

See [§ 2.5](#25-exporting-data) and [§ 2.6](#26-the-administrator-account).

## 2.3 Managing participants

From the dashboard, the *Participants* button opens the **Participants** page.

### The pending queue

If there are registrations to decide on, a highlighted panel at the top of the page
gives their number and reminds you that **none of those people can sign in until
their account is approved**. **Review them** filters the list down to the pending
accounts.

### Search and filters

- **Search bar:** searches by email, name or surname.
- **Status filters:** *All*, *Pending*, *Active*, *Blocked*. Each carries the number
  of accounts in that state; the *Pending* count is highlighted whenever it is above
  zero.

### Each participant's card

Every row shows:

- **name and surname** (or «Name not provided» if the profile is incomplete) and the
  **email**;
- the account **status**, given as an icon *and* a word — never colour alone:
  - ⏳ **Pending** — waiting for approval, cannot sign in;
  - ✓ **Active** — can sign in;
  - 🔒 **Blocked** — cannot sign in;
- **Activities** — how many they have recorded;
- **Last activity** — the date of their most recent one;
- **Registered** — when they signed up;
- **Last sign-in** — their most recent sign-in.

The list is paginated (25 accounts per page), navigated with **Previous** and
**Next**.

> Administrator accounts never appear in this list: they are excluded upstream, so
> every visible row is actionable.

## 2.4 Actions on an account

Which actions are available depends on the account's state:

| State | Actions |
|---|---|
| **Pending** | Approve · Reject · Delete |
| **Active** | Block · Delete |
| **Blocked** | Unblock · Delete |

Every action opens a confirmation window that spells out the consequence, lets you
write a **message to the user**, and shows **an exact preview of the email** that
will be sent.

| Action | Effect | Message | Reversible |
|---|---|---|---|
| **Approve** | The account can sign in immediately. | Optional | — |
| **Reject** | The request is turned down: the person is notified by email and cannot sign in. **Nothing is deleted** — the account stays in the list as *Blocked*. | Optional but **strongly advised**: it is the only explanation the person gets. | Yes — unblock it from the *Blocked* filter |
| **Block** | The person can no longer sign in. Their data is kept. | Optional | Yes — with *Unblock* |
| **Unblock** | The person can sign in again. | Optional | — |
| **Delete** | Erases the account **and every activity recorded**. | **Required** | **No** |

**About the emails.** Every action sends an automatic notice to the person concerned.
Your free-text message, if written, is appended to the standard wording. Because
*Reject* is technically a block, the email that goes out is the block one:
**the free-text message is where you must explain that this was a refusal of the
request**.

**About deletion.** It is the only action with a mandatory message: before everything
a person recorded disappears, they are owed an explanation. The confirmation window
states exactly how many activities will be destroyed along with the account.

If an action fails, the window stays open with the text you already wrote, so you can
correct it and retry without starting over.

## 2.5 Exporting data

The dashboard's **Export data** section downloads two CSV files.

### Activities CSV (`chronogram-activities.csv`)

**Pseudonymised** data: it identifies the participant by a numeric `user_id`, not by
email.

```
activity_id, user_id, activity_date, activity_type,
duration_mins, pleasantness, location, cost_euro, created_at
```

### Users CSV (`chronogram-users.csv`)

**This contains personal data — handle it accordingly.**

```
user_id, email, name, surname, phone, gender, birthday, address,
weekly_income, weekly_income_other, weekly_home_cost, notes,
registered_at, last_login, account_status
```

### Joining them

The two files join on the **`user_id`** column, present in both. As long as you work
on the activities alone you never need the users file — which is exactly why the
export is split in two.

> Both files are UTF-8 with a BOM, so Excel on Windows opens accented names and
> addresses correctly.

## 2.6 The administrator account

The dashboard's **Administrator account** section opens your own credential settings.
You can change the email, the password, or both.

1. Enter your **current password** (always required: it is what proves the account is
   yours).
2. Fill in the **new email**, the **new password**, or both. Leaving a field empty
   keeps its current value.
3. Press **Save changes**.

> If you change the email, you are asked to **sign in again** with the new address.

The change is permanent and survives server restarts: the installation's initial
configuration no longer overwrites values updated from this page.

---

## Appendix A — Common messages

| Message | Meaning | What to do |
|---|---|---|
| *Registration received. An administrator has to approve your account…* | Registration succeeded but is awaiting approval. | Wait for the decision email. |
| *If your email address exists in our system, you will receive a password reset link.* | The standard answer to a reset request — it neither confirms nor denies that the account exists. | Check your inbox, and your spam folder. The link is valid for 30 minutes. |
| *Your account has been blocked…* | An administrator suspended your access, or your registration request was refused. Your data has not been deleted. | Reply to the administrator who sent the notice. |
| *Duration must be between 1 and 1440 minutes* | Duration out of range (1 minute – 24 hours). | Correct the value. |
| *Password must be at least 8 characters with uppercase, lowercase, number and symbol* | The password does not meet the requirements. | Choose a compliant password. |
| *Couldn't load your activities.* | The server did not answer. | Press **Retry**; if it persists, check your connection. |
| *The AI assistant is unavailable right now.* | The assisted-fill service is unreachable or not configured. | Fill the form manually. |

---

## Appendix B — Not yet active

For transparency, some parts of the interface are present but not yet operational:

- **Notifications** (Settings → Notifications) — the three switches (email, push,
  SMS) are visible but the preferences **are not saved**: they reset when you reopen
  the page, and they do not control any sending.
- **Calendar** — a calendar screen exists, but it is not linked from anywhere in the
  app and shows demonstration data rather than your own activities. Browsing happens
  through the Home timeline.
- **FAQ search** (Settings → Support) — the search bar is there but does not filter
  the questions yet. Scroll through the FAQs instead.
- **Custom categories** — activity categories are predefined and cannot be edited by
  users.

---

## Contacts

- **Technical contact:** [m.molinara@unicas.it](mailto:m.molinara@unicas.it)
- **Research contact:** [nistico@unicas.it](mailto:nistico@unicas.it)
- **University of Cassino and Southern Lazio** — [www.unicas.it](https://www.unicas.it)