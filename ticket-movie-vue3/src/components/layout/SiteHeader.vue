<script setup lang="ts">
import { useRouter } from 'vue-router'

import CityPicker from '@/components/layout/CityPicker.vue'
import { useUserStore } from '@/stores/user'

const router = useRouter()
const userStore = useUserStore()

async function handleLogout() {
  await userStore.logoutRemote()
  router.push({ name: 'home' })
}
</script>

<template>
  <div class="topstrip" aria-hidden="true">
    <div class="marquee">
      <div v-for="n in 2" :key="n" class="marquee-group">
        <span>NOW SHOWING</span>
        <span>在线选座</span>
        <span>影院取票</span>
        <span>电子票根</span>
        <span>MOVIE TICKER</span>
        <span>灯暗下来 · 故事开始</span>
      </div>
    </div>
  </div>

  <header class="topbar">
    <div class="topbar-left">
      <router-link class="brand" to="/">
        <span class="brand-mark">
          <svg
            width="20"
            height="20"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            stroke-width="1.5"
          >
            <rect x="3" y="4" width="18" height="16" rx="1.5" />
            <path d="M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4" />
          </svg>
        </span>
        <span class="brand-text">
          <strong>MOVIE TICKER</strong>
          <small>电影票务</small>
        </span>
      </router-link>

      <CityPicker />
    </div>

    <nav class="nav">
      <router-link class="nav-link" to="/" active-class="is-active">首页</router-link>
      <router-link class="nav-link" to="/movies" active-class="is-active">电影</router-link>
      <router-link class="nav-link" to="/cinemas" active-class="is-active">影院</router-link>
      <router-link class="nav-link" to="/search" active-class="is-active">搜索</router-link>
      <router-link v-if="userStore.isLoggedIn" class="nav-link" to="/orders" active-class="is-active">
        我的订单
      </router-link>

      <template v-if="userStore.isLoggedIn">
        <router-link class="user-chip" to="/profile" active-class="is-active">
          <span class="user-dot">{{ userStore.nickname.slice(0, 1) }}</span>
          {{ userStore.nickname }}
        </router-link>
        <button class="btn btn-sm btn-ghost" type="button" @click="handleLogout">退出</button>
      </template>
      <router-link v-else class="btn-cta" to="/auth">登录 / 注册</router-link>
    </nav>
  </header>
</template>

<style scoped>
.topbar-left {
  display: flex;
  align-items: center;
  gap: 20px;
  flex-shrink: 0;
}

.nav {
  max-width: calc(100% - 130px);
  overflow-x: auto;
  scrollbar-width: none;
}

.nav::-webkit-scrollbar {
  display: none;
}

.nav-link,
.user-chip {
  flex-shrink: 0;
  white-space: nowrap;
}

@media (max-width: 880px) {
  .topbar-left {
    gap: 10px;
  }
}

@media (max-width: 620px) {
  .nav {
    gap: 10px;
  }

  .nav .btn-ghost {
    display: none;
  }
}
</style>
