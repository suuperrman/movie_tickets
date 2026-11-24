<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import { applyRefund, cancelOrder, getMyOrders } from '@/api/order'
import type { UserOrderItem } from '@/types'
import { errorMessage, money, toNumber } from '@/utils/format'

interface StatusMeta {
  text: string
  tone: 'pending' | 'paid' | 'warning' | 'muted'
}

const router = useRouter()
const PAGE_SIZE = 10

const loading = ref(true)
const error = ref('')
const actionError = ref('')
const orders = ref<UserOrderItem[]>([])
const total = ref(0)
const pageNo = ref(1)

const STATUS_MAP: Record<number, StatusMeta> = {
  0: { text: '待支付', tone: 'pending' },
  1: { text: '已支付', tone: 'paid' },
  2: { text: '已取消', tone: 'muted' },
  3: { text: '退款中', tone: 'warning' },
  4: { text: '已退款', tone: 'muted' },
  5: { text: '已完成', tone: 'paid' },
}

const filters: Array<{ label: string; value: number | null }> = [
  { label: '全部', value: null },
  { label: '待支付', value: 0 },
  { label: '已支付', value: 1 },
  { label: '退款中', value: 3 },
  { label: '已退款', value: 4 },
  { label: '已取消', value: 2 },
]

const activeStatus = ref<number | null>(null)
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / PAGE_SIZE)))

function statusOf(order: UserOrderItem): StatusMeta {
  return STATUS_MAP[toNumber(order.status)] ?? { text: '未知', tone: 'muted' }
}

async function loadOrders() {
  loading.value = true
  error.value = ''
  actionError.value = ''
  try {
    const params = { pageNo: pageNo.value, pageSize: PAGE_SIZE }
    const page = await getMyOrders(
      activeStatus.value === null ? params : { ...params, status: activeStatus.value },
    )
    orders.value = page.records ?? []
    total.value = toNumber(page.total)
  } catch (e) {
    error.value = errorMessage(e, '加载订单失败')
  } finally {
    loading.value = false
  }
}

function changeFilter(value: number | null) {
  if (activeStatus.value === value) return
  activeStatus.value = value
  pageNo.value = 1
  void loadOrders()
}

function goPage(target: number) {
  if (target < 1 || target > totalPages.value) return
  pageNo.value = target
  void loadOrders()
}

function goDetail(order: UserOrderItem) {
  router.push({ name: 'order-confirm', params: { orderId: String(order.id) } })
}

async function handleCancel(order: UserOrderItem) {
  if (!window.confirm('确认取消该订单？取消后座位将被释放。')) return
  actionError.value = ''
  try {
    await cancelOrder(order.id)
    await loadOrders()
  } catch (e) {
    actionError.value = errorMessage(e, '取消失败')
  }
}

// ===== 退款申请 =====
const refundTarget = ref<UserOrderItem | null>(null)
const refundReason = ref('')
const refundSubmitting = ref(false)
const refundError = ref('')

function openRefund(order: UserOrderItem) {
  refundTarget.value = order
  refundReason.value = ''
  refundError.value = ''
}

function closeRefund() {
  refundTarget.value = null
  refundReason.value = ''
  refundError.value = ''
}

async function submitRefund() {
  if (!refundTarget.value) return
  refundSubmitting.value = true
  refundError.value = ''
  try {
    await applyRefund(refundTarget.value.id, refundReason.value.trim() || undefined)
    closeRefund()
    await loadOrders()
  } catch (e) {
    refundError.value = errorMessage(e, '退款申请失败')
  } finally {
    refundSubmitting.value = false
  }
}

onMounted(() => {
  void loadOrders()
})
</script>

