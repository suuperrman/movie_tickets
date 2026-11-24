<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getCinemaDetail, getCinemaMovies } from '@/api/cinema'
import { getMovieSessions } from '@/api/movie'
import type { Cinema, CinemaMovie, Schedule } from '@/types'
import {
  dateOffset,
  errorMessage,
  monthDay,
  priceText,
  timeOnly,
  toNumber,
  weekdayLabel,
} from '@/utils/format'

const route = useRoute()
const router = useRouter()

const cinemaId = String(route.params.id ?? '')
const cinema = ref<Cinema | null>(null)
const movies = ref<CinemaMovie[]>([])
const selectedId = ref('')
const date = ref(dateOffset(0))
const sessions = ref<Schedule[]>([])

const loadingMovies = ref(false)
const loadingSessions = ref(false)
const moviesError = ref('')
const sessionsError = ref('')

const railRef = ref<HTMLElement | null>(null)

const dates = computed(() =>
  [0, 1, 2].map((offset) => {
    const value = dateOffset(offset)
    return {
      value,
      label: offset === 0 ? '今天' : offset === 1 ? '明天' : '后天',
      sub: `${monthDay(value)} ${weekdayLabel(value)}`,
    }
  }),
)

const selectedMovie = computed(
  () => movies.value.find((m) => String(m.id) === selectedId.value) ?? null,
)

function scrollRail(direction: number) {
  railRef.value?.scrollBy({ left: direction * 320, behavior: 'smooth' })
}

async function loadSessions() {
  if (!selectedId.value) {
    sessions.value = []
    return
  }
  loadingSessions.value = true
  sessionsError.value = ''
  try {
    sessions.value = await getMovieSessions(selectedId.value, {
      cinemaId,
      date: date.value,
    })
  } catch (e) {
    sessionsError.value = errorMessage(e)
    sessions.value = []
  } finally {
    loadingSessions.value = false
  }
}

async function loadMovies() {
  loadingMovies.value = true
  moviesError.value = ''
  try {
    movies.value = (await getCinemaMovies(cinemaId, { date: date.value })) ?? []
    const stillExists = movies.value.some((m) => String(m.id) === selectedId.value)
    if (!stillExists) {
      selectedId.value = movies.value.length ? String(movies.value[0]?.id ?? '') : ''
    }
  } catch (e) {
    moviesError.value = errorMessage(e)
    movies.value = []
    selectedId.value = ''
  } finally {
    loadingMovies.value = false
  }
}

async function pickDate(value: string) {
  if (date.value === value) return
  date.value = value
  sessions.value = []
  await loadMovies()
  await loadSessions()
}

function selectMovie(movie: CinemaMovie) {
  const id = String(movie.id)
  if (selectedId.value === id) return
  selectedId.value = id
  void loadSessions()
}

function chooseSession(session: Schedule) {
  if (!selectedId.value) return
  router.push({
    name: 'seat-select',
    params: { movieId: selectedId.value },
    query: {
      cinemaId,
      date: date.value,
      scheduleId: String(session.scheduleId),
    },
  })
}

onMounted(async () => {
  try {
    cinema.value = await getCinemaDetail(cinemaId)
  } catch (e) {
    moviesError.value = errorMessage(e, '影院信息加载失败')
  }
  await loadMovies()
  await loadSessions()
})
</script>

