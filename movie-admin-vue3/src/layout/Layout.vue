<template>
  <el-container class="layout-container">
    <!-- 侧边栏 -->
    <el-aside :width="isCollapse ? '64px' : '220px'" class="sidebar">
      <div class="logo">
        <el-icon size="24"><Film /></el-icon>
        <span v-if="!isCollapse" class="logo-text">Movie Ticker</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        :collapse="isCollapse"
        :collapse-transition="false"
        router
        background-color="#1f2937"
        text-color="#d1d5db"
        active-text-color="#ffffff"
        class="sidebar-menu"
      >
        <template v-for="route in menuRoutes" :key="route.path">
          <!-- 只有一个子菜单且不隐藏的 → 直接显示单项 -->
          <el-menu-item
            v-if="route.children && route.children.filter(c => !c.meta?.hidden).length === 1"
            :index="resolvePath(route.path, route.children[0].path)"
          >
            <el-icon>
              <component :is="route.children[0].meta?.icon || route.meta?.icon || 'Menu'" />
            </el-icon>
            <template #title>{{ route.children[0].meta?.title || route.meta?.title }}</template>
          </el-menu-item>
          <!-- 多个子菜单 → 显示 sub-menu -->
          <el-sub-menu
            v-else-if="route.children && route.children.filter(c => !c.meta?.hidden).length > 1"
            :index="route.path"
          >
            <template #title>
              <el-icon><component :is="route.meta?.icon || 'Menu'" /></el-icon>
              <span>{{ route.meta?.title }}</span>
            </template>
            <el-menu-item
              v-for="child in route.children.filter(c => !c.meta?.hidden)"
              :key="child.path"
              :index="resolvePath(route.path, child.path)"
            >
              <el-icon><component :is="child.meta?.icon || 'Dot'" /></el-icon>
              <template #title>{{ child.meta?.title }}</template>
            </el-menu-item>
          </el-sub-menu>
        </template>
      </el-menu>
    </el-aside>

    <!-- 右侧主区域 -->
    <el-container>
      <!-- 顶部栏 -->
      <el-header class="header">
        <div class="header-left">
          <el-button text @click="toggleCollapse">
            <el-icon :size="20">
              <component :is="isCollapse ? 'Expand' : 'Fold'" />
            </el-icon>
          </el-button>
          <el-breadcrumb separator="/">
            <el-breadcrumb-item v-for="item in breadcrumbs" :key="item.path">
              {{ item.title }}
            </el-breadcrumb-item>
          </el-breadcrumb>
        </div>
        <div class="header-right">
          <el-dropdown trigger="click" @command="handleCommand">
            <span class="user-info">
              <el-avatar :size="32" style="background: var(--el-color-primary)">
                {{ usernameChar }}
              </el-avatar>
              <span class="username">{{ userStore.username || '管理员' }}</span>
              <el-icon><ArrowDown /></el-icon>
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
        <router-view v-slot="{ Component }">
          <transition name="fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';

import { useUserStore } from '@/stores/user';

const route = useRoute();
const router = useRouter();
const userStore = useUserStore();

const isCollapse = ref(false);

function toggleCollapse() {
  isCollapse.value = !isCollapse.value;
}

// 从路由中提取用于菜单的路由（有 meta.title 且不是 hidden 的）
const menuRoutes = computed(() => {
  const routes = router.options.routes;
  return routes.filter(
    (r) => r.component?.name === 'Layout' || r.path === '/',
  ).filter((r) => r.meta?.title || (r.children?.length && r.children[0]?.meta?.title));
});

const activeMenu = computed(() => route.path);

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

function resolvePath(parent, child) {
  if (child.startsWith('/')) return child;
  return `${parent}/${child}`.replace('//', '/');
}

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout();
    router.push('/login');
  } else if (command === 'profile') {
    // 个人中心后续做
  }
}
</script>

<style scoped>
.layout-container {
  height: 100vh;
}

/* Sidebar */
.sidebar {
  background-color: #1f2937;
  transition: width 0.3s;
  overflow: hidden;
}

.logo {
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  border-bottom: 1px solid #374151;
}

.logo-text {
  white-space: nowrap;
}

.sidebar-menu {
  border-right: none;
  height: calc(100vh - 60px);
}

.sidebar-menu:not(.el-menu--collapse) {
  width: 220px;
}

/* Header */
.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  padding: 0 20px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
  font-size: 14px;
}

.username {
  color: #374151;
}

/* Main */
.main-content {
  background-color: #f3f4f6;
  padding: 20px;
  overflow-y: auto;
}

/* 页面过渡 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.2s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