<template>
  <main class="page wrap">
    <header class="order-head">
      <p class="kicker">04 / 票夹 / MY TICKETS</p>
      <h1 class="page-title">我的订单<span class="dot">。</span></h1>
      <p class="page-sub">查看历史购票记录。待支付订单可继续支付，已支付订单可申请退款。</p>
    </header>

    <div class="filters" role="tablist" aria-label="订单状态筛选">
      <button
        v-for="item in filters"
        :key="String(item.value)"
        class="filter-chip"
        :class="{ 'is-active': activeStatus === item.value }"
        type="button"
        role="tab"
        :aria-selected="activeStatus === item.value"
        @click="changeFilter(item.value)"
      >
        {{ item.label }}
      </button>
    </div>

    <p v-if="actionError" class="action-error" role="alert">{{ actionError }}</p>

    <div v-if="loading" class="state-box">
      <span class="spinner"></span>
      <strong>正在整理票夹</strong>
      <p>正在把你的电影票一张张翻出来。</p>
    </div>

    <div v-else-if="error" class="state-box">
      <span class="state-glyph">!</span>
      <strong>订单加载失败</strong>
      <p>{{ error }}</p>
      <button class="btn btn-primary" type="button" @click="loadOrders">重试</button>
    </div>

    <div v-else-if="!orders.length" class="state-box">
      <span class="state-glyph">票</span>
      <strong>这里还没有票</strong>
      <p>选一部电影，挑个好位置，开始第一笔订单。</p>
      <router-link class="btn btn-primary" to="/">去选电影</router-link>
    </div>

    <div v-else class="order-list">
      <article v-for="order in orders" :key="order.id" class="order-card">
        <div class="ticket-head">
          <span class="order-no">NO. {{ order.orderNo }}</span>
          <span class="status-badge" :class="`tone-${statusOf(order).tone}`">
            {{ statusOf(order).text }}
          </span>
        </div>

        <div class="ticket-body">
          <div class="poster">
            <img v-if="order.moviePoster" :src="order.moviePoster" :alt="order.movieName" />
            <span v-else class="poster-fallback">{{ order.movieName.slice(0, 1) }}</span>
          </div>

          <div class="order-info">
            <h2 class="movie-name">{{ order.movieName }}</h2>
            <p class="meta">{{ order.cinemaName }}<template v-if="order.hallName"> · {{ order.hallName }}</template></p>
            <p class="meta time">{{ order.startTime }}</p>
            <p class="meta">共 {{ toNumber(order.seatCount) }} 张票</p>
          </div>

          <div class="amount-box">
            <span class="amount-label">实付</span>
            <span class="amount">¥{{ money(order.payAmount) }}</span>
            <span class="created-at">{{ order.createdAt }}</span>
          </div>
        </div>

        <footer class="ticket-foot">
          <span class="tear" aria-hidden="true"></span>
          <button class="btn btn-sm btn-ghost" type="button" @click="goDetail(order)">查看详情</button>
          <template v-if="toNumber(order.status) === 0">
            <button class="btn btn-sm btn-danger" type="button" @click="handleCancel(order)">取消订单</button>
            <button class="btn btn-sm btn-primary" type="button" @click="goDetail(order)">去支付</button>
          </template>
          <button
            v-else-if="toNumber(order.status) === 1"
            class="btn btn-sm btn-danger"
            type="button"
            @click="openRefund(order)"
          >
            申请退款
          </button>
        </footer>
      </article>
    </div>

    <div v-if="!loading && !error && total > 0" class="pager">
      <button class="page-num" type="button" :disabled="pageNo <= 1" @click="goPage(pageNo - 1)">
        上一页
      </button>
      <span class="pager-info">第 {{ pageNo }} / {{ totalPages }} 页 · 共 {{ total }} 笔</span>
      <button
        class="page-num"
        type="button"
        :disabled="pageNo >= totalPages"
        @click="goPage(pageNo + 1)"
      >
        下一页
      </button>
    </div>

    <div v-if="refundTarget" class="dialog-mask" @click.self="closeRefund">
      <div class="dialog" role="dialog" aria-modal="true" aria-label="申请退款">
        <header class="dialog-head">
          <h3>申请退款</h3>
          <button class="dialog-close" type="button" aria-label="关闭" @click="closeRefund">×</button>
        </header>
        <p class="dialog-sub">订单 {{ refundTarget.orderNo }}，退款提交后需等待管理员审核。</p>
        <textarea
          v-model="refundReason"
          class="reason-input"
          rows="3"
          maxlength="200"
          placeholder="请填写退款原因（选填，最多 200 字）"
        ></textarea>
        <p v-if="refundError" class="dialog-error">{{ refundError }}</p>
        <footer class="dialog-foot">
          <button class="btn btn-ghost" type="button" @click="closeRefund">取消</button>
          <button class="btn btn-primary" type="button" :disabled="refundSubmitting" @click="submitRefund">
            {{ refundSubmitting ? '提交中…' : '提交申请' }}
          </button>
        </footer>
      </div>
    </div>
  </main>
</template>

<style scoped>
.order-head {
  margin-bottom: 26px;
}

.dot {
  color: var(--red);
}

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 26px;
}

.filter-chip {
  height: 36px;
  padding: 0 15px;
  color: var(--gray);
  font-size: 12px;
  font-weight: 700;
  background: var(--white);
  border: 2px solid rgba(20, 17, 16, 0.28);
  transition:
    color 150ms ease,
    background-color 150ms ease,
    border-color 150ms ease,
    box-shadow 150ms ease;
}

.filter-chip:hover {
  color: var(--ink);
  border-color: var(--ink);
}

.filter-chip.is-active {
  color: var(--white);
  background: var(--red);
  border-color: var(--ink);
  box-shadow: 3px 3px 0 var(--ink);
}

