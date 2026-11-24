<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'

import { useCityStore } from '@/stores/city'

const cityStore = useCityStore()
const open = ref(false)
const rootRef = ref<HTMLElement | null>(null)

function toggle() {
  open.value = !open.value
}

function choose(name: string) {
  cityStore.setCity(name)
  open.value = false
}

async function handleLocate() {
  await cityStore.locate()
}

function onDocumentPointerDown(event: PointerEvent) {
  if (!open.value) return
  const target = event.target as Node | null
  if (target && rootRef.value && !rootRef.value.contains(target)) {
    open.value = false
  }
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') open.value = false
}

onMounted(() => {
  document.addEventListener('pointerdown', onDocumentPointerDown)
  document.addEventListener('keydown', onKeydown)
  // 首次访问自动尝试定位
  if (!cityStore.located) void cityStore.locate()
})

onBeforeUnmount(() => {
  document.removeEventListener('pointerdown', onDocumentPointerDown)
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div ref="rootRef" class="city-picker">
    <button class="city-btn" type="button" :aria-expanded="open" @click="toggle">
      <svg width="13" height="13" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8">
        <path d="M12 21s7-6.2 7-11a7 7 0 1 0-14 0c0 4.8 7 11 7 11Z" />
        <circle cx="12" cy="10" r="2.6" />
      </svg>
      <span>{{ cityStore.city }}</span>
      <svg
        class="caret"
        :class="{ up: open }"
        width="9"
        height="9"
        viewBox="0 0 24 24"
        fill="none"
        stroke="currentColor"
        stroke-width="2.4"
      >
        <path d="m6 9 6 6 6-6" />
      </svg>
    </button>

    <div v-if="open" class="panel">
      <div class="panel-row">
        <span class="panel-title">当前城市</span>
        <button class="locate" type="button" :disabled="cityStore.locating" @click="handleLocate">
          {{ cityStore.locating ? '定位中…' : '重新定位' }}
        </button>
      </div>

      <div class="current">
        <strong>{{ cityStore.city }}</strong>
        <span>{{ cityStore.located ? '已定位' : '默认城市' }}</span>
      </div>

      <div class="panel-row">
        <span class="panel-title">广西</span>
        <span class="panel-hint">选择后自动按城市查询影院</span>
      </div>

      <div class="city-grid">
        <button
          v-for="city in cityStore.cities"
          :key="city"
          class="city-item"
          :class="{ active: city === cityStore.city }"
          type="button"
          @click="choose(city)"
        >
          {{ city }}
        </button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.city-picker {
  position: relative;
  flex-shrink: 0;
}

.city-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 6px 8px;
  background: transparent;
  border: 1px solid transparent;
  color: var(--ink);
  font-family: inherit;
  font-size: 14px;
  cursor: pointer;
}

.city-btn:hover {
  border-color: rgba(20, 17, 16, 0.22);
}

.caret {
  transition: transform 0.15s ease;
  color: var(--muted);
}

.caret.up {
  transform: rotate(180deg);
}

.panel {
  position: absolute;
  top: calc(100% + 12px);
  left: 0;
  z-index: 80;
  width: 320px;
  padding: 16px;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 6px 6px 0 rgba(20, 17, 16, 0.16);
}

.panel-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.panel-title {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.2em;
  color: var(--muted);
}

.panel-hint {
  font-size: 11px;
  color: var(--muted);
}

.locate {
  background: transparent;
  border: 0;
  padding: 0;
  color: var(--red);
  font-size: 12px;
  cursor: pointer;
}

.locate:disabled {
  color: var(--muted);
  cursor: wait;
}

.current {
  display: flex;
  align-items: baseline;
  gap: 8px;
  padding: 8px 0 12px;
  border-bottom: 1px dashed rgba(20, 17, 16, 0.2);
  margin-bottom: 12px;
}

.current strong {
  font-size: 18px;
}

.current span {
  font-size: 11px;
  color: var(--muted);
}

.city-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 6px;
}

.city-item {
  appearance: none;
  padding: 7px 4px;
  background: var(--paper);
  border: 1px solid rgba(20, 17, 16, 0.16);
  color: var(--ink);
  font-size: 12px;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.12s ease;
}

.city-item:hover {
  border-color: var(--red);
  color: var(--red);
}

.city-item.active {
  background: var(--red);
  border-color: var(--red);
  color: var(--white);
}

@media (max-width: 620px) {
  .panel {
    width: 280px;
  }
}
</style>
