<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { getMovieSessions, getSeatMap } from '@/api/movie'
import { createOrder } from '@/api/order'
import { useUserStore } from '@/stores/user'
import type { Schedule, SeatMap } from '@/types'
import { dateOffset, errorMessage, priceText, timeOnly, toNumber } from '@/utils/format'

interface SeatCell {
  key: string
  rowNo: number
  colNo: number
  status: number
}

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 所有 ID 保持字符串，避免雪花 ID 超出 JS 安全整数范围
const movieId = String(route.params.movieId ?? '')
const cinemaId = String(route.query.cinemaId ?? '')
const date = String(route.query.date || dateOffset(0))
const wantedScheduleId = String(route.query.scheduleId ?? '')

const sessions = ref<Schedule[]>([])
const loadingSessions = ref(true)
const selectedSessionId = ref<string | null>(null)

const seatMap = ref<SeatMap | null>(null)
const loadingSeats = ref(false)
const selectedSeatIds = ref<string[]>([])
const MAX_SEATS = 5

const creating = ref(false)
const error = ref('')
const notice = ref('')

const selectedCount = computed(() => selectedSeatIds.value.length)
const selectedSet = computed(() => new Set(selectedSeatIds.value))
const totalPrice = computed(() => toNumber(seatMap.value?.price) * selectedCount.value)

/** Bitmap 解码：2bit/座，大端序，Base64。00 可售 01 锁定 10 已售 11 无此座位 */
const seatStatusMap = computed(() => {
  const map = new Map<string, SeatCell>()
  const m = seatMap.value
  if (!m?.seatStatusBitmap) return map

  const rowCount = toNumber(m.rowCount)
  const colCount = toNumber(m.colCount)

  let bytes: number[] = []
  try {
    bytes = atob(m.seatStatusBitmap)
      .split('')
      .map((char) => char.charCodeAt(0))
  } catch {
    return map
  }

  for (let r = 1; r <= rowCount; r += 1) {
    for (let c = 1; c <= colCount; c += 1) {
      const idx = (r - 1) * colCount + (c - 1)
      const byteIdx = Math.floor(idx / 4)
      const bitPos = (3 - (idx % 4)) * 2
      const status = (Number(bytes[byteIdx] ?? 0) >> bitPos) & 0x03
      map.set(`${r}-${c}`, {
        key: `${r}-${c}`,
        rowNo: r,
        colNo: c,
        status,
      })
    }
  }
  return map
})

const SKELETON_ROWS = 15
const SKELETON_COLS = 20

const seatGrid = computed<SeatCell[][]>(() => {
  if (!seatMap.value && !loadingSeats.value) return []
  const rowCount = seatMap.value ? toNumber(seatMap.value.rowCount) : SKELETON_ROWS
  const colCount = seatMap.value ? toNumber(seatMap.value.colCount) : SKELETON_COLS
  const hasRealData = seatStatusMap.value.size > 0

  return Array.from({ length: rowCount }, (_, rowIndex) =>
    Array.from({ length: colCount }, (_, colIndex) => {
      const key = `${rowIndex + 1}-${colIndex + 1}`
      if (hasRealData) {
        return (
          seatStatusMap.value.get(key) ?? {
            key,
            rowNo: rowIndex + 1,
            colNo: colIndex + 1,
            status: -1,
          }
        )
      }
      return { key, rowNo: rowIndex + 1, colNo: colIndex + 1, status: -1 }
    }),
  )
})

function showNotice(message: string) {
  notice.value = message
  window.setTimeout(() => {
    if (notice.value === message) notice.value = ''
  }, 2600)
}

async function loadSessions() {
  if (!cinemaId) {
    loadingSessions.value = false
    error.value = '缺少影院信息，请返回电影详情重新选择影院'
    return
  }

  loadingSessions.value = true
  error.value = ''
  try {
    sessions.value = await getMovieSessions(movieId, { cinemaId, date })
    if (sessions.value.length) {
      const wanted = wantedScheduleId
        ? sessions.value.find((s) => String(s.scheduleId) === wantedScheduleId)
        : undefined
      selectedSessionId.value = String(wanted?.scheduleId ?? sessions.value[0]?.scheduleId ?? '')
    }
  } catch (e) {
    error.value = errorMessage(e, '加载场次失败')
  } finally {
    loadingSessions.value = false
  }
}

const seatMapCache = new Map<string, { data: SeatMap; fetchedAt: number }>()
const SEAT_CACHE_TTL_MS = 60 * 1000
let requestSeq = 0

