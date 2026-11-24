<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'

import { getComingSoon, getNowShowing } from '@/api/movie'
import MovieCard from '@/components/home/MovieCard.vue'
import type { MovieListItem } from '@/types'
import { errorMessage, toNumber } from '@/utils/format'

type TabKey = 'now' | 'coming'

const tabs: { key: TabKey; label: string; en: string }[] = [
  { key: 'now', label: '正在热映', en: 'NOW SHOWING' },
  { key: 'coming', label: '即将上映', en: 'COMING SOON' },
]

const activeTab = ref<TabKey>('now')
const pageNo = ref(1)
const pageSize = 12
const items = ref<MovieListItem[]>([])
const total = ref(0)
const loading = ref(false)
const error = ref('')

const totalPages = computed(() => Math.max(1, Math.ceil(total.value / pageSize)))
const pageNumbers = computed(() => {
  const pages = totalPages.value
  const start = Math.max(1, Math.min(pageNo.value - 2, pages - 4))
  const end = Math.min(pages, start + 4)
  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
})

async function load() {
  loading.value = true
  error.value = ''
  try {
    const res =
      activeTab.value === 'now'
        ? await getNowShowing({ pageNo: pageNo.value, pageSize })
        : await getComingSoon({ pageNo: pageNo.value, pageSize })
    items.value = res.records ?? []
    total.value = toNumber(res.total)
  } catch (e) {
    error.value = errorMessage(e)
    items.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function switchTab(key: TabKey) {
  if (activeTab.value === key) return
  activeTab.value = key
  pageNo.value = 1
  void load()
}

function goPage(page: number) {
  if (page < 1 || page > totalPages.value || page === pageNo.value) return
  pageNo.value = page
  void load()
}

onMounted(load)
</script>

<template>
  <main class="page wrap">
    <header class="page-head">
      <span class="kicker">MOVIES / 影片</span>
      <h1>看电影，先选一部</h1>
      <p class="lede">正在热映与即将上映，找到想看的片子，再去挑影院和场次。</p>
    </header>

    <div class="tabs" role="tablist">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        class="tab"
        :class="{ active: activeTab === tab.key }"
        type="button"
        role="tab"
        :aria-selected="activeTab === tab.key"
        @click="switchTab(tab.key)"
      >
        <span class="tab-label">{{ tab.label }}</span>
        <span class="tab-en">{{ tab.en }}</span>
      </button>
    </div>

    <div v-if="error" class="state state-error">
      <strong>加载失败</strong>
      <p>{{ error }}</p>
      <button class="btn btn-primary" type="button" @click="load">重新加载</button>
    </div>

    <div v-else-if="loading && !items.length" class="state">正在加载影片…</div>

    <div v-else-if="!items.length" class="state">
      暂无{{ activeTab === 'now' ? '正在热映' : '即将上映' }}的影片
    </div>

    <template v-else>
      <div class="movie-grid" :class="{ dim: loading }">
        <MovieCard v-for="(movie, i) in items" :key="movie.id" :movie="movie" :index="i" />
      </div>

      <nav v-if="totalPages > 1" class="pager" aria-label="分页">
        <button class="page-btn" type="button" :disabled="pageNo === 1" @click="goPage(pageNo - 1)">
          上一页
        </button>
        <button
          v-for="p in pageNumbers"
          :key="p"
          class="page-btn"
          :class="{ active: p === pageNo }"
          type="button"
          @click="goPage(p)"
        >
          {{ p }}
        </button>
        <button
          class="page-btn"
          type="button"
          :disabled="pageNo === totalPages"
          @click="goPage(pageNo + 1)"
        >
          下一页
        </button>
        <span class="page-total">共 {{ total }} 部</span>
      </nav>
    </template>
  </main>
</template>

<style scoped>
.page {
  padding: 56px 0 96px;
}

.page-head {
  margin-bottom: 28px;
}

.kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.28em;
  color: var(--red);
}

.page-head h1 {
  margin: 10px 0 8px;
  font-family: var(--font-display);
  font-size: clamp(30px, 4vw, 44px);
  letter-spacing: 0.02em;
}

.lede {
  margin: 0;
  color: var(--gray);
  font-size: 14px;
}

.tabs {
  display: flex;
  gap: 0;
  border-bottom: 2px solid var(--ink);
  margin-bottom: 28px;
}

.tab {
  appearance: none;
  background: transparent;
  border: 0;
  padding: 14px 26px;
  cursor: pointer;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
  color: var(--gray);
  border-bottom: 4px solid transparent;
  margin-bottom: -2px;
  transition: color 0.15s ease, border-color 0.15s ease;
}

.tab:hover {
  color: var(--ink);
}

.tab.active {
  color: var(--ink);
  border-bottom-color: var(--red);
}

.tab-label {
  font-size: 17px;
  font-weight: 700;
}

.tab-en {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.22em;
}

.movie-grid {
  transition: opacity 0.2s ease;
}

.movie-grid.dim {
  opacity: 0.45;
  pointer-events: none;
}

.state {
  padding: 72px 20px;
  border: 1px dashed rgba(20, 17, 16, 0.3);
  background: var(--white);
  text-align: center;
  color: var(--gray);
  font-size: 14px;
}

.state-error {
  border-color: rgba(217, 42, 31, 0.4);
}

.state-error strong {
  display: block;
  color: var(--red);
  margin-bottom: 6px;
}

.state .btn {
  margin-top: 16px;
}

.pager {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 40px;
  flex-wrap: wrap;
}

.page-btn {
  appearance: none;
  min-width: 40px;
  height: 38px;
  padding: 0 14px;
  background: var(--white);
  border: 1px solid rgba(20, 17, 16, 0.25);
  color: var(--ink);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s ease;
}

.page-btn:hover:not(:disabled) {
  border-color: var(--red);
  color: var(--red);
}

.page-btn.active {
  background: var(--ink);
  border-color: var(--ink);
  color: var(--white);
}

.page-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.page-total {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--muted);
}

@media (max-width: 640px) {
  .page {
    padding: 32px 0 64px;
  }

  .tab {
    padding: 12px 16px;
  }
}
</style>
