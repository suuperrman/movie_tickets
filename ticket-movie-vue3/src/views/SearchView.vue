<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { searchMovies } from '@/api/movie'
import MovieCard from '@/components/home/MovieCard.vue'
import type { MovieListItem } from '@/types'
import { errorMessage, toNumber } from '@/utils/format'

const route = useRoute()
const router = useRouter()

const input = ref(String(route.query.q ?? ''))
const loading = ref(false)
const error = ref('')
const results = ref<MovieListItem[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 12
const totalPages = ref(0)

const activeKeyword = computed(() => String(route.query.q ?? '').trim())

async function loadResults() {
  const keyword = activeKeyword.value
  if (!keyword) {
    results.value = []
    total.value = 0
    totalPages.value = 0
    error.value = ''
    return
  }

  loading.value = true
  error.value = ''
  try {
    const page = await searchMovies({ keyword, pageNo: pageNo.value, pageSize })
    results.value = page.records ?? []
    total.value = toNumber(page.total)
    totalPages.value = toNumber(page.pages)
  } catch (e) {
    error.value = errorMessage(e, '搜索失败，请稍后重试')
    results.value = []
    total.value = 0
    totalPages.value = 0
  } finally {
    loading.value = false
  }
}

function submitSearch() {
  const keyword = input.value.trim()
  if (keyword === activeKeyword.value) {
    void loadResults()
    return
  }
  router.push({ name: 'search', query: keyword ? { q: keyword } : {} })
}

function goPage(page: number) {
  if (page < 1 || page > totalPages.value || page === pageNo.value) return
  pageNo.value = page
  void loadResults()
}

watch(activeKeyword, () => {
  input.value = activeKeyword.value
  pageNo.value = 1
  void loadResults()
})

onMounted(() => {
  void loadResults()
})
</script>

<template>
  <main class="page wrap">
    <div class="search-head">
      <p class="kicker">03 / 片库检索 / SEARCH</p>
      <h1 class="page-title">找一部想看的电影<span class="dot">。</span></h1>
      <p class="page-sub">输入片名、导演或演员，从正在热映与即将上映的片单里查找。</p>

      <form class="search-bar" role="search" @submit.prevent="submitSearch">
        <span class="search-icon" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="7" />
            <path d="m20 20-3.5-3.5" />
          </svg>
        </span>
        <input
          v-model="input"
          class="search-input"
          type="text"
          placeholder="例如：星际回响、科幻、陈默..."
          aria-label="搜索电影"
          autofocus
        />
        <button class="search-submit" type="submit">搜索</button>
      </form>
    </div>

    <div v-if="!activeKeyword" class="state-box idle">
      <span class="state-glyph">检</span>
      <strong>输入关键词开始搜索</strong>
      <p>也可以回到首页，看看今天正在热映的影片。</p>
      <router-link class="btn btn-ghost" to="/">浏览片单</router-link>
    </div>

    <template v-else>
      <div class="result-head">
        <h2 class="result-title">
          搜索结果 <span class="keyword">「{{ activeKeyword }}」</span>
        </h2>
        <span v-if="!loading && !error" class="result-count">{{ total }} 部影片</span>
      </div>

      <div v-if="loading" class="state-box">
        <span class="spinner"></span>
        <strong>正在检索片库</strong>
        <p>正在翻找与「{{ activeKeyword }}」相关的影片。</p>
      </div>

      <div v-else-if="error" class="state-box">
        <span class="state-glyph">!</span>
        <strong>搜索失败</strong>
        <p>{{ error }}</p>
        <button class="btn btn-primary" type="button" @click="loadResults">重试</button>
      </div>

      <template v-else-if="results.length">
        <div class="movie-grid">
          <MovieCard
            v-for="(movie, index) in results"
            :key="movie.id"
            :movie="movie"
            :index="(pageNo - 1) * pageSize + index"
          />
        </div>

        <nav v-if="totalPages > 1" class="pagination" aria-label="分页">
          <button class="page-num" type="button" :disabled="pageNo <= 1" @click="goPage(pageNo - 1)">
            上一页
          </button>
          <button
            v-for="page in totalPages"
            :key="page"
            class="page-num"
            :class="{ 'is-active': page === pageNo }"
            type="button"
            @click="goPage(page)"
          >
            {{ page }}
          </button>
          <button
            class="page-num"
            type="button"
            :disabled="pageNo >= totalPages"
            @click="goPage(pageNo + 1)"
          >
            下一页
          </button>
        </nav>
      </template>

      <div v-else class="state-box">
        <span class="state-glyph">空</span>
        <strong>没有找到相关影片</strong>
        <p>没有与「{{ activeKeyword }}」匹配的结果，换个片名或关键词再试试。</p>
        <router-link class="btn btn-ghost" to="/">浏览片单</router-link>
      </div>
    </template>
  </main>
</template>

<style scoped>
.search-head {
  max-width: 760px;
  margin-bottom: 42px;
}

.dot {
  color: var(--red);
}

.search-bar {
  display: flex;
  margin-top: 26px;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 6px 6px 0 rgba(20, 17, 16, 0.12);
  transition: box-shadow 180ms ease;
}

.search-bar:focus-within {
  box-shadow: 6px 6px 0 var(--red);
}

.search-icon {
  display: grid;
  place-items: center;
  width: 50px;
  flex-shrink: 0;
  color: var(--gray);
  border-right: 2px solid var(--ink);
}

.search-input {
  min-width: 0;
  flex: 1;
  height: 56px;
  padding: 0 16px;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 14px;
  background: transparent;
  border: 0;
  outline: none;
}

.search-input::placeholder {
  color: var(--muted);
}

.search-submit {
  flex-shrink: 0;
  width: 116px;
  color: var(--white);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.04em;
  background: var(--red);
  border: 0;
  border-left: 2px solid var(--ink);
  transition: background-color 160ms ease;
}

.search-submit:hover {
  background: var(--red-deep);
}

.result-head {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 26px;
  padding-bottom: 16px;
  border-bottom: 2px solid var(--ink);
}

.result-title {
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.keyword {
  color: var(--red);
}

.result-count {
  color: var(--gray);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.12em;
}

.idle {
  color: var(--gray);
}

@media (max-width: 620px) {
  .search-bar {
    flex-wrap: wrap;
  }

  .search-icon {
    width: 44px;
  }

  .search-input {
    height: 50px;
  }

  .search-submit {
    width: 100%;
    height: 48px;
    border-top: 2px solid var(--ink);
    border-left: 0;
  }
}
</style>
