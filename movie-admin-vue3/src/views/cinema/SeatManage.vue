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
            <span class="subtitle">{{ cinemaName }}</span>
          </div>
        </div>
        <div class="header-right">
          <el-button @click="openAddDialog">
            <el-icon><Plus /></el-icon>新增座位
          </el-button>
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
          <el-button
            v-if="hasSeats"
            :type="deleteMode ? 'danger' : 'default'"
            :plain="!deleteMode"
            @click="toggleDeleteMode"
          >
            <el-icon><Delete /></el-icon>{{ deleteMode ? '退出删除' : '删除座位' }}
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

        <!-- 删除模式提示 -->
        <el-alert
          v-if="hasSeats && deleteMode"
          class="delete-tip"
          type="warning"
          show-icon
          :closable="false"
          title="删除模式：点击座位即可删除，已被场次引用的座位无法删除"
        />

        <!-- 座位图 -->
        <div v-if="hasSeats" class="seat-grid-wrap">
          <div class="seat-grid" :class="{ 'delete-mode': deleteMode }" :style="gridStyle">
            <div
              v-for="cell in gridCells"
              :key="`${cell.rowNo}-${cell.colNo}`"
              class="seat-item"
              :class="{
                'seat-available': cell.seat && cell.seat.isActive === 1,
                'seat-disabled': cell.seat && cell.seat.isActive === 0,
                'seat-updating': cell.seat && updatingId === cell.seat.id,
                'seat-empty': !cell.seat,
              }"
              :title="cell.seat ? `${rowLetter(cell.rowNo)}${cell.colNo} 座` : ''"
              @click="cell.seat && handleSeatClick(cell.seat)"
            >
              {{ cell.seat ? `${rowLetter(cell.rowNo)}${cell.seat.colNo}` : '' }}
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

    <!-- 新增座位 -->
    <el-dialog
      v-model="addDialogVisible"
      title="新增座位"
      width="440px"
      :close-on-click-modal="false"
      @closed="resetAddForm"
    >
      <el-form ref="addFormRef" :model="addForm" :rules="addRules" label-width="88px">
        <el-form-item label="行号" prop="rowNo">
          <el-input-number
            v-model="addForm.rowNo"
            :min="1"
            :max="50"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="列号" prop="colNo">
          <el-input-number
            v-model="addForm.colNo"
            :min="1"
            :max="50"
            controls-position="right"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="座位编号">
          <el-input
            v-model="addForm.seatCode"
            maxlength="20"
            placeholder="留空自动生成，如 A1"
          />
        </el-form-item>
        <el-form-item label="状态">
          <el-radio-group v-model="addForm.isActive">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="addDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitAddSeat">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import { getHallDetail } from '@/api/hall.js';
import { batchGenerateSeats, createSeat, deleteSeat, getSeatPage, updateSeat } from '@/api/seat.js';

const route = useRoute();
const router = useRouter();

// 雪花 ID 超出 JS 安全整数范围，必须保持字符串，不能转 Number
const hallId = computed(() => route.params.hallId);

const loading = ref(false);
const regenerating = ref(false);
const hallName = ref('');
const cinemaName = ref('');
const rowCount = ref(0);
const colCount = ref(0);
const seatList = ref([]);

const hasSeats = computed(() => seatList.value.length > 0);

const updatingId = ref(null);
const creating = ref(false);
const deleteMode = ref(false);
const addDialogVisible = ref(false);
const addFormRef = ref();

const addForm = reactive({
  rowNo: 1,
  colNo: 1,
  seatCode: '',
  isActive: 1,
});

const addRules = {
  rowNo: [{ required: true, message: '请输入行号', trigger: 'change' }],
  colNo: [{ required: true, message: '请输入列号', trigger: 'change' }],
};

const availableCount = computed(() => seatList.value.filter((s) => s.isActive === 1).length);
const disabledCount = computed(() => seatList.value.filter((s) => s.isActive === 0).length);
const totalCount = computed(() => seatList.value.length);

const gridStyle = computed(() => ({
  gridTemplateColumns: `repeat(${colCount.value}, 1fr)`,
}));

// 按行列生成网格单元，缺失的座位用空位占位，避免删座后整体错位
const seatMap = computed(() => {
  const map = new Map();
  seatList.value.forEach((s) => map.set(`${s.rowNo}-${s.colNo}`, s));
  return map;
});

const gridCells = computed(() => {
  const cells = [];
  for (let r = 1; r <= rowCount.value; r++) {
    for (let c = 1; c <= colCount.value; c++) {
      cells.push({
        rowNo: r,
        colNo: c,
        seat: seatMap.value.get(`${r}-${c}`) || null,
      });
    }
  }
  return cells;
});

