<template>
  <div class="seat-manage">
    <!-- 顶部信息栏 -->
    <el-card shadow="never" class="header-card">
      <div class="page-header">
        <div class="header-left">
          <el-button @click="goBack">
            <el-icon><ArrowLeft /></el-icon>返回影厅列表
          </el-button>
          <div class="info">
            <h3 class="title">{{ hallName }} - 座位管理</h3>
            <span class="subtitle">{{ cinemaName }} · {{ hallTypeDesc }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-button
            type="primary"
            :disabled="hasSeats"
            @click="handleBatchGenerate"
          >
            <el-icon><Grid /></el-icon>批量生成座位
          </el-button>
          <el-button
            v-if="hasSeats"
            type="danger"
            :loading="regenerating"
            @click="handleRegenerate"
          >
            <el-icon><Refresh /></el-icon>重新生成
          </el-button>
        </div>
      </div>
    </el-card>

    <!-- 座位图区域 -->
    <el-card shadow="never" class="seat-card">
      <div v-loading="loading" class="seat-container">
        <!-- 银幕 -->
        <div class="screen">
          <div class="screen-inner">银 幕</div>
        </div>

        <!-- 空状态 -->
        <el-empty
          v-if="!loading && !hasSeats"
          description="暂无座位，点击右上角「批量生成座位」"
          :image-size="120"
        >
          <el-button type="primary" @click="handleBatchGenerate">
            <el-icon><Grid /></el-icon>批量生成座位
          </el-button>
        </el-empty>

        <!-- 座位图 -->
        <div v-if="hasSeats" class="seat-grid-wrap">
          <div class="seat-grid" :style="gridStyle">
            <div
              v-for="seat in seatList"
              :key="`${seat.rowNo}-${seat.colNo}`"
              class="seat-item"
              :class="{
                'seat-available': seat.status === 1,
                'seat-disabled': seat.status === 0,
              }"
              :title="`${seat.rowNo}排${seat.colNo}座`"
              @click="toggleSeat(seat)"
            >
              {{ seat.colNo }}
            </div>
          </div>
        </div>

        <!-- 统计信息 -->
        <div v-if="hasSeats" class="seat-legend">
          <div class="legend-item">
            <span class="seat-dot seat-available"></span>
            <span>可用座位</span>
            <span class="count">{{ availableCount }}</span>
          </div>
          <div class="legend-item">
            <span class="seat-dot seat-disabled"></span>
            <span>不可用</span>
            <span class="count">{{ disabledCount }}</span>
          </div>
          <div class="legend-item total">
            <span>总计</span>
            <span class="count">{{ totalCount }} 座</span>
          </div>
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import { getHallDetail } from '@/api/hall.js';
import { batchGenerateSeats, getSeatPage } from '@/api/seat.js';

const route = useRoute();
const router = useRouter();

const hallId = computed(() => Number(route.params.hallId));

const loading = ref(false);
const regenerating = ref(false);
const hallName = ref('');
const cinemaName = ref('');
const hallTypeDesc = ref('');
const rowCount = ref(0);
const colCount = ref(0);
const seatList = ref([]);

const hasSeats = computed(() => seatList.value.length > 0);

