<template>
  <div class="schedule-list">
    <!-- 搜索筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="电影">
          <el-select
            v-model="queryForm.movieId"
            placeholder="全部电影"
            clearable
            filterable
            style="width: 200px"
          >
            <el-option
              v-for="m in movieOptions"
              :key="m.id"
              :label="m.name"
              :value="m.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="影院">
          <el-select
            v-model="queryForm.cinemaId"
            placeholder="全部影院"
            clearable
            filterable
            style="width: 180px"
          >
            <el-option
              v-for="c in cinemaOptions"
              :key="c.id"
              :label="c.name"
              :value="c.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="影厅">
          <el-select
            v-model="queryForm.hallId"
            placeholder="全部影厅"
            clearable
            :disabled="!queryForm.cinemaId"
            style="width: 140px"
          >
            <el-option
              v-for="h in hallOptions"
              :key="h.id"
              :label="h.name"
              :value="h.id"
            />
          </el-select>
        </el-form-item>

        <el-form-item label="日期">
          <el-date-picker
            v-model="queryForm.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期"
            style="width: 160px"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部状态"
            clearable
            style="width: 120px"
          >
            <el-option label="可售" :value="1" />
            <el-option label="停售" :value="0" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" @click="handleSearch">
            <el-icon><Search /></el-icon>查询
          </el-button>
          <el-button @click="handleReset">
            <el-icon><Refresh /></el-icon>重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 数据表格 -->
    <el-card shadow="never" class="table-card">
      <div class="table-header">
        <span class="table-title">排片列表</span>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新增排片
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column label="电影" min-width="180">
          <template #default="{ row }">
            <div class="movie-cell">
              <img v-if="row.moviePoster" :src="row.moviePoster" class="movie-poster" />
              <span class="movie-name">{{ row.movieName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="cinemaName" label="影院" min-width="160" />
        <el-table-column prop="hallName" label="影厅" width="120" />
        <el-table-column label="开场时间" width="160" align="center">
          <template #default="{ row }">
            <span class="time-strong">{{ row.startTime }}</span>
          </template>
        </el-table-column>
        <el-table-column label="散场时间" width="160" align="center">
          <template #default="{ row }">{{ row.endTime }}</template>
        </el-table-column>
        <el-table-column label="票价" width="100" align="center">
          <template #default="{ row }">
            <span class="price">¥{{ row.price }}</span>
          </template>
        </el-table-column>
        <el-table-column label="座位" width="120" align="center">
          <template #default="{ row }">
            <el-progress
              :percentage="seatPercent(row)"
              :color="seatColor(row)"
              :stroke-width="10"
              :show-text="false"
              style="width: 80px; display: inline-block"
            />
            <span class="seat-count">
              {{ row.availableSeat }}/{{ row.totalSeat }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small">
              {{ row.status === 1 ? '可售' : '停售' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '停售' : '开售' }}
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          v-model:current-page="pagination.pageNo"
          v-model:page-size="pagination.pageSize"
          :page-sizes="[10, 20, 50, 100]"
          :total="pagination.total"
          layout="total, sizes, prev, pager, next, jumper"
          background
          @size-change="handleSizeChange"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑排片' : '新增排片'"
      width="600px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="选择电影" prop="movieId">
          <el-select
            v-model="form.movieId"
            placeholder="请选择电影"
            filterable
            style="width: 100%"
          >
            <el-option
              v-for="m in movieOptions"
              :key="m.id"
              :label="m.name"
              :value="m.id"
            />
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="选择影院" prop="cinemaId">
              <el-select
                v-model="form.cinemaId"
                placeholder="请选择影院"
                filterable
                style="width: 100%"
                @change="onCinemaChange"
              >
                <el-option
                  v-for="c in cinemaOptions"
                  :key="c.id"
                  :label="c.name"
                  :value="c.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="选择影厅" prop="hallId">
              <el-select
                v-model="form.hallId"
                placeholder="请先选影院"
                :disabled="!form.cinemaId"
                style="width: 100%"
              >
                <el-option
                  v-for="h in dialogHallOptions"
                  :key="h.id"
                  :label="h.name"
                  :value="h.id"
                />
              </el-select>
            </el-form-item>
          </el-col>
        </el-row>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="开场时间" prop="startTime">
              <el-date-picker
                v-model="form.startTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm"
                placeholder="选择开场时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="散场时间" prop="endTime">
              <el-date-picker
                v-model="form.endTime"
                type="datetime"
                value-format="YYYY-MM-DD HH:mm"
                placeholder="选择散场时间"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="票价" prop="price">
          <el-input-number
            v-model="form.price"
            :min="0.01"
            :max="9999"
            :step="1"
            :precision="2"
            controls-position="right"
            style="width: 200px"
          />
          <span style="margin-left: 8px; color: #6b7280; font-size: 13px">元 / 张</span>
        </el-form-item>

        <el-alert
          v-if="isEdit"
          type="warning"
          :closable="false"
          title="注意：修改场次可能影响已售出的票"
          style="margin-bottom: 10px"
        />
      </el-form>

      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">
          确定
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createSchedule,
  deleteSchedule,
  getScheduleDetail,
  getSchedulePage,
  updateSchedule,
  updateScheduleStatus,
} from '@/api/schedule.js';
import { getMoviePage } from '@/api/movie.js';
import { getCinemaPage } from '@/api/cinema.js';
import { getHallPage } from '@/api/hall.js';

const loading = ref(false);
const tableData = ref([]);

// 下拉选项
const movieOptions = ref([]);
const cinemaOptions = ref([]);
const hallOptions = ref([]);
const dialogHallOptions = ref([]);

const queryForm = reactive({
  movieId: null,
  cinemaId: null,
  hallId: null,
  date: '',
  status: null,
});

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
});

