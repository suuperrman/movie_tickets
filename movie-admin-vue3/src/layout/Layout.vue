<template>
  <el-container class="admin-layout">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '76px' : '238px'" class="sidebar">
      <div class="brand" @click="router.push('/dashboard')">
        <div class="brand-mark">
          <el-icon :size="20"><Film /></el-icon>
        </div>
        <div v-if="!isCollapse" class="brand-text">
          <span class="brand-name">MOVIE TICKER</span>
          <span class="brand-sub">影院运营后台</span>
        </div>
      </div>

      <div class="menu-wrap">
        <el-menu
          :default-active="activeMenu"
          :collapse="isCollapse"
          :collapse-transition="false"
          :unique-opened="true"
          router
          popper-class="admin-menu-popup"
          class="sidebar-menu"
        >
          <el-menu-item index="/dashboard">
            <el-icon><Odometer /></el-icon>
            <template #title>仪表盘</template>
          </el-menu-item>

          <!-- 快捷操作：与仪表盘同款菜单行 -->
          <el-menu-item
            v-for="item in quickActions"
            :key="item.path"
            :index="item.path"
          >
            <el-icon><component :is="item.icon" /></el-icon>
            <template #title>{{ item.label }}</template>
          </el-menu-item>

          <template v-for="route in menuRoutes" :key="route.path">
            <!-- 只有一个子菜单且不隐藏的 → 直接显示单项 -->
            <el-menu-item
              v-if="route.children && route.children.filter((c) => !c.meta?.hidden).length === 1"
              :index="resolvePath(route.path, route.children[0].path)"
            >
              <el-icon>
                <component :is="route.children[0].meta?.icon || route.meta?.icon || 'Menu'" />
              </el-icon>
              <template #title>{{ route.children[0].meta?.title || route.meta?.title }}</template>
            </el-menu-item>
            <!-- 多个子菜单 → 显示 sub-menu -->
            <el-sub-menu
              v-else-if="route.children && route.children.filter((c) => !c.meta?.hidden).length > 1"
              :index="route.path"
            >
              <template #title>
                <el-icon><component :is="route.meta?.icon || 'Menu'" /></el-icon>
                <span>{{ route.meta?.title }}</span>
              </template>
              <el-menu-item
                v-for="child in route.children.filter((c) => !c.meta?.hidden)"
                :key="child.path"
                :index="resolvePath(route.path, child.path)"
              >
                <el-icon><component :is="child.meta?.icon || 'Dot'" /></el-icon>
                <template #title>{{ child.meta?.title }}</template>
              </el-menu-item>
            </el-sub-menu>
          </template>
        </el-menu>
      </div>

      <div class="sidebar-footer">
        <span class="status-dot"></span>
        <span v-if="!isCollapse" class="footer-text">系统运行正常</span>
      </div>
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container class="main-wrap">
      <!-- 顶部栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-button text class="icon-btn" @click="toggleCollapse">
            <el-icon :size="19">
              <component :is="isCollapse ? 'Expand' : 'Fold'" />
            </el-icon>
          </el-button>
          <div class="crumb-block">
            <span class="crumb-eyebrow">管理控制台</span>
            <el-breadcrumb separator="/">
              <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
                {{ item.title }}
              </el-breadcrumb-item>
            </el-breadcrumb>
          </div>
        </div>

        <div class="header-right">
          <div class="status-chip">
            <span class="live-dot"></span>
            运营中心
          </div>
          <span class="header-divider"></span>
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-chip">
              <el-avatar
                :size="34"
                :src="userStore.userInfo?.avatar || undefined"
                class="user-avatar"
              >
                {{ usernameChar }}
              </el-avatar>
              <span class="user-meta">
                <span class="user-name">{{ userStore.username || '管理员' }}</span>
                <span class="user-role">{{ roleLabel }}</span>
              </span>
              <el-icon class="caret"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  <el-icon><User /></el-icon>个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  <el-icon><SwitchButton /></el-icon>退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <!-- 主内容区 -->
      <el-main class="main-content">
        <div class="content-shell">
          <router-view v-slot="{ Component }">
            <transition name="page-fade" mode="out-in">
              <component :is="Component" :key="route.path" />
            </transition>
          </router-view>
        </div>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const isCollapse = ref(false);

const quickActions = [
  { label: '新增电影', icon: 'Plus', path: '/movie/create' },
  { label: '影院管理', icon: 'OfficeBuilding', path: '/cinema/list' },
  { label: '排片管理', icon: 'Calendar', path: '/schedule/list' },
  { label: '订单管理', icon: 'Tickets', path: '/order/list' },
];

function toggleCollapse() {
  isCollapse.value = !isCollapse.value;
}

function handleResize() {
  if (window.innerWidth < 1100) {
    isCollapse.value = true;
  }
}

// 快捷入口已覆盖的菜单分组，避免侧栏出现重复项与双重高亮
const quickActionGroups = new Set(['/cinema', '/schedule', '/order']);