watch(selectedSessionId, async (sid) => {
  const seq = (requestSeq += 1)
  if (!sid) {
    seatMap.value = null
    loadingSeats.value = false
    return
  }
  selectedSeatIds.value = []
  error.value = ''

  const cached = seatMapCache.get(sid)
  if (cached && Date.now() - cached.fetchedAt < SEAT_CACHE_TTL_MS) {
    seatMap.value = cached.data
    loadingSeats.value = false
    return
  }

  seatMap.value = cached ? cached.data : null
  loadingSeats.value = true
  try {
    const data = await getSeatMap(sid)
    seatMapCache.set(sid, { data, fetchedAt: Date.now() })
    if (seq === requestSeq) seatMap.value = data
  } catch (e) {
    if (seq === requestSeq && !seatMap.value) {
      error.value = errorMessage(e, '加载座位图失败')
    }
  } finally {
    if (seq === requestSeq) loadingSeats.value = false
  }
})

function onSeatGridClick(event: MouseEvent) {
  const target = event.target as HTMLElement | null
  const button = target?.closest('button.seat') as HTMLButtonElement | null
  const key = button?.dataset.key
  if (key) toggleSeat(key)
}

function toggleSeat(key: string) {
  const seat = seatStatusMap.value.get(key)
  if (!seat || seat.status !== 0) return

  if (selectedSet.value.has(key)) {
    selectedSeatIds.value = selectedSeatIds.value.filter((item) => item !== key)
    return
  }
  if (selectedSeatIds.value.length >= MAX_SEATS) {
    showNotice(`每次最多选择 ${MAX_SEATS} 个座位`)
    return
  }
  selectedSeatIds.value.push(key)
}

function seatClass(seat: SeatCell): string {
  if (!seatMap.value) return 'seat skeleton'
  if (seat.status === -1 || seat.status === 3) return 'seat hole'
  if (selectedSet.value.has(seat.key)) return 'seat selected'
  return seat.status === 0 ? 'seat available' : 'seat sold'
}

/** 行号转字母：1 → A，26 → Z，27 → AA */
function rowLetter(row: number): string {
  let n = row
  let label = ''
  while (n > 0) {
    const rem = (n - 1) % 26
    label = String.fromCharCode(65 + rem) + label
    n = Math.floor((n - 1) / 26)
  }
  return label
}

function seatLabel(seat: SeatCell): string {
  if (seat.status === -1 || seat.status === 3) return '无此座位'
  const statusText = seat.status === 0 ? '可售' : '已售'
  return `${rowLetter(seat.rowNo)}排${seat.colNo}座，${statusText}`
}

function seatCode(key: string): string {
  const seat = seatStatusMap.value.get(key)
  return seat ? `${rowLetter(seat.rowNo)}${seat.colNo}` : ''
}

async function handleConfirm() {
  if (!selectedCount.value) {
    showNotice('请先选择座位')
    return
  }
  if (!userStore.isLoggedIn) {
    router.push({ name: 'auth', query: { redirect: route.fullPath } })
    return
  }

  const seats = selectedSeatIds.value
    .map((key) => seatStatusMap.value.get(key))
    .filter((seat): seat is SeatCell => seat !== undefined && seat.status === 0)
    .map((seat) => ({ rowNo: seat.rowNo, colNo: seat.colNo }))
  if (!seats.length) {
    error.value = '座位数据异常，请刷新重试'
    return
  }

  creating.value = true
  error.value = ''
  try {
    const order = await createOrder({
      scheduleId: selectedSessionId.value ?? '',
      seats,
    })
    router.push({ name: 'order-confirm', params: { orderId: String(order.id) } })
  } catch (e) {
    error.value = errorMessage(e, '下单失败')
  } finally {
    creating.value = false
  }
}

function goBack() {
  router.push({ name: 'movie-detail', params: { id: movieId } })
}

onMounted(() => {
  void loadSessions()
})
</script>

