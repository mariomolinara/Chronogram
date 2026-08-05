import { createRouter, createWebHistory } from '@ionic/vue-router';
import { RouteLocationNormalized, RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/store/auth';

/**
 * Le uniche pagine raggiungibili SENZA sessione.
 *
 * L'elenco è per nome e in negativo (tutto ciò che non è qui dentro richiede il
 * login) perché l'alternativa — marcare `requiresAuth` rotta per rotta — mette
 * ogni pagina futura al riparo solo se qualcuno si ricorda di aggiungere il
 * flag. Qui una rotta nuova nasce protetta e va scoperta di proposito.
 */
const PUBLIC_ROUTE_NAMES: ReadonlySet<string> = new Set([
  'Login',
  'Register',
  'ForgotPassword',
  'ResetPassword'
]);

const isPublicRoute = (route: RouteLocationNormalized): boolean =>
    typeof route.name === 'string' && PUBLIC_ROUTE_NAMES.has(route.name);

/**
 * Destinazione da riaprire dopo il login, presa dalla query `redirect`.
 *
 * Si accettano solo path interni: un valore che comincia per `//` o che porta
 * uno schema (`https:`, `javascript:`) verrebbe risolto dal browser come URL
 * assoluto e trasformerebbe il login in un open redirect verso un sito terzo.
 */
export function safeRedirectTarget(value: unknown): string | null {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//')) {
    return null;
  }
  return value;
}

// NOTA: Gli import delle pagine non sono più necessari qui sopra,
// perché li carichiamo dinamicamente ("lazy loading") qui sotto.

const routes: Array<RouteRecordRaw> = [
  {
    path: '/',
    redirect: '/login'// Imposta la pagina di login come predefinita
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/LoginPage.vue')
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/HomePage.vue')
  },
  {
    path: '/activity',
    name: 'AddActivity',
    component: () => import('@/views/AddActivityPage.vue')
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/SettingsPage.vue')
  },
  // --- ROTTA AGGIUNTA ---
  {
    path: '/register',
    name: 'Register', // Questo è il nome che abbiamo usato in LoginPage.vue
    component: () => import('@/views/RegistrationPage.vue')
  },

  {
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPasswordPage.vue')
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/ResetPasswordPage.vue')
  },
  {
    path: '/calendar',
    name: 'Calendar',
    component: () => import('@/views/CalendarPage.vue')
  },
  {
    path: '/support',
    name: 'Support',
    component: () => import('@/views/SupportPage.vue')
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('@/views/ChangePassword.vue')
  },
  {
    path: '/edit-profile',
    name: 'EditProfile',
    component: () => import('@/views/EditProfile.vue')
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: () => import('@/views/Notifications.vue')
  },
  {
    path: '/delete-account',
    name: 'DeleteAccount',
    component: () => import('@/views/DeleteAccount.vue')
  },
  {
    path: '/delete-reasons',
    name: 'DeleteReasons',
    component: () => import('@/views/DeleteReasons.vue')
  },
  {
    path: '/details',
    name: 'Details',
    component: () => import('@/views/DetailsPage.vue')
  },

  // --- AREA AMMINISTRAZIONE ---
  // Visibile solo al ruolo ADMIN. Il guard qui sotto è una comodità di UX: la
  // vera protezione è `hasRole('ADMIN')` su `/api/admin/**` lato backend.
  {
    path: '/admin',
    name: 'AdminDashboard',
    component: () => import('@/views/admin/AdminDashboardPage.vue'),
    meta: { requiresAdmin: true }
  },
  {
    path: '/admin/users',
    name: 'AdminUsers',
    component: () => import('@/views/admin/AdminUsersPage.vue'),
    meta: { requiresAdmin: true }
  },
  {
    path: '/admin/credentials',
    name: 'AdminCredentials',
    component: () => import('@/views/admin/AdminCredentialsPage.vue'),
    meta: { requiresAdmin: true }
  },

  // Qualsiasi URL sconosciuto (link vecchio, refuso) finisce sulla root invece
  // di lasciare l'app montata su una rotta inesistente, cioè su una pagina
  // vuota: nginx e il WAR servono index.html per ogni path sotto /chronogram,
  // quindi senza catch-all la SPA si carica e non instrada nulla.
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }

];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

/**
 * Guard di sessione, area amministrativa e cambio password obbligatorio.
 *
 * `checkAuthStatus()` viene atteso perché al primo instradamento la sessione
 * potrebbe non essere ancora stata ripristinata dallo storage nativo: senza
 * l'await un reload su `/admin` rimbalzerebbe al login pur avendo un token
 * valido. Dopo la prima chiamata è un no-op.
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.checkAuthStatus();

  // Nessuna sessione su una pagina privata: si torna al login PRIMA di montarla.
  //
  // Prima questo caso era affidato al 401 della prima chiamata API, cioè a un
  // giro di rete e all'interceptor axios: aprendo un deep link (es.
  // /chronogram/activity) in un browser senza più la sessione — incognito
  // richiuso, storage pulito — la pagina privata veniva comunque montata e
  // l'utente restava lì, con un toast che spariva dopo tre secondi e nessun
  // redirect (riprodotto in produzione: POST /api/activities/types -> 401,
  // URL fermo su /chronogram/activity). Un redirect deciso qui non dipende
  // né dalla rete né dal ciclo di vita dell'`ion-router-outlet`.
  if (!isPublicRoute(to) && !auth.isAuthenticated) {
    // `fullPath` conserva query e hash, così dopo il login si riapre esattamente
    // la pagina richiesta invece della home.
    return { name: 'Login', query: { redirect: to.fullPath } };
  }

  if (to.meta.requiresAdmin && !(auth.isAuthenticated && auth.isAdmin)) {
    return { name: 'Login' };
  }

  // Account ancora sulla password di provisioning: nessuna altra pagina è
  // raggiungibile finché non viene sostituita.
  if (auth.isAuthenticated && auth.mustChangePassword && to.name !== 'AdminCredentials') {
    return { name: 'AdminCredentials' };
  }

  // Sessione valida e login richiesto: non ha senso chiedere di nuovo le
  // credenziali. È il caso normale della riapertura dell'app (la root instrada
  // su /login) e di un URL sbagliato, che la rotta catch-all porta qui.
  if (to.name === 'Login' && auth.isAuthenticated) {
    return { name: auth.isAdmin ? 'AdminDashboard' : 'Home' };
  }

  return true;
});

export default router;