<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'

import { getCinemaCities, getCinemaPage } from '@/api/cinema'
import { useCityStore } from '@/stores/city'
import type { Cinema } from '@/types'
import { errorMessage, toNumber } from '@/utils/format'

const router = useRouter()
const cityStore = useCityStore()

const form = reactive({ keyword: '', city: cityStore.city, address: '' })
const cities = ref<string[]>([])
const list = ref<Cinema[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 10
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
    // 始终以当前城市为主查询条件，避免全量扫描
    const res = await getCinemaPage({
      pageNo: pageNo.value,
      pageSize,
      keyword: form.keyword.trim() || undefined,
      address: form.address.trim() || undefined,
      city: form.city || cityStore.city,
    })
    list.value = res.records ?? []
    total.value = toNumber(res.total)
  } catch (e) {
    error.value = errorMessage(e)
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function submit() {
  pageNo.value = 1
  void load()
}

function onCityChange(value: string) {
  if (value) cityStore.setCity(value)
}

function reset() {
  form.keyword = ''
  form.address = ''
  form.city = cityStore.city
  pageNo.value = 1
  void load()
}

function goPage(page: number) {
  if (page < 1 || page > totalPages.value || page === pageNo.value) return
  pageNo.value = page
  void load()
}

function choose(cinema: Cinema) {
  router.push({ name: 'cinema-detail', params: { id: String(cinema.id) } })
}

onMounted(async () => {
  void load()
  try {
    cities.value = (await getCinemaCities()) ?? []
  } catch {
    cities.value = []
  }
})

// 顶部城市切换后，影院筛选同步跟随
watch(
  () => cityStore.city,
  (city) => {
    if (form.city === city) return
    form.city = city
    pageNo.value = 1
    void load()
  },
)
</script>

<template>
  <main class="page wrap">
    <header class="page-head">
      <span class="kicker">CINEMAS / 影院</span>
      <h1>找一家离你近的影院</h1>
      <p class="lede">按名称、城市或地址筛选，点进影院即可查看在映影片与场次。</p>
    </header>

    <form class="filters" @submit.prevent="submit">
      <label class="field">
        <span>影院名称</span>
        <input v-model="form.keyword" type="search" placeholder="如：万达影城" />
      </label>
      <label class="field">
        <span>城市</span>
        <select v-model="form.city" @change="onCityChange(form.city)">
          <option v-if="!cities.includes(form.city)" :value="form.city">{{ form.city }}</option>
          <option v-for="city in cities" :key="city" :value="city">{{ city }}</option>
        </select>
      </label>
      <label class="field grow">
        <span>具体地址</span>
        <input v-model="form.address" type="search" placeholder="如：解放路、万象城" />
      </label>
      <div class="actions">
        <button class="btn btn-primary" type="submit">筛选</button>
        <button class="btn btn-ghost" type="button" @click="reset">重置</button>
      </div>
    </form>

    <div v-if="error" class="state state-error">
      <strong>加载失败</strong>
      <p>{{ error }}</p>
      <button class="btn btn-primary" type="button" @click="load">重新加载</button>
    </div>

    <div v-else-if="loading && !list.length" class="state">正在加载影院…</div>

    <div v-else-if="!list.length" class="state">
      「{{ form.city || cityStore.city }}」暂无可售影院，试试切换顶部城市
    </div>

    <template v-else>
      <ul class="cinema-list" :class="{ dim: loading }">
        <li v-for="(cinema, index) in list" :key="cinema.id" class="cinema-row">
          <span class="rank">{{ String((pageNo - 1) * pageSize + index + 1).padStart(2, '0') }}</span>

          <div class="cinema-main">
            <div class="cinema-title">
              <h2>{{ cinema.name }}</h2>
              <span class="tag tag-open">营业中</span>
              <span class="tag">退改签</span>
              <span class="tag">影城卡</span>
            </div>
            <p class="addr">
              <span class="city">{{ cinema.city }}</span>
              {{ cinema.address }}
            </p>
            <p class="extra">
              <span v-if="cinema.phone">电话 {{ cinema.phone }}</span>
              <span>{{ toNumber(cinema.hallCount) }} 个影厅</span>
            </p>
          </div>

          <div class="cinema-side">
            <button class="btn btn-primary" type="button" @click="choose(cinema)">
              选座购票
            </button>
          </div>
        </li>
      </ul>

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
        <span class="page-total">共 {{ total }} 家影院</span>
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

.filters {
  display: flex;
  flex-wrap: wrap;
  gap: 14px;
  align-items: flex-end;
  padding: 20px;
  background: var(--white);
  border: 1px solid rgba(20, 17, 16, 0.18);
  margin-bottom: 28px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-width: 180px;
}

.field.grow {
  flex: 1;
  min-width: 220px;
}

.field span {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.18em;
  color: var(--muted);
}

.field input,
.field select {
  height: 40px;
  padding: 0 12px;
  background: var(--paper);
  border: 1px solid rgba(20, 17, 16, 0.22);
  color: var(--ink);
  font-size: 14px;
  font-family: inherit;
  outline: none;
}

.field input:focus,
.field select:focus {
  border-color: var(--red);
}

.actions {
  display: flex;
  gap: 10px;
}

.cinema-list {
  list-style: none;
  margin: 0;
  padding: 0;
  border-top: 2px solid var(--ink);
  transition: opacity 0.2s ease;
}

.cinema-list.dim {
  opacity: 0.45;
  pointer-events: none;
}

.cinema-row {
  display: flex;
  gap: 20px;
  align-items: center;
  padding: 22px 8px;
  border-bottom: 1px solid rgba(20, 17, 16, 0.15);
}

.cinema-row:hover {
  background: rgba(255, 253, 248, 0.75);
}

.rank {
  font-family: var(--font-display);
  font-size: 26px;
  color: rgba(20, 17, 16, 0.18);
  min-width: 44px;
}

.cinema-main {
  flex: 1;
  min-width: 0;
}

.cinema-title {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
}

.cinema-title h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
}

.tag {
  font-size: 11px;
  padding: 2px 7px;
  border: 1px solid rgba(20, 17, 16, 0.25);
  color: var(--gray);
}

.tag-open {
  color: var(--red);
  border-color: rgba(217, 42, 31, 0.45);
}

.addr {
  margin: 8px 0 4px;
  font-size: 13px;
  color: var(--gray);
}

.addr .city {
  display: inline-block;
  margin-right: 8px;
  padding: 1px 6px;
  background: var(--ink);
  color: var(--white);
  font-size: 11px;
}

.extra {
  margin: 0;
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--muted);
  font-family: var(--font-mono);
}

.cinema-side {
  flex-shrink: 0;
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
  margin-top: 32px;
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

@media (max-width: 720px) {
  .page {
    padding: 32px 0 64px;
  }

  .cinema-row {
    flex-wrap: wrap;
  }

  .rank {
    display: none;
  }

  .cinema-side {
    width: 100%;
  }

  .cinema-side .btn {
    width: 100%;
    justify-content: center;
  }
}
</style>