.action-error {
  margin-bottom: 16px;
  padding: 10px 14px;
  color: var(--red);
  font-size: 12px;
  background: rgba(217, 42, 31, 0.07);
  border-left: 3px solid var(--red);
}

/* ---------- 票根卡片 ---------- */
.order-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.order-card {
  position: relative;
  background: var(--white);
  border: 2px solid var(--ink);
  transition:
    box-shadow 180ms var(--ease),
    transform 180ms var(--ease);
}

.order-card:hover {
  box-shadow: 7px 7px 0 var(--ink);
  transform: translate(-3px, -3px);
}

.ticket-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 13px 20px;
  border-bottom: 1px solid rgba(20, 17, 16, 0.16);
}

.order-no {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
}

.status-badge {
  flex-shrink: 0;
  padding: 3px 9px;
  font-family: var(--font-mono);
  font-size: 8.5px;
  font-weight: 500;
  letter-spacing: 0.08em;
  border: 1px solid currentColor;
}

.tone-pending {
  color: var(--red);
  background: rgba(217, 42, 31, 0.08);
}

.tone-paid {
  color: var(--white);
  background: var(--ink);
  border-color: var(--ink);
}

.tone-warning {
  color: #a8140c;
  background: rgba(217, 42, 31, 0.12);
}

.tone-muted {
  color: var(--gray);
  border-color: rgba(20, 17, 16, 0.3);
}

.ticket-body {
  display: flex;
  gap: 18px;
  padding: 18px 20px;
}

.poster {
  display: grid;
  place-items: center;
  width: 70px;
  flex-shrink: 0;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  color: rgba(20, 17, 16, 0.22);
  font-size: 34px;
  font-weight: 900;
  background: #ece5d8;
  border: 1px solid rgba(20, 17, 16, 0.3);
}

.poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.order-info {
  flex: 1;
  min-width: 0;
}

.movie-name {
  margin: 2px 0 8px;
  font-size: 20px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.meta {
  color: var(--gray);
  font-size: 11.5px;
  line-height: 1.75;
}

.meta.time {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 10px;
}

.amount-box {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 4px;
  flex-shrink: 0;
}

.amount-label {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.1em;
}

.amount {
  color: var(--red);
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1;
  letter-spacing: 0.02em;
}

.created-at {
  margin-top: 4px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8px;
}

.ticket-foot {
  position: relative;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
  padding: 13px 20px 15px;
  border-top: 2px dashed rgba(20, 17, 16, 0.28);
}

.ticket-foot::before,
.ticket-foot::after {
  content: '';
  position: absolute;
  top: -9px;
  width: 16px;
  height: 16px;
  background: var(--paper);
  border: 2px solid var(--ink);
  border-radius: 50%;
}

.ticket-foot::before {
  left: -9px;
  clip-path: inset(0 0 0 50%);
}

.ticket-foot::after {
  right: -9px;
  clip-path: inset(0 50% 0 0);
}

.tear {
  display: none;
}

/* ---------- 分页 ---------- */
.pager {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  margin-top: 30px;
}

.pager-info {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
}

/* ---------- 退款弹窗 ---------- */
.dialog-mask {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(20, 17, 16, 0.62);
  backdrop-filter: blur(5px);
}

.dialog {
  width: min(440px, 100%);
  padding: 22px;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 10px 10px 0 var(--red);
}

.dialog-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.dialog-head h3 {
  font-size: 21px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.dialog-close {
  display: grid;
  place-items: center;
  width: 30px;
  height: 30px;
  color: var(--gray);
  font-size: 18px;
  background: transparent;
  border: 1px solid rgba(20, 17, 16, 0.3);
}

.dialog-close:hover {
  color: var(--white);
  background: var(--red);
  border-color: var(--red);
}

.dialog-sub {
  margin: 10px 0 14px;
  color: var(--gray);
  font-size: 11.5px;
  line-height: 1.7;
}

.reason-input {
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

.reason-input:focus {
  border-color: var(--red);
  box-shadow: 3px 3px 0 var(--red);
}

.dialog-error {
  margin-top: 10px;
  color: var(--red);
  font-size: 11.5px;
}

.dialog-foot {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 18px;
}

@media (max-width: 700px) {
  .ticket-body {
    flex-wrap: wrap;
  }

  .amount-box {
    align-items: flex-start;
    flex-basis: 100%;
    padding-top: 4px;
  }

  .created-at {
    display: none;
  }

  .order-info {
    flex-basis: calc(100% - 92px);
  }
}

@media (max-width: 520px) {
  .ticket-head {
    padding: 12px 14px;
  }

  .ticket-body {
    padding: 16px 14px;
  }

  .ticket-foot {
    padding: 12px 14px 14px;
  }

  .ticket-foot .btn {
    flex: 1;
  }
}
</style>
