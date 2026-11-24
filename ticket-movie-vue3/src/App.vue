<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import SiteFooter from '@/components/layout/SiteFooter.vue'
import SiteHeader from '@/components/layout/SiteHeader.vue'
import { useUserStore } from '@/stores/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const fullBleed = computed(() => route.meta.fullBleed === true)

function handleAuthLogout() {
  userStore.logout()
  if (route.meta.requiresAuth) {
    router.replace({ name: 'auth', query: { redirect: route.fullPath } })
  }
}

onMounted(() => {
  window.addEventListener('auth:logout', handleAuthLogout)
  userStore.restore()
  if (userStore.accessToken && !userStore.userInfo) {
    userStore.fetchProfile().catch(() => {
      // 401 会由 http 拦截器统一处理
    })
  }
})

onBeforeUnmount(() => {
  window.removeEventListener('auth:logout', handleAuthLogout)
})
</script>

<template>
  <template v-if="fullBleed">
    <router-view />
  </template>
  <template v-else>
    <SiteHeader />
    <router-view />
    <SiteFooter />
  </template>
</template>
