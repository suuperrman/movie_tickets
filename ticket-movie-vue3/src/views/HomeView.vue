<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { getComingSoon, getNowShowing } from '@/api/movie'
import MovieCard from '@/components/home/MovieCard.vue'
import type { MovieListItem } from '@/types'
import { errorMessage, monthDay, priceText, ratingText, toNumber } from '@/utils/format'

type TabKey = 'now' | 'coming'

const router = useRouter()
const keyword = ref('')
const activeTab = ref<TabKey>('now')
const pageNo = ref(1)
const pageSize = 8

const lists = reactive<Record<TabKey, { items: MovieListItem[]; total: number }>>({
  now: { items: [], total: 0 },
  coming: { items: [], total: 0 },
})

const pending = ref(0)
const loading = computed(() => pending.value > 0)
const error = ref('')
const feature = ref<MovieListItem | null>(null)

const now = new Date()
const todayLabel = `${now.getFullYear()}.${String(now.getMonth() + 1).padStart(2, '0')}.${String(now.getDate()).padStart(2, '0')}`

const current = computed(() => lists[activeTab.value])
const totalPages = computed(() => Math.max(1, Math.ceil(current.value.total / pageSize)))

const pageNumbers = computed(() => {
  const total = totalPages.value
  const start = Math.max(1, Math.min(pageNo.value - 2, total - 4))
  const end = Math.min(total, start + 4)
  return Array.from({ length: end - start + 1 }, (_, i) => start + i)
})

const featureRating = computed(() => ratingText(feature.value?.avgRating))
const featurePrice = computed(() =>
  feature.value?.minPrice !== null && feature.value?.minPrice !== undefined
    ? priceText(feature.value.minPrice)
    : '—',
)

async function load(tab: TabKey, page = 1) {
  pending.value += 1
  error.value = ''
  try {
    const res =
      tab === 'now'
        ? await getNowShowing({ pageNo: page, pageSize })
        : await getComingSoon({ pageNo: page, pageSize })
    lists[tab].items = res.records ?? []
    lists[tab].total = toNumber(res.total)
    if (tab === 'now' && page === 1) {
      feature.value = res.records?.[0] ?? null
    }
  } catch (e) {
    error.value = errorMessage(e)
    lists[tab].items = []
  } finally {
    pending.value -= 1
  }
}

function switchTab(tab: TabKey) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  pageNo.value = 1
  if (!lists[tab].items.length) load(tab, 1)
}

function goPage(page: number) {
  if (page < 1 || page > totalPages.value || page === pageNo.value) return
  pageNo.value = page
  void load(activeTab.value, page)
}

function goSearch() {
  const q = keyword.value.trim()
  router.push(q ? { path: '/search', query: { q } } : '/search')
}

function hideBrokenImage(event: Event) {
  const img = event.target as HTMLImageElement | null
  if (img) img.style.display = 'none'
}

onMounted(() => {
  void load('now', 1)
  void load('coming', 1)
})
</script>