async function loadHallInfo() {
  try {
    const detail = await getHallDetail(hallId.value);
    hallName.value = detail.name;
    rowCount.value = Math.max(rowCount.value, detail.rowCount || 0);
    colCount.value = Math.max(colCount.value, detail.colCount || 0);
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
    if (!list.length) deleteMode.value = false;
    if (list.length > 0) {
      const rows = Math.max(...list.map((s) => s.rowNo));
      const cols = Math.max(...list.map((s) => s.colNo));
      rowCount.value = Math.max(rowCount.value, rows);
      colCount.value = Math.max(colCount.value, cols);
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

// 行号转字母：1=A、2=B … 26=Z、27=AA
function rowLetter(rowNo) {
  let n = Number(rowNo);
  let label = '';
  while (n > 0) {
    const rem = (n - 1) % 26;
    label = String.fromCharCode(65 + rem) + label;
    n = Math.floor((n - 1) / 26);
  }
  return label || '';
}

// 找一个空闲座位作为默认行列
function findFreePosition() {
  const used = new Set(seatList.value.map((s) => `${s.rowNo}-${s.colNo}`));
  for (let r = 1; r <= 50; r++) {
    for (let c = 1; c <= 50; c++) {
      if (!used.has(`${r}-${c}`)) return { rowNo: r, colNo: c };
    }
  }
  return { rowNo: 1, colNo: 1 };
}

function openAddDialog() {
  const pos = findFreePosition();
  addForm.rowNo = pos.rowNo;
  addForm.colNo = pos.colNo;
  addForm.seatCode = '';
  addForm.isActive = 1;
  addDialogVisible.value = true;
}

function resetAddForm() {
  addFormRef.value?.clearValidate();
}

async function submitAddSeat() {
  try {
    await addFormRef.value.validate();
  } catch {
    return;
  }

  const { rowNo, colNo, isActive } = addForm;
  const seatCode = addForm.seatCode.trim();

  if (seatList.value.some((s) => s.rowNo === rowNo && s.colNo === colNo)) {
    ElMessage.warning(`${rowLetter(rowNo)}${colNo} 座已存在，请更换行号或列号`);
    return;
  }

  creating.value = true;
  try {
    await createSeat({
      hallId: hallId.value,
      rowNo,
      colNo,
      seatCode: seatCode || undefined,
      isActive,
    });
    ElMessage.success(`已新增 ${rowLetter(rowNo)}${colNo} 座`);
    addDialogVisible.value = false;
    await loadSeats();
  } catch (err) {
    // 拦截器已提示
  } finally {
    creating.value = false;
  }
}

function toggleDeleteMode() {
  deleteMode.value = !deleteMode.value;
}

function handleSeatClick(seat) {
  if (deleteMode.value) {
    handleDeleteSeat(seat);
    return;
  }
  toggleSeat(seat);
}

async function handleDeleteSeat(seat) {
  if (updatingId.value) return;

  try {
    await ElMessageBox.confirm(
      `确定删除 ${rowLetter(seat.rowNo)}${seat.colNo} 座（${seat.seatCode || '未编号'}）吗？删除后不可恢复，已被场次引用的座位无法删除。`,
      '删除座位',
      {
        type: 'warning',
        confirmButtonText: '删除',
        confirmButtonClass: 'el-button--danger',
      },
    );
  } catch {
    return;
  }

  updatingId.value = seat.id;
  try {
    await deleteSeat(seat.id);
    ElMessage.success('座位已删除');
    await loadSeats();
  } catch (err) {
    // 拦截器已提示，如座位已被场次引用
  } finally {
    updatingId.value = null;
  }
}

// 点击座位切换启用/停用
async function toggleSeat(seat) {
  if (updatingId.value) return;

  const nextActive = seat.isActive === 1 ? 0 : 1;
  updatingId.value = seat.id;
  try {
    await updateSeat(seat.id, {
      hallId: seat.hallId ?? hallId.value,
      rowNo: seat.rowNo,
      colNo: seat.colNo,
      seatCode: seat.seatCode,
      isActive: nextActive,
    });
    seat.isActive = nextActive;
    ElMessage.success(
      `${seat.rowNo}排${seat.colNo}座已${nextActive === 1 ? '启用' : '停用'}`,
    );
  } catch (err) {
    // 拦截器已提示
  } finally {
    updatingId.value = null;
  }
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
  flex-wrap: wrap;
  justify-content: flex-end;
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
  width: 32px;
  height: 30px;
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

.seat-updating {
  opacity: 0.5;
  cursor: wait;
  pointer-events: none;
}

.seat-empty {
  background: transparent;
  border: 1px dashed #e5e7eb;
  color: transparent;
  cursor: default;
}

.seat-empty:hover {
  transform: none;
  box-shadow: none;
}

/* 删除模式 */
.delete-tip {
  width: 100%;
}

.seat-grid.delete-mode .seat-item {
  cursor: crosshair;
}

.seat-grid.delete-mode .seat-item:not(.seat-empty):hover {
  background: #fee2e2;
  border-color: #ef4444;
  color: #991b1b;
  box-shadow: 0 0 0 2px rgba(239, 68, 68, 0.25);
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