<template>
  <main class="page wrap">
    <button class="back" type="button" @click="router.push('/cinemas')">← 返回影院列表</button>

    <header class="cinema-hero">
      <div class="hero-main">
        <span class="kicker">CINEMA / 影院</span>
        <h1>{{ cinema?.name || '影院详情' }}</h1>
        <p class="addr">
          <span v-if="cinema?.city" class="city">{{ cinema.city }}</span>
          {{ cinema?.address || '地址待完善' }}
        </p>
        <p class="extra">
          <span v-if="cinema?.phone">电话 {{ cinema.phone }}</span>
          <span>{{ toNumber(cinema?.hallCount) }} 个影厅</span>
        </p>
      </div>
      <div class="hero-tags">
        <span class="tag tag-open">营业中</span>
        <span class="tag">退改签</span>
        <span class="tag">影城卡</span>
        <span class="tag">3D / IMAX</span>
      </div>
    </header>

    <section class="date-bar" aria-label="选择日期">
      <button
        v-for="item in dates"
        :key="item.value"
        class="date-chip"
        :class="{ active: date === item.value }"
        type="button"
        @click="pickDate(item.value)"
      >
        <strong>{{ item.label }}</strong>
        <span>{{ item.sub }}</span>
      </button>
    </section>

    <section class="block">
      <div class="section-head">
        <h2>正在放映</h2>
        <span class="section-sub">{{ movies.length }} 部影片 · 点击切换</span>
        <div class="rail-controls">
          <button type="button" aria-label="向左" @click="scrollRail(-1)">‹</button>
          <button type="button" aria-label="向右" @click="scrollRail(1)">›</button>
        </div>
      </div>

      <div v-if="moviesError" class="state state-error">
        <strong>加载失败</strong>
        <p>{{ moviesError }}</p>
        <button class="btn btn-primary" type="button" @click="loadMovies">重新加载</button>
      </div>
      <div v-else-if="loadingMovies && !movies.length" class="state">正在加载影片…</div>
      <div v-else-if="!movies.length" class="state">该影院当日暂无可售影片</div>

      <div v-else ref="railRef" class="rail">
        <button
          v-for="movie in movies"
          :key="movie.id"
          class="movie-chip"
          :class="{ active: String(movie.id) === selectedId }"
          type="button"
          @click="selectMovie(movie)"
        >
          <div class="poster">
            <span class="glyph">{{ movie.name?.slice(0, 1) }}</span>
            <img v-if="movie.posterUrl" :src="movie.posterUrl" :alt="`${movie.name} 海报`" loading="lazy" />
          </div>
          <div class="chip-meta">
            <strong>{{ movie.name }}</strong>
            <span>{{ toNumber(movie.scheduleCount) }} 场 · 起 {{ priceText(movie.minPrice) }}</span>
          </div>
        </button>
      </div>
    </section>

    <section v-if="selectedMovie" class="block sessions">
      <div class="section-head">
        <h2>{{ selectedMovie.name }}</h2>
        <span class="section-sub">
          {{ toNumber(selectedMovie.duration) ? `${toNumber(selectedMovie.duration)} 分钟 · ` : '' }}导演
          {{ selectedMovie.director || '待定' }}
        </span>
      </div>

      <div v-if="sessionsError" class="state state-error">
        <strong>场次加载失败</strong>
        <p>{{ sessionsError }}</p>
        <button class="btn btn-primary" type="button" @click="loadSessions">重新加载</button>
      </div>
      <div v-else-if="loadingSessions" class="state">正在加载场次…</div>
      <div v-else-if="!sessions.length" class="state">该影片当日暂无可售场次</div>

      <ul v-else class="session-list">
        <li v-for="session in sessions" :key="session.scheduleId" class="session-row">
          <div class="time">
            <strong>{{ timeOnly(session.startTime) }}</strong>
            <span>{{ timeOnly(session.endTime) }} 散场</span>
          </div>
          <div class="hall">{{ session.hallName || '影厅' }}</div>
          <div class="seats">
            余 {{ toNumber(session.availableSeat) }} / {{ toNumber(session.totalSeat) }} 座
          </div>
          <div class="price">{{ priceText(session.price) }}</div>
          <button
            class="btn btn-primary buy"
            type="button"
            :disabled="toNumber(session.status) !== 1 || toNumber(session.availableSeat) <= 0"
            @click="chooseSession(session)"
          >
            选座购票
          </button>
        </li>
      </ul>
    </section>
  </main>
</template>

<style scoped>
.page {
  padding: 40px 0 96px;
}

.back {
  appearance: none;
  background: transparent;
  border: 0;
  padding: 0;
  margin-bottom: 18px;
  color: var(--gray);
  font-size: 13px;
  cursor: pointer;
}

.back:hover {
  color: var(--red);
}

.cinema-hero {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  gap: 24px;
  padding: 26px 28px;
  background: var(--ink);
  color: var(--white);
  margin-bottom: 22px;
}

.kicker {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.28em;
  color: var(--red);
}

.cinema-hero h1 {
  margin: 8px 0 10px;
  font-family: var(--font-display);
  font-size: clamp(26px, 3.4vw, 38px);
  letter-spacing: 0.02em;
}

.addr {
  margin: 0 0 6px;
  font-size: 13px;
  color: rgba(255, 253, 248, 0.78);
}

