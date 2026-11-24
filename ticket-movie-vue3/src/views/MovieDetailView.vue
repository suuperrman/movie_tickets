<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'

import { getMovieCinemas, getMovieDetail, getMovieReviews } from '@/api/movie'
import { createReview, getMyReviewOfMovie } from '@/api/review'
import { useCityStore } from '@/stores/city'
import { useUserStore } from '@/stores/user'
import type { CinemaSchedule, MovieDetail, Review } from '@/types'
import { dateOffset, errorMessage, monthDay, priceText, ratingText, timeOnly, toNumber, weekdayLabel } from '@/utils/format'

const route = useRoute()
const userStore = useUserStore()
const cityStore = useCityStore()

const movieId = String(route.params.id ?? '')
const isLoggedIn = computed(() => userStore.isLoggedIn)

const loading = ref(true)
const error = ref('')
const detail = ref<MovieDetail | null>(null)
const selectedDate = ref(dateOffset(0))
const cinemaMap = ref<Record<string, CinemaSchedule[]>>({})
const dateTabs = computed(() =>
  [0, 1, 2].map((offset) => {
    const value = dateOffset(offset)
    return {
      value,
      label: offset === 0 ? '今天' : offset === 1 ? '明天' : '后天',
      sub: `${monthDay(value)} ${weekdayLabel(value)}`,
    }
  }),
)
const allCinemas = computed(() => cinemaMap.value[selectedDate.value] ?? [])
const cinemas = computed(() => {
  const city = cityStore.city
  return city ? allCinemas.value.filter((item) => item.city === city) : allCinemas.value
})
const loadingCinemas = ref(false)

const reviews = ref<Review[]>([])
const reviewTotal = ref(0)
const reviewPage = ref(1)
const reviewPages = ref(0)
const loadingReviews = ref(false)
const myReview = ref<Review | null>(null)
const showForm = ref(false)
const submitting = ref(false)
const formError = ref('')
const form = ref({ rating: 5, content: '' })

const RATING_TEXT: Record<number, string> = { 1: '很差', 2: '较差', 3: '一般', 4: '推荐', 5: '力荐' }

const canReview = computed(() => isLoggedIn.value && !myReview.value)
const status = computed(() => toNumber(detail.value?.status))
const score = computed(() => toNumber(detail.value?.avgRating))
const reviewCount = computed(() => toNumber(detail.value?.reviewCount))

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    detail.value = await getMovieDetail(movieId)
    void loadCinemas()
    void loadReviews(true)
    if (isLoggedIn.value) void loadMyReview()
  } catch (e) {
    error.value = errorMessage(e, '加载电影详情失败')
  } finally {
    loading.value = false
  }
}

async function loadCinemas() {
  loadingCinemas.value = true
  try {
    const tabs = dateTabs.value
    const results = await Promise.all(
      tabs.map((tab) => getMovieCinemas(movieId, { date: tab.value }).catch(() => [] as CinemaSchedule[])),
    )
    const map: Record<string, CinemaSchedule[]> = {}
    tabs.forEach((tab, index) => {
      map[tab.value] = results[index] ?? []
    })
    cinemaMap.value = map
    // 今天场次已散场时自动落到第一个有排片的日期，避免页面直接空掉
    if (!(map[selectedDate.value] ?? []).length) {
      const first = tabs.find((tab) => (map[tab.value] ?? []).length)
      if (first) selectedDate.value = first.value
    }
  } catch {
    cinemaMap.value = {}
  } finally {
    loadingCinemas.value = false
  }
}

async function loadReviews(reset = true) {
  if (reset) {
    reviewPage.value = 1
    reviews.value = []
  }
  loadingReviews.value = true
  try {
    const page = await getMovieReviews(movieId, { pageNo: reviewPage.value, pageSize: 10 })
    const records = page.records ?? []
    reviews.value = reset ? records : [...reviews.value, ...records]
    reviewTotal.value = toNumber(page.total)
    reviewPages.value = toNumber(page.pages)
    reviewPage.value += 1
  } catch {
    if (reset) {
      reviews.value = []
      reviewTotal.value = 0
    }
  } finally {
    loadingReviews.value = false
  }
}