// 弹窗
const dialogVisible = ref(false);
const formRef = ref(null);
const submitting = ref(false);
const isEdit = ref(false);
const editId = ref(null);

const form = reactive({
  movieId: null,
  cinemaId: null,
  hallId: null,
  startTime: '',
  endTime: '',
  price: 39.9,
});

const rules = {
  movieId: [{ required: true, message: '请选择电影', trigger: 'change' }],
  cinemaId: [{ required: true, message: '请选择影院', trigger: 'change' }],
  hallId: [{ required: true, message: '请选择影厅', trigger: 'change' }],
  startTime: [{ required: true, message: '请选择开场时间', trigger: 'change' }],
  endTime: [{ required: true, message: '请选择散场时间', trigger: 'change' }],
  price: [{ required: true, message: '请输入票价', trigger: 'blur' }],
};

// 座位进度颜色
function seatPercent(row) {
  if (!row.totalSeat) return 0;
  return Math.round((row.availableSeat / row.totalSeat) * 100);
}

function seatColor(row) {
  const pct = seatPercent(row);
  if (pct > 50) return '#67c23a';
  if (pct > 20) return '#e6a23c';
  return '#f56c6c';
}

// ===== 加载下拉选项 =====
async function loadMovieOptions() {
  try {
    const page = await getMoviePage({ pageNo: 1, pageSize: 100, status: 1 });
    movieOptions.value = (page.records || []).map((m) => ({
      id: m.id,
      name: m.name,
      poster: m.posterUrl,
    }));
  } catch (err) {
    // 拦截器已提示
  }
}

async function loadCinemaOptions() {
  try {
    const page = await getCinemaPage({ pageNo: 1, pageSize: 100, status: 1 });
    cinemaOptions.value = (page.records || []).map((c) => ({
      id: c.id,
      name: c.name,
    }));
  } catch (err) {
    // 拦截器已提示
  }
}

async function loadHallOptions(cinemaId) {
  if (!cinemaId) {
    hallOptions.value = [];
    return;
  }
  try {
    const page = await getHallPage({ pageNo: 1, pageSize: 100, cinemaId });
    hallOptions.value = (page.records || []).map((h) => ({
      id: h.id,
      name: h.name,
    }));
  } catch (err) {
    // 拦截器已提示
  }
}

async function loadDialogHalls(cinemaId) {
  if (!cinemaId) {
    dialogHallOptions.value = [];
    return;
  }
  try {
    const page = await getHallPage({ pageNo: 1, pageSize: 100, cinemaId });
    dialogHallOptions.value = (page.records || []).map((h) => ({
      id: h.id,
      name: h.name,
    }));
  } catch (err) {
    // 拦截器已提示
  }
}

// 筛选区影院变化时加载影厅
watch(
  () => queryForm.cinemaId,
  (val) => {
    queryForm.hallId = null;
    loadHallOptions(val);
  },
);

function onCinemaChange() {
  form.hallId = null;
  loadDialogHalls(form.cinemaId);
}

