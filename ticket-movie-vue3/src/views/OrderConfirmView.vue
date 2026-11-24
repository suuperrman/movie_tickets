<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { cancelOrder, getOrderDetail, payOrder } from '@/api/order'
import CountdownTimer from '@/components/CountdownTimer.vue'
import type { OrderDetail } from '@/types'
import { errorMessage, money, priceText, toNumber } from '@/utils/format'

type PayPhase = 'pending' | 'paying' | 'paid' | 'canceled' | 'expired' | 'refunding' | 'refunded'

const route = useRoute()
const router = useRouter()
const orderId = String(route.params.orderId ?? '')

const order = ref<OrderDetail | null>(null)
const loading = ref(true)
const error = ref('')
const phase = ref<PayPhase>('pending')
const payError = ref('')
// 自动取消只尝试一次，避免「取消→刷新→重新挂载→再取消」的死循环
const autoCancelAttempted = ref(false)

const totalAmount = computed(() => money(order.value?.totalAmount))
const seatCount = computed(() => order.value?.items?.length ?? toNumber(order.value?.seatCount))

async function loadOrder() {
  loading.value = true
  error.value = ''
  try {
    order.value = await getOrderDetail(orderId)
    const status = toNumber(order.value.status)
    if (status === 1 || status === 5) phase.value = 'paid'
    else if (status === 2) phase.value = 'canceled'
    else if (status === 3) phase.value = 'refunding'
    else if (status === 4) phase.value = 'refunded'
    else if (phase.value !== 'paying') phase.value = 'pending'
  } catch (e) {
    error.value = errorMessage(e, '加载订单失败')
  } finally {
    loading.value = false
  }
}

async function handlePay() {
  phase.value = 'paying'
  payError.value = ''
  try {
    await payOrder(orderId)
    phase.value = 'paid'
    await loadOrder()
  } catch (e) {
    phase.value = 'pending'
    payError.value = errorMessage(e, '支付失败，请重试')
  }
}

async function handleCancel() {
  if (!window.confirm('确认取消订单？取消后座位将被释放。')) return
  try {
    await cancelOrder(orderId)
    phase.value = 'canceled'
    await loadOrder()
  } catch (e) {
    payError.value = errorMessage(e, '取消失败')
  }
}

async function handleCountdownExpired() {
  if (phase.value !== 'pending' || autoCancelAttempted.value) return
  autoCancelAttempted.value = true
  try {
    await cancelOrder(orderId)
    phase.value = 'canceled'
    await loadOrder()
  } catch (e) {
    await loadOrder()
    if (phase.value === 'pending') {
      phase.value = 'expired'
      payError.value = errorMessage(e, '订单已超时')
    }
  }
}

onMounted(() => {
  void loadOrder()
})
</script>

