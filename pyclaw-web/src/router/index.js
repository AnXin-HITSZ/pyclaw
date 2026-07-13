import { createRouter, createWebHistory } from 'vue-router'
import CoverPage from '../views/CoverPage.vue'
import ProductPage from '../views/ProductPage.vue'
import AuthPage from '../views/AuthPage.vue'
import ConsolePage from '../views/ConsolePage.vue'

const TOKEN_KEY = 'pyclaw.console.token'

const routes = [
  { path: '/', name: 'cover', component: CoverPage },
  { path: '/product', name: 'product', component: ProductPage },
  { path: '/login', name: 'login', component: AuthPage, props: { mode: 'login' } },
  { path: '/register', name: 'register', component: AuthPage, props: { mode: 'register' } },
  { path: '/console', name: 'console', component: ConsolePage },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach((to) => {
  if (to.path.startsWith('/console')) {
    const token = localStorage.getItem(TOKEN_KEY)
    if (!token) {
      return { path: '/login', query: { redirect: to.fullPath } }
    }
  }
})

export default router