// ===== 列表方法 =====
async function loadData() {
  loading.value = true;
  try {
    const params = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    };
    if (queryForm.movieId) params.movieId = queryForm.movieId;
    if (queryForm.cinemaId) params.cinemaId = queryForm.cinemaId;
    if (queryForm.hallId) params.hallId = queryForm.hallId;
    if (queryForm.date) params.date = queryForm.date;
    if (queryForm.status !== null && queryForm.status !== undefined && queryForm.status !== '') {
      params.status = queryForm.status;
    }
    const page = await getSchedulePage(params);
    tableData.value = page.records || [];
    pagination.total = page.total || 0;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pagination.pageNo = 1;
  loadData();
}

function handleReset() {
  queryForm.movieId = null;
  queryForm.cinemaId = null;
  queryForm.hallId = null;
  queryForm.date = '';
  queryForm.status = null;
  pagination.pageNo = 1;
  loadData();
}

function handlePageChange(page) {
  pagination.pageNo = page;
  loadData();
}

function handleSizeChange(size) {
  pagination.pageSize = size;
  pagination.pageNo = 1;
  loadData();
}

// ===== 新增/编辑 =====
function handleCreate() {
  isEdit.value = false;
  editId.value = null;
  resetForm();
  dialogVisible.value = true;
}

async function handleEdit(row) {
  isEdit.value = true;
  editId.value = row.id;
  try {
    const detail = await getScheduleDetail(row.id);
    form.movieId = detail.movieId;
    form.cinemaId = detail.cinemaId;
    form.hallId = detail.hallId;
    form.startTime = detail.startTime;
    form.endTime = detail.endTime;
    form.price = Number(detail.price);
    // 加载该影院的影厅列表
    loadDialogHalls(detail.cinemaId);
    dialogVisible.value = true;
  } catch (err) {
    // 拦截器已提示
  }
}

function resetForm() {
  form.movieId = null;
  form.cinemaId = null;
  form.hallId = null;
  form.startTime = '';
  form.endTime = '';
  form.price = 39.9;
  dialogHallOptions.value = [];
  formRef.value?.clearValidate();
}

function handleDialogClosed() {
  resetForm();
}

async function handleSubmit() {
  if (!formRef.value) return;
  try {
    await formRef.value.validate();
  } catch {
    return;
  }

  // 简单校验时间
  if (form.startTime >= form.endTime) {
    ElMessage.error('散场时间必须晚于开场时间');
    return;
  }

  submitting.value = true;
  try {
    const payload = {
      movieId: form.movieId,
      cinemaId: form.cinemaId,
      hallId: form.hallId,
      startTime: form.startTime,
      endTime: form.endTime,
      price: form.price,
    };

    if (isEdit.value) {
      await updateSchedule(editId.value, payload);
      ElMessage.success('修改成功');
    } else {
      await createSchedule(payload);
      ElMessage.success('新增成功');
    }

    dialogVisible.value = false;
    loadData();
  } finally {
    submitting.value = false;
  }
}

// ===== 启停用 =====
async function handleToggleStatus(row) {
  const action = row.status === 1 ? '停售' : '开售';
  try {
    await ElMessageBox.confirm(`确认${action}该场次吗？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await updateScheduleStatus(row.id, row.status === 1 ? 0 : 1);
    ElMessage.success(`${action}成功`);
    loadData();
  } catch (err) {
    // 拦截器已提示
  }
}

// ===== 删除 =====
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除该场次吗？有售票或锁定座位的场次无法删除。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonClass: 'el-button--danger',
      },
    );
  } catch {
    return;
  }
  try {
    await deleteSchedule(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (err) {
    // 拦截器已提示
  }
}

onMounted(() => {
  loadMovieOptions();
  loadCinemaOptions();
  loadData();
});
</script>

<style scoped>
.schedule-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.filter-card {
  padding: 16px 20px 4px;
}

.filter-card :deep(.el-form-item) {
  margin-bottom: 16px;
}

.table-card {
  padding: 16px 20px;
}

.table-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.movie-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.movie-poster {
  width: 36px;
  height: 50px;
  object-fit: cover;
  border-radius: 4px;
}

.movie-name {
  font-weight: 500;
  color: #1f2937;
}

.time-strong {
  font-weight: 600;
  color: #1f2937;
}

.price {
  color: #e6a23c;
  font-weight: 600;
}

.seat-count {
  margin-left: 6px;
  font-size: 12px;
  color: #6b7280;
  vertical-align: middle;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