<template>
  <div class="seat-page">
    <header class="page-header">
      <div class="header-inner">
        <button class="back-btn" type="button" aria-label="返回电影详情" @click="goBack">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M11 18l-6-6 6-6" />
          </svg>
        </button>
        <div class="header-info">
          <h1 class="head-title">{{ seatMap?.movieName || '选择场次' }}</h1>
          <div class="sub-info">
            <span v-if="seatMap?.cinemaName">{{ seatMap.cinemaName }}</span>
            <span v-if="seatMap?.hallName">· {{ seatMap.hallName }}</span>
            <span v-if="seatMap?.startTime">· {{ seatMap.startTime }}</span>
            <span v-else-if="sessions.length">· {{ date }}</span>
          </div>
        </div>
        <router-link class="header-exit" to="/">退出选座</router-link>
      </div>
    </header>

    <div v-if="loadingSessions" class="seat-state">
      <span class="spinner"></span>
      <p>正在加载场次…</p>
    </div>

    <div v-else-if="error && !sessions.length" class="seat-state">
      <span class="state-glyph">!</span>
      <strong>{{ error }}</strong>
      <button class="btn btn-primary" type="button" @click="loadSessions">重试</button>
      <button class="btn btn-ghost on-dark" type="button" @click="goBack">返回电影详情</button>
    </div>

    <template v-else-if="sessions.length">
      <div class="session-bar">
        <div class="session-inner">
          <span class="session-date">
            <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
              <rect x="3" y="5" width="18" height="16" rx="1.5" />
              <path d="M3 10h18M8 3v4M16 3v4" />
            </svg>
            {{ date }}
          </span>
          <div class="session-tabs">
            <button
              v-for="session in sessions"
              :key="session.scheduleId"
              type="button"
              class="session-tab"
              :class="{ 'is-active': String(selectedSessionId) === String(session.scheduleId) }"
              @click="selectedSessionId = String(session.scheduleId)"
            >
              <span class="session-time">{{ timeOnly(session.startTime) }}</span>
              <span class="session-price">¥{{ priceText(session.price) }}</span>
              <span class="session-seat">余 {{ toNumber(session.availableSeat) }}</span>
            </button>
          </div>
        </div>
      </div>

      <div v-if="seatGrid.length" class="seat-area">
        <div class="screen" aria-hidden="true">
          <div class="screen-line"></div>
          <div class="screen-label">S C R E E N / 银幕</div>
        </div>

        <div class="seat-scroll">
          <div class="seat-grid">
            <div class="row-labels">
              <div v-for="(_, rowIndex) in seatGrid" :key="rowIndex" class="row-label">
                {{ rowLetter(rowIndex + 1) }}
              </div>
            </div>

            <div class="seat-body" :class="{ 'skeleton-mode': !seatMap }" @click="onSeatGridClick">
              <div v-for="(row, rowIndex) in seatGrid" :key="rowIndex" class="seat-row">
                <button
                  v-for="seat in row"
                  :key="seat.key"
                  type="button"
                  class="seat"
                  :class="seatClass(seat)"
                  :disabled="seat.status !== 0"
                  :data-key="seat.key"
                  :aria-pressed="selectedSet.has(seat.key)"
                  :aria-label="seatLabel(seat)"
                  :title="seatLabel(seat)"
                ></button>
              </div>
            </div>
          </div>
        </div>

        <p v-if="loadingSeats" class="skeleton-hint">座位数据加载中…</p>

        <div class="legend">
          <span class="legend-item"><i class="legend-dot available"></i>可售座位</span>
          <span class="legend-item"><i class="legend-dot selected"></i>已选选座</span>
          <span class="legend-item"><i class="legend-dot sold"></i>已售座位</span>
        </div>
      </div>

      <div v-else class="seat-state">
        <span class="spinner"></span>
        <p>正在加载座位图…</p>
      </div>

      <footer v-if="seatMap" class="confirm-bar">
        <div class="confirm-inner">
          <div class="selected-info">
            <div class="selected-title">
              <span class="badge badge-green">已选 {{ selectedCount }} 座</span>
              <span class="price">¥{{ totalPrice.toFixed(2) }}</span>
            </div>
            <div class="selected-codes">
              <template v-if="selectedSeatIds.length">
                {{ selectedSeatIds.map(seatCode).filter(Boolean).join(' · ') }}
              </template>
              <template v-else>点击座位开始选座，最多 {{ MAX_SEATS }} 个</template>
            </div>
          </div>
          <p v-if="error" class="bar-error">{{ error }}</p>
          <button
            class="btn btn-primary confirm-btn"
            type="button"
            :disabled="selectedCount === 0 || creating"
            @click="handleConfirm"
          >
            {{ creating ? '下单中…' : '确认选座 →' }}
          </button>
        </div>
      </footer>

      <transition name="toast">
        <p v-if="notice" class="notice">{{ notice }}</p>
      </transition>
    </template>

    <div v-else class="seat-state">
      <span class="state-glyph">空</span>
      <strong>该影院当日暂无场次</strong>
      <button class="btn btn-primary" type="button" @click="goBack">返回电影详情</button>
    </div>
  </div>
</template>

<style scoped>
.seat-page {
  min-height: 100vh;
  padding-bottom: 116px;
  color: var(--white);
  background: var(--ink);
}