const availableCount = computed(() => seatList.value.filter((s) => s.status === 1).length);
const disabledCount = computed(() => seatList.value.filter((s) => s.status === 0).length);
const totalCount = computed(() => seatList.value.length);

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${colCount.value}, 1fr)`,
}));

async function loadHallInfo() {
  try {
    const detail = await getHallDetail(hallId.value);
    hallName.value = detail.name;
    hallTypeDesc.value = detail.typeDesc || '普通厅';
    rowCount.value = detail.rowCount || 0;
    colCount.value = detail.colCount || 0;
    // 从影厅详情里拿影院名可能没有，先简单处理
    cinemaName.value = detail.cinemaName || '';
  } catch (err) {
    // 拦截器已提示
  }
}

async function loadSeats() {
  loading.value = true;
  try {
    const page = await getSeatPage({
      hallId: hallId.value,
      pageNo: 1,
      pageSize: 2500, // 最多支持 50x50=2500
    });
    const list = page.records || [];
    // 按行、列排序
    list.sort((a, b) => {
      if (a.rowNo !== b.rowNo) return a.rowNo - b.rowNo;
      return a.colNo - b.colNo;
    });
    seatList.value = list;
    if (list.length > 0) {
      const rows = Math.max(...list.map((s) => s.rowNo));
      const cols = Math.max(...list.map((s) => s.colNo));
      if (!rowCount.value) rowCount.value = rows;
      if (!colCount.value) colCount.value = cols;
    }
  } finally {
    loading.value = false;
  }
}

async function handleBatchGenerate() {
  try {
    await ElMessageBox.confirm(
      '确认根据影厅规格批量生成座位吗？生成后可以点击座位切换状态。',
      '生成确认',
      { type: 'info' },
    );
  } catch {
    return;
  }

  regenerating.value = true;
  try {
    const count = await batchGenerateSeats(hallId.value);
    ElMessage.success(`成功生成 ${count} 个座位`);
    loadSeats();
  } catch (err) {
    // 拦截器已提示
  } finally {
    regenerating.value = false;
  }
}

async function handleRegenerate() {
  try {
    await ElMessageBox.confirm(
      '重新生成会清空现有座位并重新生成，确认继续吗？',
      '重新生成确认',
      {
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    );
  } catch {
    return;
  }

  regenerating.value = true;
  try {
    const count = await batchGenerateSeats(hallId.value);
    ElMessage.success(`成功重新生成 ${count} 个座位`);
    loadSeats();
  } catch (err) {
    // 拦截器已提示
  } finally {
    regenerating.value = false;
  }
}

// 点击座位切换可用/不可用（后续可以加单独修改接口，这里先做展示）
function toggleSeat(seat) {
  ElMessage.info(`座位：${seat.rowNo}排${seat.colNo}座，状态：${seat.status === 1 ? '可用' : '不可用'}`);
}

function goBack() {
  // 返回对应影院的影厅列表
  router.back();
}

onMounted(() => {
  loadHallInfo();
  loadSeats();
});
</script>

<style scoped>
.seat-manage {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.header-card {
  padding: 16px 20px;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16px;
}

.info .title {
  margin: 0 0 2px;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.info .subtitle {
  font-size: 13px;
  color: #6b7280;
}

.header-right {
  display: flex;
  gap: 10px;
}

.seat-card {
  padding: 20px;
}

.seat-container {
  min-height: 300px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 30px;
}

/* 银幕 */
.screen {
  width: 60%;
  max-width: 600px;
}

.screen-inner {
  height: 32px;
  background: linear-gradient(180deg, #e5e7eb 0%, #f3f4f6 100%);
  border-radius: 0 0 50% 50% / 0 0 100% 100%;
  display: grid;
  place-items: end center;
  padding-bottom: 6px;
  font-size: 13px;
  color: #6b7280;
  letter-spacing: 10px;
  text-indent: 10px;
}

/* 座位图 */
.seat-grid-wrap {
  width: 100%;
  display: flex;
  justify-content: center;
  overflow-x: auto;
  padding: 10px;
}

.seat-grid {
  display: grid;
  gap: 6px;
  justify-content: center;
}

.seat-item {
  width: 28px;
  height: 28px;
  border-radius: 6px 6px 2px 2px;
  display: grid;
  place-items: center;
  font-size: 10px;
  cursor: pointer;
  transition: transform 0.1s ease, box-shadow 0.1s ease;
  user-select: none;
}

.seat-item:hover {
  transform: scale(1.15);
  z-index: 1;
}

.seat-available {
  background: #86efac;
  color: #14532d;
  border: 1px solid #4ade80;
}

.seat-disabled {
  background: #e5e7eb;
  color: #9ca3af;
  border: 1px solid #d1d5db;
  text-decoration: line-through;
}

/* 图例 */
.seat-legend {
  display: flex;
  align-items: center;
  gap: 32px;
  padding: 12px 20px;
  background: #f9fafb;
  border-radius: 8px;
}

.legend-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #374151;
}

.legend-item .count {
  font-weight: 600;
  color: #1f2937;
}

.legend-item.total {
  margin-left: auto;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-color-primary);
}

.legend-item.total .count {
  font-size: 18px;
  color: var(--el-color-primary);
}

.seat-dot {
  width: 18px;
  height: 18px;
  border-radius: 4px 4px 1px 1px;
  display: inline-block;
}

.seat-dot.seat-available {
  background: #86efac;
  border: 1px solid #4ade80;
}

.seat-dot.seat-disabled {
  background: #e5e7eb;
  border: 1px solid #d1d5db;
}
</style>
