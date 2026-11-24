<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'

const props = defineProps<{
  /** 过期时间："yyyy-MM-dd HH:mm:ss"、ISO 或时间戳 */
  expireAt: string | number
}>()

const emit = defineEmits<{
  expired: []
}>()

const remaining = ref(0)
let timer: number | null = null
let emitted = false

function toTimestamp(value: string | number): number {
  if (value === null || value === undefined || value === '') return 0
  if (typeof value === 'number') return value
  return new Date(String(value).replace(' ', 'T')).getTime()
}

function stop() {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

function tick() {
  const diff = toTimestamp(props.expireAt) - Date.now()
  remaining.value = Math.max(0, Math.floor(diff / 1000))
  if (remaining.value <= 0) {
    stop()
    if (!emitted) {
      emitted = true
      emit('expired')
    }
  }
}

function start() {
  stop()
  emitted = false
  tick()
  if (remaining.value > 0) {
    timer = window.setInterval(tick, 1000)
  }
}

const display = computed(() => {
  const total = remaining.value
  const hours = Math.floor(total / 3600)
  const minutes = Math.floor((total % 3600) / 60)
  const seconds = total % 60
  const mm = String(minutes).padStart(2, '0')
  const ss = String(seconds).padStart(2, '0')
  return hours > 0 ? `${hours}:${mm}:${ss}` : `${mm}:${ss}`
})

watch(() => props.expireAt, start, { immediate: true })

onBeforeUnmount(stop)
</script>

<template>
  <div class="countdown" :class="{ urgent: remaining <= 300 }" role="timer" aria-live="polite">
    <span class="cd-label">支付剩余</span>
    <span class="cd-time">{{ display }}</span>
  </div>
</template>

<style scoped>
.countdown {
  display: flex;
  flex-direction: column;
  align-items: center;
  flex-shrink: 0;
  min-width: 118px;
  padding: 8px 14px;
  color: var(--white);
  background: var(--ink);
  border: 2px solid var(--ink);
  box-shadow: 4px 4px 0 var(--red);
}

.cd-label {
  color: rgba(255, 255, 255, 0.55);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.14em;
}

.cd-time {
  margin-top: 3px;
  color: var(--white);
  font-family: var(--font-display);
  font-size: 26px;
  line-height: 1.1;
  letter-spacing: 0.04em;
  font-variant-numeric: tabular-nums;
}

.countdown.urgent {
  box-shadow: 4px 4px 0 #ff8d83;
}

.countdown.urgent .cd-time {
  color: #ff9b91;
}
</style>