// 从路由中提取用于菜单的路由（有子路由的分组，排除登录页与通配路由）
const menuRoutes = computed(() =>
  router.options.routes.filter(
    (r) =>
      r.path !== '/' &&
      Array.isArray(r.children) &&
      r.children.length > 0 &&
      !quickActionGroups.has(r.path),
  ),
);

// 隐藏页面（新增/编辑等）高亮其所属菜单
const activeMenu = computed(() => {
  if (!route.meta?.hidden) {
    return route.path;
  }
  const group = router.options.routes.find(
    (r) => r.path !== '/' && r.children?.length && route.path.startsWith(`${r.path}/`),
  );
  const child = group?.children?.find((c) => !c.meta?.hidden);
  if (!group || !child) {
    return route.path;
  }
  return resolvePath(group.path, child.path);
});

// 面包屑
const breadcrumbs = computed(() => {
  const matched = route.matched.filter((m) => m.meta?.title);
  return matched.map((m) => ({
    path: m.path,
    title: m.meta.title,
  }));
});

const usernameChar = computed(() => {
  const name = userStore.username;
  return name ? name.charAt(0).toUpperCase() : 'A';
});

const roleLabel = computed(() =>
  userStore.userInfo?.role === 1 || userStore.userInfo?.isAdmin ? '超级管理员' : '管理员',
);

// 刷新页面后补拉一次管理员资料（登录响应可能已过期）
onMounted(() => {
  handleResize();
  window.addEventListener('resize', handleResize);
  if (userStore.token && !userStore.userInfo) {
    userStore.fetchProfile().catch(() => {});
  }
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
});

function resolvePath(parent, child) {
  if (child.startsWith('/')) return child;
  return `${parent}/${child}`.replace('//', '/');
}

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout();
    router.push('/login');
  } else if (command === 'profile') {
    router.push('/profile');
  }
}
</script>

<style scoped>
.admin-layout {
  height: 100vh;
  background: #f2f5f3;
}

/* ============ 侧边栏 ============ */
.sidebar {
  position: relative;
  display: flex;
  flex-direction: column;
  background: #12171b;
  border-right: 1px solid rgba(255, 255, 255, 0.05);
  transition: width 0.28s cubic-bezier(0.4, 0, 0.2, 1);
  overflow: hidden;
}

/* 顶部翠绿光晕 */
.sidebar::before {
  content: '';
  position: absolute;
  top: -130px;
  left: -90px;
  width: 300px;
  height: 300px;
  background: radial-gradient(circle, rgba(16, 185, 129, 0.18) 0%, transparent 66%);
  pointer-events: none;
}

/* 右缘胶片孔 */
.sidebar::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  bottom: 0;
  width: 5px;
  background-image: repeating-linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.07) 0 5px,
    transparent 5px 17px
  );
  pointer-events: none;
}