<template>
  <div class="confirm-page">
    <header class="page-header">
      <div class="header-inner">
        <button class="back-btn" type="button" aria-label="返回上一页" @click="router.back()">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <path d="M19 12H5M11 18l-6-6 6-6" />
          </svg>
        </button>
        <h1 class="head-title">确认订单</h1>
        <span v-if="order" class="head-order-no">No. {{ order.orderNo }}</span>
        <router-link class="head-home" to="/">回首页</router-link>
      </div>
    </header>

    <main class="confirm-main">
      <div v-if="loading" class="state-box">
        <span class="spinner"></span>
        <strong>正在加载订单</strong>
        <p>正在核对座位与金额。</p>
      </div>

      <div v-else-if="error" class="state-box">
        <span class="state-glyph">!</span>
        <strong>订单加载失败</strong>
        <p>{{ error }}</p>
        <button class="btn btn-primary" type="button" @click="loadOrder">重试</button>
      </div>

      <template v-else-if="order">
        <div v-if="phase === 'paid'" class="status-banner paid">
          <span class="status-mark">
            <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4">
              <path d="m4 12.5 5 5L20 6.5" />
            </svg>
          </span>
          <div class="status-text">
            <div class="status-title">支付成功</div>
            <div class="status-sub">凭订单号到影院取票，祝你观影愉快。</div>
          </div>
        </div>

        <div v-else-if="phase === 'canceled'" class="status-banner canceled">
          <span class="status-mark">×</span>
          <div class="status-text">
            <div class="status-title">订单已取消</div>
            <div class="status-sub">座位已释放，可以重新选座下单。</div>
          </div>
        </div>

        <div v-else-if="phase === 'refunding'" class="status-banner canceled">
          <span class="status-mark">¥</span>
          <div class="status-text">
            <div class="status-title">退款处理中</div>
            <div class="status-sub">退款申请已提交，等待管理员审核。</div>
          </div>
        </div>

        <div v-else-if="phase === 'refunded'" class="status-banner canceled">
          <span class="status-mark">¥</span>
          <div class="status-text">
            <div class="status-title">订单已退款</div>
            <div class="status-sub">款项将按原支付渠道退回，请留意到账通知。</div>
          </div>
        </div>

        <div v-else-if="phase === 'expired'" class="status-banner canceled">
          <span class="status-mark">×</span>
          <div class="status-text">
            <div class="status-title">订单已超时</div>
            <div class="status-sub">{{ payError || '支付时间已结束，请返回重新选座下单。' }}</div>
          </div>
        </div>

        <div v-else-if="phase === 'pending' && order.expireAt" class="status-banner pending">
          <CountdownTimer :expire-at="order.expireAt" @expired="handleCountdownExpired" />
          <div class="status-text">
            <div class="status-title">请尽快完成支付</div>
            <div class="status-sub">超时后订单将自动取消，座位将被释放。</div>
          </div>
        </div>

        <section class="card info-card">
          <div class="poster">
            <img v-if="order.moviePoster" :src="order.moviePoster" :alt="order.movieName" />
            <span v-else class="poster-fallback">{{ order.movieName.slice(0, 1) }}</span>
          </div>
          <div class="movie-info">
            <span class="badge badge-red">{{ phase === 'paid' ? '已出票' : '待支付' }}</span>
            <h2 class="movie-name">{{ order.movieName }}</h2>
            <p class="meta">{{ order.cinemaName }}<template v-if="order.hallName"> · {{ order.hallName }}</template></p>
            <p class="meta time">{{ order.startTime }}<template v-if="order.endTime"> — {{ order.endTime }}</template></p>
            <p v-if="order.cinemaAddress" class="meta">{{ order.cinemaAddress }}</p>
          </div>
        </section>

        <section class="card">
          <div class="card-head">
            <h3>座位信息</h3>
            <span class="card-mono">{{ seatCount }} TICKET(S)</span>
          </div>
          <div v-if="order.items?.length" class="seat-list">
            <div v-for="item in order.items" :key="item.seatCode" class="seat-item">
              <span class="seat-code">{{ item.seatCode }}</span>
              <span class="seat-row">{{ toNumber(item.rowNo) }}排{{ toNumber(item.colNo) }}座</span>
              <span class="seat-price">¥{{ priceText(item.price) }}</span>
            </div>
          </div>
          <p v-else class="empty-sub">暂无座位信息</p>
        </section>

        <section class="card">
          <div class="price-row">
            <span>票价</span>
            <span>¥{{ money(order.totalAmount) }}</span>
          </div>
          <div v-if="toNumber(order.discount) > 0" class="price-row">
            <span>优惠</span>
            <span>-¥{{ money(order.discount) }}</span>
          </div>
          <div class="price-row total">
            <span>实付</span>
            <span class="amount">¥{{ totalAmount }}</span>
          </div>
        </section>

        <div class="order-meta">
          <div>订单号 · {{ order.orderNo }}</div>
          <div v-if="order.createdAt">创建时间 · {{ order.createdAt }}</div>
          <div v-if="order.payTime">支付时间 · {{ order.payTime }}</div>
        </div>

        <div v-if="phase === 'pending' || phase === 'paying'" class="actions">
          <button class="btn btn-ghost" type="button" @click="handleCancel">取消订单</button>
          <button
            class="btn btn-primary pay-btn"
            type="button"
            :disabled="phase === 'paying'"
            @click="handlePay"
          >
            {{ phase === 'paying' ? '支付中…' : `立即支付 ¥${totalAmount}` }}
          </button>
          <p v-if="payError" class="pay-error">{{ payError }}</p>
        </div>

        <div v-else class="actions">
          <button class="btn btn-ghost" type="button" @click="router.push('/')">返回首页</button>
          <button class="btn btn-primary" type="button" @click="router.push('/orders')">查看我的订单</button>
        </div>
      </template>
    </main>
  </div>
</template>

<style scoped>
.confirm-page {
  min-height: 100vh;
  padding-bottom: 72px;
  background: var(--paper);
}

/* ---------- 顶部 ---------- */
.page-header {
  position: sticky;
  top: 0;
  z-index: 20;
  background: rgba(255, 253, 248, 0.94);
  border-bottom: 2px solid var(--ink);
  backdrop-filter: blur(14px);
}

.header-inner {
  display: flex;
  align-items: center;
  gap: 14px;
  width: min(760px, calc(100% - 40px));
  margin: 0 auto;
  padding: 14px 0;
}

.back-btn {
  display: grid;
  place-items: center;
  width: 38px;
  height: 38px;
  flex-shrink: 0;
  color: var(--ink);
  background: var(--white);
  border: 2px solid var(--ink);
  transition:
    color 150ms ease,
    border-color 150ms ease;
}

.back-btn:hover {
  color: var(--red);
  border-color: var(--red);
}