/* ---------- 顶部 ---------- */
.page-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(20, 17, 16, 0.94);
  border-bottom: 2px solid rgba(255, 255, 255, 0.16);
  backdrop-filter: blur(14px);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 14px;
  width: min(920px, calc(100% - 48px));
  margin: 0 auto;
  padding: 14px 0;
}

.back-btn {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  color: var(--white);
  background: transparent;
  border: 2px solid rgba(255, 255, 255, 0.28);
  transition:
    color 150ms ease,
    border-color 150ms ease;
}

.back-btn:hover {
  color: var(--red);
  border-color: var(--red);
}

.header-info {
  flex: 1;
  min-width: 0;
}

.head-title {
  overflow: hidden;
  font-size: 19px;
  font-weight: 900;
  letter-spacing: -0.02em;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.sub-info {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-top: 2px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 11.5px;
}

.header-exit {
  flex-shrink: 0;
  padding: 7px 12px;
  color: rgba(255, 255, 255, 0.7);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.12em;
  border: 1px solid rgba(255, 255, 255, 0.24);
  transition:
    color 150ms ease,
    border-color 150ms ease;
}

.header-exit:hover {
  color: var(--white);
  border-color: var(--red);
}

/* ---------- 场次 ---------- */
.session-bar {
  background: #1b1714;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.session-inner {
  display: flex;
  align-items: center;
  gap: 18px;
  width: min(920px, calc(100% - 48px));
  margin: 0 auto;
  padding: 13px 0;
}

.session-date {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex-shrink: 0;
  padding-right: 18px;
  color: rgba(255, 255, 255, 0.55);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.08em;
  border-right: 1px solid rgba(255, 255, 255, 0.16);
}

.session-tabs {
  display: flex;
  gap: 8px;
  overflow-x: auto;
  scrollbar-width: none;
}

.session-tabs::-webkit-scrollbar {
  display: none;
}

.session-tab {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
  min-width: 96px;
  padding: 8px 14px;
  color: rgba(255, 255, 255, 0.72);
  background: transparent;
  border: 2px solid rgba(255, 255, 255, 0.18);
  transition:
    color 150ms ease,
    background-color 150ms ease,
    border-color 150ms ease;
}

.session-tab:hover {
  color: var(--white);
  border-color: rgba(255, 255, 255, 0.42);
}

.session-tab.is-active {
  color: var(--white);
  background: var(--red);
  border-color: var(--red);
}

.session-time {
  font-family: var(--font-display);
  font-size: 17px;
  letter-spacing: 0.03em;
}

.session-price {
  font-size: 10.5px;
  opacity: 0.85;
}

.session-seat {
  color: inherit;
  font-size: 9px;
  opacity: 0.55;
}

/* ---------- 银幕与座位 ---------- */
.seat-area {
  width: min(900px, 100%);
  margin: 0 auto;
  padding: 36px 20px 20px;
}

.seat-scroll {
  overflow-x: auto;
  padding-bottom: 4px;
}

.screen {
  margin-bottom: 34px;
  text-align: center;
}

.screen-line {
  width: min(460px, 66%);
  height: 3px;
  margin: 0 auto;
  background: linear-gradient(90deg, transparent, var(--red), transparent);
  box-shadow: 0 12px 44px 8px rgba(217, 42, 31, 0.35);
  transform: perspective(180px) rotateX(-32deg);
}

.screen-label {
  margin-top: 18px;
  color: rgba(255, 255, 255, 0.45);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.32em;
}

.seat-grid {
  display: flex;
  gap: 14px;
  justify-content: center;
  width: max-content;
  min-width: 100%;
  margin: 0 auto;
}

.row-labels {
  position: sticky;
  left: 0;
  z-index: 1;
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 2px;
  background: var(--ink);
}

.row-label {
  display: grid;
  place-items: center;
  width: 22px;
  height: 28px;
  color: rgba(255, 255, 255, 0.35);
  font-family: var(--font-mono);
  font-size: 9.5px;
}

.seat-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.seat-row {
  display: flex;
  gap: 4px;
}

.seat {
  display: block;
  width: 28px;
  height: 28px;
  padding: 0;
  background: transparent;
  border: 0;
  border-radius: 5px 5px 2px 2px;
  cursor: pointer;
  outline: none;
  transition:
    transform 120ms ease,
    background-color 150ms ease,
    border-color 150ms ease,
    box-shadow 150ms ease;
}

.seat.skeleton {
  background: #272220;
  border: 1px solid #332d29;
  cursor: default;
}

.seat-body.skeleton-mode {
  animation: skeleton-pulse 1.5s ease-in-out infinite;
}

@keyframes skeleton-pulse {
  0%,
  100% {
    opacity: 0.5;
  }
  50% {
    opacity: 0.9;
  }
}

.seat.available {
  background: #2e2825;
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: inset 0 -3px 0 rgba(0, 0, 0, 0.35);
}

.seat.available:hover {
  background: #3a322e;
  border-color: var(--red);
  transform: translateY(-2px);
  box-shadow: 0 5px 14px -4px rgba(217, 42, 31, 0.6);
}

.seat.selected {
  background: #17a34a;
  border: 1px solid #6ee7a0;
  box-shadow:
    0 0 0 1px rgba(34, 197, 94, 0.4),
    0 4px 16px -2px rgba(34, 197, 94, 0.65);
  transform: scale(1.08);
}

/* 已售：红色实心，不可点（锁定中的座位也按已售展示） */
.seat.sold {
  background: rgba(217, 42, 31, 0.82);
  border: 1px solid #ff8d83;
  cursor: not-allowed;
  opacity: 0.72;
  box-shadow: none;
}

/* 影厅没有座位的位置：留白，不渲染成座位 */
.seat.hole {
  background: transparent;
  border: 0;
  cursor: default;
  box-shadow: none;
  pointer-events: none;
}

.seat:disabled {
  cursor: not-allowed;
}

.skeleton-hint {
  width: fit-content;
  margin: 20px auto 0;
  padding: 5px 13px;
  color: rgba(255, 255, 255, 0.5);
  font-size: 10.5px;
  border: 1px solid rgba(255, 255, 255, 0.16);
}

.legend {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 10px 24px;
  margin-top: 32px;
  color: rgba(255, 255, 255, 0.55);
  font-size: 11.5px;
}

.legend-item {
  display: inline-flex;
  align-items: center;
  gap: 7px;
}

.legend-dot {
  width: 16px;
  height: 16px;
  border-radius: 4px 4px 2px 2px;
}

.legend-dot.available {
  background: #2e2825;
  border: 1px solid rgba(255, 255, 255, 0.3);
}

.legend-dot.selected {
  background: #17a34a;
  border: 1px solid #6ee7a0;
}

.legend-dot.sold {
  background: rgba(217, 42, 31, 0.82);
  border: 1px solid #ff8d83;
}

/* ---------- 底部确认 ---------- */
.confirm-bar {
  position: fixed;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 30;
  color: var(--ink);
  background: var(--white);
  border-top: 3px solid var(--red);
  box-shadow: 0 -8px 30px rgba(0, 0, 0, 0.35);
}

.confirm-inner {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: min(920px, calc(100% - 40px));
  margin: 0 auto;
  padding: 13px 0;
}

.selected-info {
  flex: 1;
  min-width: 0;
}

.selected-title {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.selected-title .price {
  color: var(--red);
  font-family: var(--font-display);
  font-size: 24px;
  letter-spacing: 0.02em;
}

.badge-green {
  color: #fff;
  background: #17a34a;
}

.selected-codes {
  margin-top: 3px;
  overflow: hidden;
  color: var(--gray);
  font-size: 11.5px;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.bar-error {
  flex-basis: 100%;
  order: 3;
  color: var(--red);
  font-size: 11.5px;
}

.confirm-btn {
  min-width: 148px;
  height: 48px;
}

.notice {
  position: fixed;
  bottom: 128px;
  left: 50%;
  z-index: 40;
  padding: 10px 18px;
  color: var(--ink);
  font-size: 12.5px;
  font-weight: 700;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 4px 4px 0 var(--red);
  transform: translateX(-50%);
}

.toast-enter-active,
.toast-leave-active {
  transition:
    opacity 180ms ease,
    transform 180ms ease;
}

.toast-enter-from,
.toast-leave-to {
  opacity: 0;
  transform: translate(-50%, 8px);
}

/* ---------- 状态 ---------- */
.seat-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 120px 24px;
  text-align: center;
}

.seat-state p {
  color: rgba(255, 255, 255, 0.55);
  font-size: 12.5px;
}

.seat-state .btn-ghost.on-dark {
  color: var(--white);
  border-color: rgba(255, 255, 255, 0.35);
}

@media (max-width: 760px) {
  .header-inner,
  .session-inner {
    width: calc(100% - 32px);
  }

  .session-date {
    display: none;
  }

  .seat-area {
    padding: 28px 12px 16px;
  }

  .seat {
    width: 24px;
    height: 24px;
  }

  .seat-row,
  .row-labels {
    gap: 3px;
  }

  .row-label {
    width: 20px;
    height: 24px;
  }

  .confirm-btn {
    width: 100%;
  }
}
</style>
