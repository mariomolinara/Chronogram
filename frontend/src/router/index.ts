import { createRouter, createWebHistory } from '@ionic/vue-router';
import { RouteRecordRaw } from 'vue-router';
import { useAuthStore } from '@/store/auth';

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
  }

];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes
});

/**
 * Guard dell'area amministrativa e del cambio password obbligatorio.
 *
 * `checkAuthStatus()` viene atteso perché al primo instradamento la sessione
 * potrebbe non essere ancora stata ripristinata dallo storage nativo: senza
 * l'await un reload su `/admin` rimbalzerebbe al login pur avendo un token
 * valido. Dopo la prima chiamata è un no-op.
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore();
  await auth.checkAuthStatus();

  if (to.meta.requiresAdmin && !(auth.isAuthenticated && auth.isAdmin)) {
    return { name: 'Login' };
  }

  // Account ancora sulla password di provisioning: nessuna altra pagina è
  // raggiungibile finché non viene sostituita.
  if (auth.isAuthenticated && auth.mustChangePassword && to.name !== 'AdminCredentials') {
    return { name: 'AdminCredentials' };
  }

  return true;
});

export default router;