<template>
  <main>
    <section class="hero wrap">
      <div class="hero-copy">
        <div class="hero-meta fade-up d1">
          <span class="num">01</span>
          <span>今日影讯</span>
          <span class="slash">/</span>
          <span>NOW SHOWING</span>
          <span>{{ todayLabel }}</span>
        </div>

        <h1 class="fade-up d2">
          <span class="line">今晚，</span>
          <span class="line"><span class="outline">看场</span><span class="block-red">电影</span></span>
        </h1>

        <p class="hero-lede fade-up d3">
          正在热映与即将上映，选好座位就出发。灯暗下来，故事开始。
        </p>

        <form class="hero-search fade-up d4" @submit.prevent="goSearch">
          <input v-model="keyword" type="text" placeholder="片名、导演或演员" aria-label="搜索电影" />
          <button type="submit">
            搜索
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2">
              <path d="M5 12h14M13 6l6 6-6 6" />
            </svg>
          </button>
        </form>

        <div class="hero-stats fade-up d5">
          <div class="stat"><strong>{{ lists.now.total }}</strong><span>部热映</span></div>
          <div class="stat"><strong>{{ lists.coming.total }}</strong><span>部待映</span></div>
          <div class="stat">
            <strong>{{ featureRating }}</strong><span>今日均分</span>
          </div>
        </div>
      </div>

      <div class="hero-art art-in">
        <router-link v-if="feature" class="poster-feature" :to="`/movie/${feature.id}`">
          <span class="poster-vertical">NOW SHOWING / 01</span>
          <div class="poster-frame">
            <img
              v-if="feature.posterUrl"
              :src="feature.posterUrl"
              :alt="`${feature.name} 海报`"
              @error="hideBrokenImage"
            />
            <div v-else class="hero-poster-fallback" aria-hidden="true">
              <span>{{ feature.name.slice(0, 1) }}</span>
              <small>NO POSTER</small>
            </div>
          </div>
          <div class="poster-cap">
            <strong>{{ feature.name }}</strong>
            <span class="price">¥{{ featurePrice }} 起</span>
          </div>
          <span class="sticker"><strong>{{ featureRating }}</strong><small>评分</small></span>
        </router-link>

        <router-link v-else class="poster-feature poster-empty" to="/search">
          <div class="poster-frame">
            <div class="hero-poster-fallback" aria-hidden="true">
              <span>影</span>
              <small>NO POSTER</small>
            </div>
          </div>
          <div class="poster-cap">
            <strong>正在排片</strong>
            <span class="price">去搜索</span>
          </div>
        </router-link>

        <span v-if="feature" class="art-note">
          {{ monthDay(feature.releaseDate) }} 上映 / {{ feature.director || '导演待定' }}
        </span>
      </div>
    </section>

    <div class="service-strip">
      <div class="service-item">
        <span class="no">01</span>
        <span><strong>在线选座</strong><small>ONLINE SEAT</small></span>
      </div>
      <div class="service-item">
        <span class="no">02</span>
        <span><strong>影院取票</strong><small>PICK UP</small></span>
      </div>
      <div class="service-item">
        <span class="no">03</span>
        <span><strong>电子票根</strong><small>E-TICKET</small></span>
      </div>
    </div>

    <section class="list-section wrap">
      <div class="section-head">
        <div>
          <p class="section-kicker">02 / 本期片单 / MOVIE LIST</p>
          <h2 class="section-title">
            {{ activeTab === 'now' ? '正在热映' : '即将上映' }}<span>。</span>
          </h2>
        </div>
        <div class="tabs" role="tablist" aria-label="影片分类">
          <button
            class="tab"
            :class="{ 'is-active': activeTab === 'now' }"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'now'"
            @click="switchTab('now')"
          >
            正在热映 <span class="num">{{ lists.now.total }}</span>
          </button>
          <button
            class="tab"
            :class="{ 'is-active': activeTab === 'coming' }"
            type="button"
            role="tab"
            :aria-selected="activeTab === 'coming'"
            @click="switchTab('coming')"
          >
            即将上映 <span class="num">{{ lists.coming.total }}</span>
          </button>
        </div>
      </div>

      <div v-if="error" class="state-box">
        <span class="state-glyph">!</span>
        <strong>片单加载失败</strong>
        <p>{{ error }}</p>
        <button class="btn btn-primary" type="button" @click="load(activeTab, pageNo)">重新加载</button>
      </div>

      <div v-else-if="loading && !current.items.length" class="state-box">
        <span class="spinner"></span>
        <strong>正在取片单</strong>
        <p>稍等，正在从放映机里搬电影。</p>
      </div>

      <div v-else-if="!current.items.length" class="state-box">
        <span class="state-glyph">空</span>
        <strong>暂无影片</strong>
        <p>{{ activeTab === 'now' ? '当前没有正在热映的影片。' : '即将上映的影片还在路上。' }}</p>
        <router-link class="btn btn-primary" to="/search">去搜索</router-link>
      </div>

      <template v-else>
        <div class="movie-grid">
          <MovieCard
            v-for="(movie, index) in current.items"
            :key="movie.id"
            :movie="movie"
            :index="(pageNo - 1) * pageSize + index"
          />
        </div>

        <nav v-if="totalPages > 1" class="pagination" aria-label="分页">
          <button class="page-num" type="button" :disabled="pageNo === 1" @click="goPage(pageNo - 1)">
            上一页
          </button>
          <button
            v-for="page in pageNumbers"
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
            :disabled="pageNo === totalPages"
            @click="goPage(pageNo + 1)"
          >
            下一页
          </button>
        </nav>
      </template>
    </section>
  </main>
</template>

<style scoped>
.hero-poster-fallback {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  width: 100%;
  aspect-ratio: 2 / 3;
  padding: 18px;
  background:
    repeating-linear-gradient(-45deg, transparent 0 12px, rgba(20, 17, 16, 0.05) 12px 24px),
    #ece5d8;
  border: 1px solid rgba(20, 17, 16, 0.25);
}

.hero-poster-fallback span {
  color: rgba(20, 17, 16, 0.2);
  font-size: 76px;
  font-weight: 900;
  line-height: 1;
}

.hero-poster-fallback small {
  color: rgba(20, 17, 16, 0.42);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.2em;
}

.poster-empty {
  transform: rotate(-2deg);
}

.state-box {
  margin-top: 4px;
}
</style>
