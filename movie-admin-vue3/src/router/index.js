import { createRouter, createWebHistory } from 'vue-router';

import { useUserStore } from '@/stores/user';
import Layout from '@/layout/Layout.vue';

// 公开路由（不需要登录）
const publicRoutes = [
  {
    path: '/login',
    name: 'login',
    component: () => import('@/views/login/LoginView.vue'),
    meta: { title: '登录' },
  },
];

// 受保护路由（需要登录）
const protectedRoutes = [
  {
    path: '/',
    component: Layout,
    redirect: '/dashboard',
    children: [
      {
        path: 'dashboard',
        name: 'dashboard',
        component: () => import('@/views/dashboard/DashboardView.vue'),
        meta: { title: '仪表盘', icon: 'Odometer' },
      },
    ],
  },
  {
    path: '/movie',
    component: Layout,
    redirect: '/movie/list',
    meta: { title: '电影管理', icon: 'Film' },
    children: [
      {
        path: 'list',
        name: 'movie-list',
        component: () => import('@/views/movie/MovieList.vue'),
        meta: { title: '电影列表', icon: 'VideoPlay' },
      },
      {
        path: 'create',
        name: 'movie-create',
        component: () => import('@/views/movie/MovieForm.vue'),
        meta: { title: '新增电影', icon: 'Plus', hidden: true },
      },
      {
        path: 'edit/:id',
        name: 'movie-edit',
        component: () => import('@/views/movie/MovieForm.vue'),
        meta: { title: '编辑电影', icon: 'Edit', hidden: true },
      },
    ],
  },
  {
    path: '/actor',
    component: Layout,
    redirect: '/actor/list',
    meta: { title: '演员管理', icon: 'User' },
    children: [
      {
        path: 'list',
        name: 'actor-list',
        component: () => import('@/views/actor/ActorList.vue'),
        meta: { title: '演员列表', icon: 'UserFilled' },
      },
    ],
  },
  {
    path: '/category',
    component: Layout,
    redirect: '/category/list',
    meta: { title: '分类管理', icon: 'Collection' },
    children: [
      {
        path: 'list',
        name: 'category-list',
        component: () => import('@/views/category/CategoryList.vue'),
        meta: { title: '分类列表', icon: 'CollectionTag' },
      },
    ],
  },
  {
    path: '/cinema',
    component: Layout,
    redirect: '/cinema/list',
    meta: { title: '影院管理', icon: 'OfficeBuilding' },
    children: [
      {
        path: 'list',
        name: 'cinema-list',
        component: () => import('@/views/cinema/CinemaList.vue'),
        meta: { title: '影院列表', icon: 'OfficeBuilding' },
      },
      {
        path: ':cinemaId/halls',
        name: 'hall-list',
        component: () => import('@/views/cinema/HallList.vue'),
        meta: { title: '影厅管理', icon: 'VideoCamera', hidden: true },
      },
      {
        path: 'hall/:hallId/seats',
        name: 'seat-list',
        component: () => import('@/views/cinema/SeatManage.vue'),
        meta: { title: '座位管理', icon: 'Grid', hidden: true },
      },
    ],
  },
  {
    path: '/schedule',
    component: Layout,
    redirect: '/schedule/list',
    meta: { title: '场次管理', icon: 'Calendar' },
    children: [
      {
        path: 'list',
        name: 'schedule-list',
        component: () => import('@/views/schedule/ScheduleList.vue'),
        meta: { title: '排片列表', icon: 'Calendar' },
      },
    ],
  },
  {
    path: '/order',
    component: Layout,
    redirect: '/order/list',
    meta: { title: '订单管理', icon: 'Tickets' },
    children: [
      {
        path: 'list',
        name: 'order-list',
        component: () => import('@/views/order/OrderList.vue'),
        meta: { title: '订单列表', icon: 'Document' },
      },
    ],
  },
  {
    path: '/user',
    component: Layout,
    redirect: '/user/list',
    meta: { title: '用户管理', icon: 'Avatar' },
    children: [
      {
        path: 'list',
        name: 'user-list',
        component: () => import('@/views/user/UserList.vue'),
        meta: { title: '用户列表', icon: 'User' },
      },
    ],
  },
  {
    path: '/review',
    component: Layout,
    redirect: '/review/list',
    meta: { title: '评价管理', icon: 'ChatDotRound' },
    children: [
      {
        path: 'list',
        name: 'review-list',
        component: () => import('@/views/review/ReviewList.vue'),
        meta: { title: '评价列表', icon: 'ChatLineSquare' },
      },
    ],
  },
];

const routes = [...publicRoutes, ...protectedRoutes, { path: '/:pathMatch(.*)*', redirect: '/dashboard' }];

const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes,
});

// 全局前置守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore();

  // 设置页面标题
  document.title = to.meta.title ? `${to.meta.title} - Movie Ticker 管理后台` : 'Movie Ticker 管理后台';

  // 登录页直接放行
  if (to.path === '/login') {
    if (userStore.isLoggedIn) {
      next('/');
    } else {
      next();
    }
    return;
  }

  // 未登录跳转登录页
  if (!userStore.isLoggedIn) {
    next({ path: '/login', query: { redirect: to.fullPath } });
    return;
  }

  next();
});

export default router;