.head-title {
  flex: 1;
  font-size: 20px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.head-order-no {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9.5px;
  letter-spacing: 0.08em;
}

.head-home {
  flex-shrink: 0;
  padding: 6px 10px;
  color: var(--gray);
  font-family: var(--font-mono);
  font-size: 9px;
  letter-spacing: 0.1em;
  border: 1px solid rgba(20, 17, 16, 0.28);
}

.head-home:hover {
  color: var(--red);
  border-color: var(--red);
}

.confirm-main {
  width: min(760px, calc(100% - 40px));
  margin: 0 auto;
  padding: 26px 0 0;
}

/* ---------- 状态 ---------- */
.status-banner {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 16px;
  padding: 18px 20px;
  border: 2px solid var(--ink);
}

.status-banner.pending {
  background: var(--white);
  border-left: 6px solid var(--red);
}

.status-banner.paid {
  color: var(--white);
  background: var(--ink);
  border-color: var(--ink);
  box-shadow: 6px 6px 0 var(--red);
}

.status-banner.canceled {
  color: var(--gray);
  background: rgba(20, 17, 16, 0.04);
  border-style: dashed;
  border-color: rgba(20, 17, 16, 0.35);
}

.status-mark {
  display: grid;
  place-items: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  color: var(--white);
  font-size: 22px;
  font-weight: 900;
  background: var(--red);
  border: 2px solid var(--ink);
}

.status-banner.paid .status-mark {
  color: var(--ink);
  background: var(--white);
  border-color: var(--white);
}

.status-banner.canceled .status-mark {
  color: var(--gray);
  background: transparent;
  border-color: rgba(20, 17, 16, 0.35);
}

.status-text {
  flex: 1;
  min-width: 0;
}

.status-title {
  font-family: var(--font-display);
  font-size: 21px;
  letter-spacing: 0.02em;
}

.status-sub {
  margin-top: 3px;
  color: var(--gray);
  font-size: 11.5px;
  line-height: 1.6;
}

.status-banner.paid .status-sub {
  color: rgba(255, 255, 255, 0.6);
}

/* ---------- 卡片 ---------- */
.card {
  margin-bottom: 14px;
  padding: 20px;
  background: var(--white);
  border: 2px solid var(--ink);
}

.card-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  padding-bottom: 10px;
  border-bottom: 1px solid rgba(20, 17, 16, 0.16);
}

.card-head h3 {
  font-size: 13.5px;
  font-weight: 900;
}

.card-mono {
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.14em;
}

.info-card {
  display: flex;
  gap: 18px;
}

.poster {
  display: grid;
  place-items: center;
  width: 76px;
  flex-shrink: 0;
  aspect-ratio: 2 / 3;
  overflow: hidden;
  color: rgba(20, 17, 16, 0.2);
  font-size: 40px;
  font-weight: 900;
  background: #ece5d8;
  border: 2px solid var(--ink);
}

.poster img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.movie-info {
  flex: 1;
  min-width: 0;
}

.movie-name {
  margin: 8px 0 8px;
  font-size: 22px;
  font-weight: 900;
  letter-spacing: -0.02em;
}

.meta {
  color: var(--gray);
  font-size: 11.5px;
  line-height: 1.7;
}

.meta.time {
  color: var(--ink);
  font-family: var(--font-mono);
  font-size: 10.5px;
}

/* ---------- 座位 ---------- */
.seat-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 10px 0;
  font-size: 12.5px;
}

.seat-item + .seat-item {
  border-top: 1px dashed rgba(20, 17, 16, 0.2);
}

.seat-code {
  min-width: 56px;
  color: var(--red);
  font-family: var(--font-mono);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.04em;
}

.seat-row {
  flex: 1;
  color: var(--gray);
  font-size: 11.5px;
}

.seat-price {
  font-variant-numeric: tabular-nums;
}

/* ---------- 金额 ---------- */
.price-row {
  display: flex;
  justify-content: space-between;
  padding: 4px 0;
  color: var(--gray);
  font-size: 12.5px;
  font-variant-numeric: tabular-nums;
}

.price-row.total {
  margin-top: 10px;
  padding-top: 13px;
  color: var(--ink);
  font-size: 14px;
  font-weight: 700;
  border-top: 2px solid var(--ink);
}

.price-row .amount {
  color: var(--red);
  font-family: var(--font-display);
  font-size: 28px;
  letter-spacing: 0.02em;
}

.order-meta {
  margin-bottom: 20px;
  color: var(--muted);
  font-family: var(--font-mono);
  font-size: 9px;
  line-height: 2;
  letter-spacing: 0.05em;
}

/* ---------- 操作 ---------- */
.actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.pay-btn {
  min-height: 50px;
  padding: 0 28px;
  font-size: 14px;
}

.pay-error {
  flex-basis: 100%;
  color: var(--red);
  font-size: 11.5px;
  text-align: right;
}

.empty-sub {
  color: var(--gray);
  font-size: 12.5px;
}

@media (max-width: 560px) {
  .head-order-no {
    display: none;
  }

  .actions {
    flex-direction: column-reverse;
    align-items: stretch;
  }

  .actions .btn {
    width: 100%;
  }

  .pay-error {
    text-align: center;
  }
}
</style>