.addr .city {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 7px;
  background: var(--red);
  color: var(--white);
  font-size: 11px;
}

.extra {
  margin: 0;
  display: flex;
  gap: 18px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: rgba(255, 253, 248, 0.55);
}

.hero-tags {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: flex-end;
}

.tag {
  font-size: 11px;
  padding: 3px 8px;
  border: 1px solid rgba(255, 253, 248, 0.3);
  color: rgba(255, 253, 248, 0.8);
}

.tag-open {
  background: var(--red);
  border-color: var(--red);
  color: var(--white);
}

.date-bar {
  display: flex;
  gap: 10px;
  margin-bottom: 28px;
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

.block {
  margin-bottom: 34px;
}

.section-head {
  display: flex;
  align-items: baseline;
  gap: 14px;
  padding-bottom: 12px;
  border-bottom: 2px solid var(--ink);
  margin-bottom: 18px;
}

.section-head h2 {
  margin: 0;
  font-size: 19px;
  font-weight: 700;
}

.section-sub {
  font-size: 12px;
  color: var(--muted);
  font-family: var(--font-mono);
}

.rail-controls {
  margin-left: auto;
  display: flex;
  gap: 6px;
}

.rail-controls button {
  width: 30px;
  height: 30px;
  border: 1px solid rgba(20, 17, 16, 0.25);
  background: var(--white);
  color: var(--ink);
  font-size: 18px;
  line-height: 1;
  cursor: pointer;
}

.rail-controls button:hover {
  border-color: var(--red);
  color: var(--red);
}

.rail {
  display: flex;
  gap: 14px;
  overflow-x: auto;
  padding: 4px 2px 12px;
  scroll-snap-type: x proximity;
}

.movie-chip {
  appearance: none;
  flex: 0 0 116px;
  padding: 0;
  background: transparent;
  border: 0;
  cursor: pointer;
  text-align: left;
  scroll-snap-align: start;
}

.poster {
  position: relative;
  width: 116px;
  height: 162px;
  overflow: hidden;
  background: repeating-linear-gradient(
    135deg,
    #221d1b 0 8px,
    #191514 8px 16px
  );
  border: 2px solid transparent;
  display: grid;
  place-items: center;
}

.poster .glyph {
  font-family: var(--font-display);
  font-size: 34px;
  color: rgba(255, 253, 248, 0.35);
}

.poster img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.movie-chip.active .poster {
  border-color: var(--red);
  box-shadow: 4px 4px 0 rgba(217, 42, 31, 0.25);
}

.chip-meta {
  display: flex;
  flex-direction: column;
  gap: 3px;
  padding-top: 8px;
}

.chip-meta strong {
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chip-meta span {
  font-size: 11px;
  color: var(--muted);
  font-family: var(--font-mono);
}

.movie-chip.active .chip-meta strong {
  color: var(--red);
}

.state {
  padding: 48px 20px;
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
  margin-top: 14px;
}

.session-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 1px solid rgba(20, 17, 16, 0.12);
}

.session-row {
  display: flex;
  align-items: center;
  gap: 20px;
  padding: 16px 8px;
  border-bottom: 1px solid rgba(20, 17, 16, 0.12);
}

.session-row:hover {
  background: rgba(255, 253, 248, 0.8);
}

.time {
  display: flex;
  flex-direction: column;
  min-width: 96px;
}

.time strong {
  font-family: var(--font-display);
  font-size: 24px;
  letter-spacing: 0.02em;
}

.time span {
  font-size: 11px;
  color: var(--muted);
  font-family: var(--font-mono);
}

.hall {
  min-width: 120px;
  font-size: 13px;
  color: var(--gray);
}

.seats {
  min-width: 130px;
  font-size: 12px;
  color: var(--muted);
  font-family: var(--font-mono);
}

.price {
  min-width: 80px;
  font-size: 17px;
  font-weight: 700;
  color: var(--red);
}

.buy {
  margin-left: auto;
}

@media (max-width: 720px) {
  .page {
    padding: 24px 0 64px;
  }

  .cinema-hero {
    flex-direction: column;
    align-items: flex-start;
  }

  .session-row {
    flex-wrap: wrap;
    gap: 10px 16px;
  }

  .buy {
    width: 100%;
    margin-left: 0;
    justify-content: center;
  }
}
</style>