async function loadMyReview() {
  try {
    myReview.value = await getMyReviewOfMovie(movieId)
  } catch {
    myReview.value = null
  }
}

function toggleForm() {
  showForm.value = !showForm.value
  formError.value = ''
}

async function submitReview() {
  if (submitting.value) return
  formError.value = ''
  if (!form.value.rating) {
    formError.value = '请选择评分'
    return
  }
  submitting.value = true
  try {
    await createReview({
      movieId,
      rating: form.value.rating,
      content: form.value.content.trim() || undefined,
    })
    showForm.value = false
    form.value = { rating: 5, content: '' }
    detail.value = await getMovieDetail(movieId)
    void loadReviews(true)
    void loadMyReview()
  } catch (e) {
    formError.value = errorMessage(e, '评价提交失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  void loadAll()
})
</script>

<template>
  <main class="page wrap">
    <nav class="breadcrumb" aria-label="面包屑">
      <router-link to="/">首页</router-link>
      <span class="slash">/</span>
      <router-link to="/search">片库</router-link>
      <span class="slash">/</span>
      <span>{{ detail?.name ?? '电影详情' }}</span>
    </nav>

    <div v-if="loading" class="state-box">
      <span class="spinner"></span>
      <strong>正在加载影片信息</strong>
      <p>正在把胶片装进放映机。</p>
    </div>

    <div v-else-if="error" class="state-box">
      <span class="state-glyph">!</span>
      <strong>影片加载失败</strong>
      <p>{{ error }}</p>
      <button class="btn btn-primary" type="button" @click="loadAll">重试</button>
    </div>

    <template v-else-if="detail">
      <header class="detail-hero">
        <div class="hero-poster">
          <img v-if="detail.posterUrl" :src="detail.posterUrl" :alt="`${detail.name} 海报`" />
          <div v-else class="poster-fallback" aria-hidden="true">
            <span>{{ detail.name.slice(0, 1) }}</span>
            <small>NO POSTER</small>
          </div>
        </div>

        <div class="hero-info">
          <p class="hero-status">
            <span class="badge badge-red">{{ status === 1 ? '即将上映' : '正在热映' }}</span>
            <span class="hero-date">{{ monthDay(detail.releaseDate) }} 上映</span>
          </p>

          <h1 class="detail-title">{{ detail.name }}</h1>

          <div v-if="reviewCount > 0" class="detail-rating">
            <span class="rating-score">{{ ratingText(detail.avgRating) }}</span>
            <span class="stars" aria-hidden="true">
              <span v-for="n in 5" :key="n" class="star" :class="{ on: n <= Math.round(score) }">★</span>
            </span>
            <span class="rating-count">{{ reviewCount }} 人评价</span>
          </div>
          <p v-else class="rating-empty">暂无评分 · 购票观影后来写第一条</p>

          <div v-if="detail.categories?.length" class="detail-tags">
            <span v-for="category in detail.categories" :key="category.id" class="tag">
              {{ category.name }}
            </span>
          </div>

          <div class="detail-meta">
            <span v-if="detail.director">导演 · {{ detail.director }}</span>
            <span v-if="toNumber(detail.duration)">{{ toNumber(detail.duration) }} 分钟</span>
            <span v-if="detail.cinemaCount">
              {{ toNumber(detail.cinemaCount) }} 家影院在映
            </span>
          </div>

          <div class="detail-price-row">
            <span class="price-label">最低票价</span>
            <span class="price-value">
              <template v-if="detail.minPrice !== null && detail.minPrice !== undefined">
                ¥{{ priceText(detail.minPrice) }}<small>起</small>
              </template>
              <template v-else>待定</template>
            </span>
          </div>

          <p class="detail-desc">{{ detail.description || '暂无剧情简介。' }}</p>

          <a class="btn btn-primary hero-cta" href="#cinemas">选座购票 →</a>
        </div>
      </header>

      <section v-if="detail.actors?.length" class="detail-section">
        <div class="block-head">
          <h2>主要演员<span></span></h2>
          <span class="block-mono">CAST / {{ detail.actors.length }}</span>
        </div>
        <div class="actor-list">
          <div v-for="actor in detail.actors" :key="actor.id" class="actor-item">
            <div class="actor-avatar">
              <img v-if="actor.avatarUrl" :src="actor.avatarUrl" :alt="actor.name" />
              <span v-else>{{ actor.name.slice(0, 1) }}</span>
            </div>
            <div class="actor-name">{{ actor.name }}</div>
            <div v-if="actor.roleName" class="actor-role">{{ actor.roleName }}</div>
          </div>
        </div>
      </section>

      <section class="detail-section">
        <div class="block-head">
          <h2>用户评价<span></span></h2>
          <div class="block-actions">
            <span class="block-mono">REVIEWS / {{ reviewTotal }}</span>
            <button v-if="canReview" class="btn btn-sm btn-ghost" type="button" @click="toggleForm">
              {{ showForm ? '收起' : '写评价' }}
            </button>
            <router-link
              v-else-if="!isLoggedIn"
              class="btn btn-sm btn-ghost"
              :to="{ name: 'auth', query: { redirect: route.fullPath } }"
            >
              登录后评价
            </router-link>
          </div>
        </div>

        <div v-if="showForm" class="review-form">
          <div class="review-form-row">
            <span class="form-label">评分</span>
            <div class="star-picker">
              <button
                v-for="n in 5"
                :key="n"
                type="button"
                class="star-btn"
                :aria-label="`${n} 星`"
                @click="form.rating = n"
              >
                <span class="star" :class="{ on: n <= form.rating }">★</span>
              </button>
            </div>
            <span class="form-hint">{{ RATING_TEXT[form.rating] }}</span>
          </div>
          <textarea
            v-model="form.content"
            class="review-textarea"
            rows="4"
            maxlength="500"
            placeholder="说说你的观影感受（选填，最多 500 字）"
          ></textarea>
          <div class="review-form-foot">
            <span v-if="formError" class="form-error">{{ formError }}</span>
            <span class="form-count">{{ form.content.length }}/500</span>
            <button class="btn btn-primary btn-sm" type="button" :disabled="submitting" @click="submitReview">
              {{ submitting ? '提交中…' : '提交评价' }}
            </button>
          </div>
        </div>

        <div v-if="myReview" class="my-review">
          <span class="badge badge-red">我的评价</span>
          <span class="stars" aria-hidden="true">
            <span v-for="n in 5" :key="n" class="star" :class="{ on: n <= toNumber(myReview.rating) }">★</span>
          </span>
          <span class="my-review-text">{{ myReview.content || '（未填写文字评价）' }}</span>
        </div>

        <div v-if="loadingReviews" class="state-box compact">
          <span class="spinner"></span>
          <p>加载评价中…</p>
        </div>

        <div v-else-if="reviews.length" class="review-list">
          <article v-for="review in reviews" :key="review.id" class="review-item">
            <div class="review-avatar">
              <img v-if="review.avatar" :src="review.avatar" :alt="review.nickname" />
              <span v-else>{{ (review.nickname || '匿').slice(0, 1) }}</span>
            </div>
            <div class="review-main">
              <div class="review-head">
                <span class="review-nickname">{{ review.nickname || '匿名用户' }}</span>
                <span class="stars small" aria-hidden="true">
                  <span
                    v-for="n in 5"
                    :key="n"
                    class="star"
                    :class="{ on: n <= toNumber(review.rating) }"
                    >★</span
                  >
                </span>
                <span class="review-time">{{ review.createdAt }}</span>
              </div>
              <p v-if="review.content" class="review-content">{{ review.content }}</p>
            </div>
          </article>

          <div v-if="reviewPage <= reviewPages" class="load-more">
            <button class="btn btn-ghost" type="button" @click="loadReviews(false)">加载更多评价</button>
          </div>
        </div>

        <div v-else class="state-box compact">
          <span class="state-glyph">评</span>
          <p>还没有评价，购票观影后来写下第一条吧。</p>
        </div>
      </section>

      <section id="cinemas" class="detail-section">
        <div class="block-head">
          <h2>购票影院<span></span></h2>
          <span class="block-mono">CINEMAS / {{ cinemas.length }}</span>
        </div>

        <div class="date-bar" aria-label="选择日期">
          <button
            v-for="tab in dateTabs"
            :key="tab.value"
            class="date-chip"
            :class="{ active: selectedDate === tab.value }"
            type="button"
            @click="selectedDate = tab.value"
          >
            <strong>{{ tab.label }}</strong>
            <span>{{ tab.sub }}</span>
          </button>
        </div>

        <div v-if="loadingCinemas" class="state-box compact">
          <span class="spinner"></span>
          <p>加载影院场次中…</p>
        </div>

        <div v-else-if="cinemas.length" class="cinema-list">
          <article v-for="cinema in cinemas" :key="cinema.cinemaId" class="cinema-card">
            <div class="cinema-info">
              <h3>{{ cinema.cinemaName }}</h3>
              <p class="cinema-meta">
                <span v-if="cinema.city">{{ cinema.city }}</span>
                <span v-if="cinema.address"> · {{ cinema.address }}</span>
              </p>
              <p v-if="cinema.phone" class="cinema-meta mono">TEL / {{ cinema.phone }}</p>
              <p v-if="cinema.firstScheduleTime" class="cinema-meta">
                最早场次 {{ timeOnly(cinema.firstScheduleTime) }}
                <template v-if="cinema.firstScheduleEndTime">
                  — {{ timeOnly(cinema.firstScheduleEndTime) }} 散场
                </template>
              </p>
            </div>
            <div class="cinema-right">
              <div class="cinema-price">¥{{ priceText(cinema.minPrice) }}<small>起</small></div>
              <div class="cinema-count">{{ toNumber(cinema.scheduleCount) }} 场可选</div>
              <router-link
                class="btn btn-primary btn-sm"
                :to="{
                  name: 'seat-select',
                  params: { movieId },
                  query: { cinemaId: String(cinema.cinemaId), date: selectedDate },
                }"
              >
                选座购票
              </router-link>
            </div>
          </article>
        </div>

        <div v-else class="state-box compact">
          <span class="state-glyph">空</span>
          <p v-if="cityStore.city">
            当前日期暂无影院开放《{{ detail.name }}》，换个日期或城市试试。
          </p>
          <p v-else>暂无可购影院，稍后再来看看。</p>
        </div>
      </section>
    </template>
  </main>