.brand {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: center;
  gap: 12px;
  height: 74px;
  padding: 0 18px;
  cursor: pointer;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.brand-mark {
  width: 38px;
  height: 38px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
  background: linear-gradient(135deg, #10b981, #047857);
  box-shadow: 0 8px 20px rgba(16, 185, 129, 0.3);
  flex-shrink: 0;
}

.brand-text {
  overflow: hidden;
  white-space: nowrap;
}

.brand-name {
  display: block;
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 2.4px;
  color: #f8fafc;
}

.brand-sub {
  display: block;
  margin-top: 3px;
  font-size: 11px;
  letter-spacing: 1.4px;
  color: #78878c;
}

.menu-wrap {
  flex: 1;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  padding: 14px 10px 10px;
}

.menu-wrap::-webkit-scrollbar {
  width: 4px;
}

.menu-wrap::-webkit-scrollbar-thumb {
  background: rgba(255, 255, 255, 0.12);
  border-radius: 2px;
}

.sidebar-menu {
  --el-menu-bg-color: transparent;
  --el-menu-hover-bg-color: rgba(255, 255, 255, 0.055);
  --el-menu-text-color: #94a3a4;
  --el-menu-active-color: #ecfdf5;
  --el-menu-border-color: transparent;
  border-right: none;
  background: transparent;
}

/* 菜单项 */
:deep(.el-menu-item),
:deep(.el-sub-menu__title) {
  position: relative;
  height: 44px;
  line-height: 44px;
  margin: 3px 0;
  border-radius: 10px;
  background-color: transparent;
  color: #94a3a4;
  font-size: 13.5px;
  letter-spacing: 0.3px;
  transition: background 0.18s ease, color 0.18s ease;
}

:deep(.el-menu-item:hover),
:deep(.el-sub-menu__title:hover) {
  background: rgba(255, 255, 255, 0.055);
  color: #e9f5f0;
}

:deep(.el-menu-item .el-icon),
:deep(.el-sub-menu__title .el-icon) {
  color: inherit;
  margin-right: 10px;
}

/* 选中态 */
:deep(.el-menu-item.is-active) {
  background: linear-gradient(90deg, rgba(16, 185, 129, 0.2), rgba(16, 185, 129, 0.05));
  color: #ecfdf5;
  font-weight: 600;
}

:deep(.el-menu-item.is-active::before) {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: #34d399;
  box-shadow: 0 0 10px rgba(52, 211, 153, 0.8);
}

:deep(.el-menu-item.is-active .el-icon) {
  color: #6ee7b7;
}

/* 子菜单展开区域 */
:deep(.el-sub-menu.is-active > .el-sub-menu__title) {
  color: #e9f5f0;
}

:deep(.el-sub-menu .el-menu) {
  background: transparent;
}

:deep(.el-sub-menu .el-menu-item) {
  min-width: auto;
  font-size: 13px;
}

/* 折叠态 */
:deep(.el-menu--collapse) {
  width: 100%;
}

:deep(.el-menu--collapse .el-menu-item),
:deep(.el-menu--collapse .el-sub-menu__title) {
  width: 100%;
  border-radius: 10px;
  padding: 0 !important;
  display: flex;
  justify-content: center;
}

:deep(.el-menu--collapse .el-icon) {
  margin-right: 0;
}

/* 底部状态 */
.sidebar-footer {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 14px 20px 18px;
  border-top: 1px solid rgba(255, 255, 255, 0.06);
  flex-shrink: 0;
}

.status-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  border-radius: 50%;
  background: #34d399;
  box-shadow: 0 0 0 4px rgba(52, 211, 153, 0.12);
}

.footer-text {
  font-size: 11px;
  letter-spacing: 0.8px;
  color: #64748b;
  white-space: nowrap;
}

/* ============ 顶栏 ============ */
.main-wrap {
  min-width: 0;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68px;
  padding: 0 22px;
  background: rgba(255, 255, 255, 0.88);
  backdrop-filter: blur(14px);
  border-bottom: 1px solid #e5eae7;
  flex-shrink: 0;
  z-index: 10;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
}

.icon-btn {
  width: 36px;
  height: 36px;
  border-radius: 10px;
  color: #5b6b66;
}

.icon-btn:hover {
  background: #edf3f0;
  color: #047857;
}

.crumb-block {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.crumb-eyebrow {
  font-size: 10px;
  letter-spacing: 2px;
  text-transform: uppercase;
  color: #9aa8a2;
}

:deep(.el-breadcrumb) {
  font-size: 13px;
}

:deep(.el-breadcrumb__inner) {
  color: #7c8b85;
  font-weight: 400;
}

:deep(.el-breadcrumb__item:last-child .el-breadcrumb__inner) {
  color: #16211d;
  font-weight: 600;
}

:deep(.el-breadcrumb__separator) {
  color: #c3cdc8;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 14px;
  flex-shrink: 0;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  height: 30px;
  padding: 0 12px;
  border-radius: 999px;
  background: #ecfdf5;
  color: #047857;
  font-size: 12px;
  font-weight: 600;
  letter-spacing: 0.4px;
}

.live-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #10b981;
  animation: live-pulse 2.2s ease-out infinite;
}

@keyframes live-pulse {
  0% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.4);
  }
  70% {
    box-shadow: 0 0 0 6px rgba(16, 185, 129, 0);
  }
  100% {
    box-shadow: 0 0 0 0 rgba(16, 185, 129, 0);
  }
}

.header-divider {
  width: 1px;
  height: 24px;
  background: #e5eae7;
}

.user-chip {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 4px 10px 4px 4px;
  border-radius: 999px;
  cursor: pointer;
  transition: background 0.18s ease;
  outline: none;
}

.user-chip:hover {
  background: #f0f5f2;
}

.user-avatar {
  background: linear-gradient(135deg, #10b981, #047857);
  border: 2px solid #fff;
  box-shadow: 0 0 0 1px #d8e5df;
  font-weight: 600;
}

.user-meta {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.user-name {
  font-size: 13px;
  font-weight: 600;
  color: #1f2b26;
}

.user-role {
  font-size: 11px;
  color: #94a3a0;
}

.caret {
  font-size: 12px;
  color: #9aa8a2;
}

/* ============ 内容区 ============ */
.main-content {
  padding: 24px;
  overflow-y: auto;
  background:
    radial-gradient(circle at 18% -8%, rgba(16, 185, 129, 0.07) 0%, transparent 42%),
    #f2f5f3;
}

.content-shell {
  width: 100%;
  max-width: 1500px;
  margin: 0 auto;
}

/* 页面过渡 */
.page-fade-enter-active {
  transition: opacity 0.24s ease, transform 0.24s ease;
}

.page-fade-leave-active {
  transition: opacity 0.14s ease;
}

.page-fade-enter-from {
  opacity: 0;
  transform: translateY(8px);
}

.page-fade-leave-to {
  opacity: 0;
}

/* ============ 响应式 ============ */
@media (max-width: 900px) {
  .header {
    padding: 0 14px;
  }

  .status-chip,
  .header-divider {
    display: none;
  }

  .user-meta {
    display: none;
  }

  .main-content {
    padding: 16px;
  }
}
</style>
