import { createRouter, createWebHistory } from 'vue-router'

import { useUserStore } from '@/stores/user'

declare module 'vue-router' {
  interface RouteMeta {
    title?: string
    requiresAuth?: boolean
    /** 不渲染全站顶栏 / 页脚（登录、选座、订单确认） */
    fullBleed?: boolean
  }
}

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      name: 'home',
      component: () => import('@/views/HomeView.vue'),
      meta: { title: '首页' },
    },
    {
      path: '/movies',
      name: 'movies',
      component: () => import('@/views/MoviesView.vue'),
      meta: { title: '电影' },
    },
    {
      path: '/cinemas',
      name: 'cinemas',
      component: () => import('@/views/CinemasView.vue'),
      meta: { title: '影院' },
    },
    {
      path: '/cinema/:id',
      name: 'cinema-detail',
      component: () => import('@/views/CinemaDetailView.vue'),
      meta: { title: '影院详情' },
    },
    {
      path: '/search',
      name: 'search',
      component: () => import('@/views/SearchView.vue'),
      meta: { title: '搜索电影' },
    },
    {
      path: '/auth',
      name: 'auth',
      component: () => import('@/views/AuthView.vue'),
      meta: { title: '登录 / 注册', fullBleed: true },
    },
    { path: '/login', redirect: { name: 'auth' } },
    {
      path: '/movie/:id',
      name: 'movie-detail',
      component: () => import('@/views/MovieDetailView.vue'),
      meta: { title: '电影详情' },
    },
    {
      path: '/movie/:movieId/buy',
      name: 'seat-select',
      component: () => import('@/views/SeatSelectView.vue'),
      meta: { title: '选择场次座位', fullBleed: true },
    },
    {
      path: '/order/:orderId/confirm',
      name: 'order-confirm',
      component: () => import('@/views/OrderConfirmView.vue'),
      meta: { title: '确认订单', requiresAuth: true, fullBleed: true },
    },
    {
      path: '/orders',
      name: 'my-orders',
      component: () => import('@/views/MyOrderView.vue'),
      meta: { title: '我的订单', requiresAuth: true },
    },
    {
      path: '/profile',
      name: 'profile',
      component: () => import('@/views/ProfileView.vue'),
      meta: { title: '个人中心', requiresAuth: true },
    },
    {
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('@/views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
  scrollBehavior() {
    return { top: 0 }
  },
})

router.beforeEach((to) => {
  const userStore = useUserStore()
  userStore.restore()

  if (to.meta.requiresAuth && !userStore.isLoggedIn) {
    return { name: 'auth', query: { redirect: to.fullPath } }
  }

  if (to.name === 'auth' && userStore.isLoggedIn) {
    return { name: 'home' }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title ?? 'Movie Ticker'
  document.title = `${title} · Movie Ticker`
})

export default router