</template>

<style scoped>
/* ---------- 主信息 ---------- */
.detail-hero {
  position: relative;
  display: grid;
  grid-template-columns: 268px minmax(0, 1fr);
  gap: 40px;
  padding: 34px;
  color: var(--white);
  background: var(--ink);
  border: 2px solid var(--ink);
  box-shadow: 12px 12px 0 var(--red);
  animation: hero-in 640ms var(--ease) both;
}

.detail-hero::after {
  content: '';
  position: absolute;
  top: 0;
  right: 0;
  width: 320px;
  height: 320px;
  background-image: radial-gradient(rgba(255, 255, 255, 0.4) 1px, transparent 1px);
  background-size: 12px 12px;
  opacity: 0.08;
  -webkit-mask-image: radial-gradient(circle at 100% 0, #000, transparent 70%);
  mask-image: radial-gradient(circle at 100% 0, #000, transparent 70%);
  pointer-events: none;
}

.hero-poster {
  position: relative;
  z-index: 1;
  align-self: start;
  padding: 7px;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 8px 8px 0 var(--red);
}

.hero-poster img {
  width: 100%;
  aspect-ratio: 2 / 3;
  object-fit: cover;
}

.poster-fallback {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  aspect-ratio: 2 / 3;
  padding: 16px;
  background:
    repeating-linear-gradient(-45deg, transparent 0 12px, rgba(20, 17, 16, 0.06) 12px 24px),
    #ece5d8;
}

.poster-fallback span {
  color: rgba(20, 17, 16, 0.2);
  font-size: 70px;
  font-weight: 900;
  line-height: 1;
}

.poster-fallback small {
  color: rgba(20, 17, 16, 0.42);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.2em;
}

.hero-info {
  position: relative;
  z-index: 1;
  min-width: 0;
}

.hero-status {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
}

.hero-date {
  color: rgba(255, 255, 255, 0.55);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.14em;
}

.detail-title {
  margin-bottom: 16px;
  font-size: clamp(38px, 5vw, 64px);
  font-weight: 900;
  line-height: 1.04;
  letter-spacing: -0.035em;
}

.detail-rating {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
}

.rating-score {
  color: var(--red);
  font-family: var(--font-display);
  font-size: 34px;
  line-height: 1;
  letter-spacing: 0.02em;
}

.stars {
  display: inline-flex;
  gap: 2px;
  font-size: 14px;
  line-height: 1;
}

.stars.small {
  font-size: 11px;
}

.star {
  color: rgba(255, 255, 255, 0.22);
}

.star.on {
  color: #ff9b91;
}

.rating-count,
.rating-empty {
  color: rgba(255, 255, 255, 0.55);
  font-size: 11.5px;
}

.rating-empty {
  margin-bottom: 16px;
}

.detail-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.tag {
  padding: 3px 10px;
  color: var(--white);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.1em;
  border: 1px solid rgba(255, 255, 255, 0.4);
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 22px;
  margin-bottom: 18px;
  color: rgba(255, 255, 255, 0.68);
  font-size: 12.5px;
}

.detail-price-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid rgba(255, 255, 255, 0.22);
}

.price-label {
  color: rgba(255, 255, 255, 0.55);
  font-size: 11px;
  letter-spacing: 0.08em;
}

.price-value {
  color: #ff9b91;
  font-family: var(--font-display);
  font-size: 30px;
  line-height: 1;
}

.price-value small {
  margin-left: 3px;
  font-family: var(--font-ui);
  font-size: 10px;
  font-weight: 500;
}

.detail-desc {
  max-width: 68ch;
  margin: 16px 0 22px;
  color: rgba(255, 255, 255, 0.76);
  font-size: 13px;
  line-height: 1.95;
}

.hero-cta {
  box-shadow: 3px 3px 0 var(--white);
}

.hero-cta:hover {
  box-shadow: 2px 2px 0 var(--white);
}

/* ---------- 分区 ---------- */
.detail-section {
  margin-top: 54px;
  padding-top: 26px;
  border-top: 2px solid var(--ink);
}

.block-head {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-end;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 20px;
}

.block-head h2 {
  font-size: 26px;
  font-weight: 900;
  letter-spacing: -0.025em;
}

.block-head h2 span {
  color: var(--red);
}

.block-mono {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.16em;
}

.block-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

/* ---------- 演员 ---------- */
.actor-list {
  display: flex;
  flex-wrap: wrap;
  gap: 24px 22px;
}

.actor-item {
  width: 88px;
  text-align: center;
}

.actor-avatar {
  display: grid;
  place-items: center;
  width: 68px;
  height: 68px;
  margin: 0 auto 9px;
  overflow: hidden;
  color: var(--white);
  font-family: var(--font-display);
  font-size: 24px;
  background: var(--ink);
  border: 2px solid var(--ink);
  border-radius: 50%;
  box-shadow: 3px 3px 0 var(--red);
}

.actor-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.actor-name {
  overflow: hidden;
  font-size: 12.5px;
  font-weight: 700;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.actor-role {
  margin-top: 2px;
  overflow: hidden;
  color: var(--gray);
  font-size: 10.5px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

/* ---------- 评价 ---------- */
.review-form {
  margin-bottom: 22px;
  padding: 20px;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 6px 6px 0 rgba(20, 17, 16, 0.1);
}

.review-form-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-bottom: 12px;
}

.form-label {
  color: var(--gray);
  font-size: 11.5px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.star-picker {
  display: inline-flex;
  gap: 2px;
}

.star-btn {
  padding: 0 2px;
  font-size: 24px;
  line-height: 1;
  background: none;
  border: 0;
  color: rgba(20, 17, 16, 0.2);
  transition: color 120ms ease;
}

.star-btn:hover,
.star-btn:hover ~ .star-btn {
  color: var(--red);
}

.star-picker .star.on {
  color: var(--red);
}

.form-hint {
  color: var(--red);
  font-size: 11.5px;
  font-weight: 700;
}

.review-textarea {
  width: 100%;
  padding: 11px 13px;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 13px;
  line-height: 1.7;
  background: #fff;
  border: 2px solid var(--ink);
  outline: none;
  resize: vertical;
}

.review-textarea:focus {
  border-color: var(--red);
  box-shadow: 3px 3px 0 var(--red);
}

.review-form-foot {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}

.form-error {
  flex: 1;
  color: var(--red);
  font-size: 11.5px;
}

.form-count {
  margin-left: auto;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
}

.my-review {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 18px;
  padding: 12px 16px;
  background: rgba(217, 42, 31, 0.07);
  border-left: 3px solid var(--red);
}

.my-review .stars .star {
  color: rgba(20, 17, 16, 0.2);
}

.my-review .stars .star.on {
  color: var(--red);
}

.my-review-text {
  color: var(--gray);
  font-size: 12.5px;
}

.review-list {
  background: var(--white);
  border: 2px solid var(--ink);
}

.review-item {
  display: flex;
  gap: 14px;
  padding: 18px 20px;
}

.review-item + .review-item {
  border-top: 1px solid rgba(20, 17, 16, 0.16);
}

.review-avatar {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  overflow: hidden;
  color: var(--white);
  font-family: var(--font-display);
  font-size: 16px;
  background: var(--ink);
  border-radius: 50%;
}

.review-avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.review-main {
  flex: 1;
  min-width: 0;
}

.review-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.review-nickname {
  font-size: 12.5px;
  font-weight: 700;
}

.review-head .stars .star {
  color: rgba(20, 17, 16, 0.2);
}

.review-head .stars .star.on {
  color: var(--red);
}

.review-time {
  margin-left: auto;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
}

.review-content {
  color: var(--gray);
  font-size: 13px;
  line-height: 1.8;
  word-break: break-word;
}

.load-more {
  display: flex;
  justify-content: center;
  padding: 16px;
  border-top: 1px solid rgba(20, 17, 16, 0.16);
}

/* ---------- 影院 ---------- */
.date-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.date-chip {
  appearance: none;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 10px 18px;
  background: var(--white);
  border: 1px solid rgba(20, 17, 16, 0.22);
  color: var(--ink);
  cursor: pointer;
  text-align: left;
  transition: all 0.15s ease;
}

.date-chip strong {
  font-size: 14px;
}

.date-chip span {
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--muted);
}

.date-chip:hover {
  border-color: var(--red);
}

.date-chip.active {
  background: var(--ink);
  border-color: var(--ink);
}

.date-chip.active strong {
  color: var(--white);
}

.date-chip.active span {
  color: rgba(255, 253, 248, 0.6);
}

.cinema-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.cinema-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 20px 22px;
  background: var(--white);
  border: 2px solid var(--ink);
  transition:
    transform 180ms var(--ease),
    box-shadow 180ms var(--ease);
}

.cinema-card:hover {
  box-shadow: 7px 7px 0 var(--ink);
  transform: translate(-3px, -3px);
}

.cinema-info {
  flex: 1;
  min-width: 0;
}

.cinema-info h3 {
  margin-bottom: 6px;
  font-size: 18px;
  font-weight: 900;
  letter-spacing: -0.01em;
}

.cinema-meta {
  color: var(--gray);
  font-size: 11.5px;
  line-height: 1.7;
}

.cinema-right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 6px;
  flex-shrink: 0;
  text-align: right;
}

.cinema-price {
  color: var(--red);
  font-family: var(--font-display);
  font-size: 24px;
  line-height: 1;
}

.cinema-price small {
  margin-left: 2px;
  color: var(--gray);
  font-family: var(--font-ui);
  font-size: 9.5px;
  font-weight: 500;
}

.cinema-count {
  color: var(--muted);
  font-size: 10.5px;
}

.state-box.compact {
  min-height: 160px;
  gap: 10px;
}

@keyframes hero-in {
  from {
    opacity: 0;
    transform: translateY(14px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (max-width: 860px) {
  .detail-hero {
    grid-template-columns: 1fr;
    gap: 26px;
    padding: 24px;
  }

  .hero-poster {
    width: 190px;
  }

  .detail-title {
    font-size: 40px;
  }

  .cinema-card {
    flex-direction: column;
    align-items: flex-start;
  }

  .cinema-right {
    align-items: flex-start;
    text-align: left;
  }
}

@media (max-width: 620px) {
  .detail-hero {
    box-shadow: 8px 8px 0 var(--red);
  }

  .block-head h2 {
    font-size: 22px;
  }
}
</style>
