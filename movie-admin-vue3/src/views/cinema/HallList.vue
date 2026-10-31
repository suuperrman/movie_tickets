<template>
  <div class="hall-list">
    <!-- 顶部返回 + 影院信息 -->
    <el-card shadow="never" class="header-card">
      <div class="page-header">
        <div class="header-left">
          <el-button @click="goBack">
            <el-icon><ArrowLeft /></el-icon>返回影院列表
          </el-button>
          <div class="cinema-info">
            <h3 class="cinema-name">{{ cinemaName }}</h3>
            <span class="cinema-city">{{ cinemaCity }}</span>
          </div>
        </div>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新增影厅
        </el-button>
      </div>
    </el-card>

    <!-- 影厅列表 -->
    <el-card shadow="never" class="table-card">
      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="影厅名称" width="180">
          <template #default="{ row }">
            <span class="hall-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="hallTypeTag(row.type)" size="small">
              {{ row.typeDesc || '未知' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="座位规格" width="140" align="center">
          <template #default="{ row }">
            {{ row.rowCount || 0 }} 排 × {{ row.colCount || 0 }} 列
          </template>
        </el-table-column>
        <el-table-column label="总座位数" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.totalSeat > 0 ? 'success' : 'info'" size="small">
              {{ row.totalSeat || 0 }} 座
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" align="center" />
        <el-table-column label="操作" width="240" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleSeat(row)">
              座位管理
            </el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              编辑
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
          :page-sizes="[10, 20, 50]"
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
      :title="isEdit ? '编辑影厅' : '新增影厅'"
      width="520px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="100px"
      >
        <el-form-item label="影厅名称" prop="name">
          <el-input v-model="form.name" placeholder="如：1号厅" maxlength="50" />
        </el-form-item>

        <el-form-item label="影厅类型" prop="type">
          <el-select v-model="form.type" style="width: 100%">
            <el-option label="普通厅" :value="1" />
            <el-option label="IMAX厅" :value="2" />
            <el-option label="情侣厅" :value="3" />
            <el-option label="VIP厅" :value="4" />
            <el-option label="4DX厅" :value="5" />
          </el-select>
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="座位行数" prop="rowCount">
              <el-input-number
                v-model="form.rowCount"
                :min="1"
                :max="50"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="座位列数" prop="colCount">
              <el-input-number
                v-model="form.colCount"
                :min="1"
                :max="50"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </el-col>
        </el-row>

        <el-alert
          type="info"
          :closable="false"
          title="提示：保存后需要手动生成座位"
          description="影厅创建后，进入「座位管理」点击批量生成座位。"
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
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import { getCinemaDetail } from '@/api/cinema.js';
import {
  createHall,
  deleteHall,
  getHallDetail,
  getHallPage,
  updateHall,
} from '@/api/hall.js';

const route = useRoute();
const router = useRouter();

const cinemaId = computed(() => Number(route.params.cinemaId));

const loading = ref(false);
const tableData = ref([]);
const cinemaName = ref('');
const cinemaCity = ref('');

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
  cinemaId: null,
  name: '',
  type: 1,
  rowCount: 10,
  colCount: 15,
});

const rules = {
  name: [{ required: true, message: '请输入影厅名称', trigger: 'blur' }],
  type: [{ required: true, message: '请选择影厅类型', trigger: 'change' }],
  rowCount: [{ required: true, message: '请输入座位行数', trigger: 'blur' }],
  colCount: [{ required: true, message: '请输入座位列数', trigger: 'blur' }],
};

function hallTypeTag(type) {
  const map = { 1: '', 2: 'warning', 3: 'danger', 4: 'success', 5: 'info' };
  return map[type] || 'info';
}

async function loadCinemaInfo() {
  try {
    const detail = await getCinemaDetail(cinemaId.value);
    cinemaName.value = detail.name;
    cinemaCity.value = detail.city;
  } catch (err) {
    // 拦截器已提示
  }
}

async function loadData() {
  loading.value = true;
  try {
    const page = await getHallPage({
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
      cinemaId: cinemaId.value,
    });
    tableData.value = page.records || [];
    pagination.total = page.total || 0;
  } finally {
    loading.value = false;
  }
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

function goBack() {
  router.push('/cinema/list');
}

function handleSeat(row) {
  router.push(`/cinema/hall/${row.id}/seats`);
}

// ===== 新增/编辑 =====
function handleCreate() {
  isEdit.value = false;
  editId.value = null;
  resetForm();
  form.cinemaId = cinemaId.value;
  dialogVisible.value = true;
}

async function handleEdit(row) {
  isEdit.value = true;
  editId.value = row.id;
  try {
    const detail = await getHallDetail(row.id);
    form.cinemaId = detail.cinemaId;
    form.name = detail.name;
    form.type = detail.type;
    form.rowCount = detail.rowCount || 10;
    form.colCount = detail.colCount || 15;
    dialogVisible.value = true;
  } catch (err) {
    // 拦截器已提示
  }
}

function resetForm() {
  form.cinemaId = cinemaId.value;
  form.name = '';
  form.type = 1;
  form.rowCount = 10;
  form.colCount = 15;
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

  submitting.value = true;
  try {
    const payload = {
      cinemaId: form.cinemaId,
      name: form.name,
      type: form.type,
      rowCount: form.rowCount,
      colCount: form.colCount,
    };

    if (isEdit.value) {
      await updateHall(editId.value, payload);
      ElMessage.success('修改成功');
    } else {
      await createHall(payload);
      ElMessage.success('新增成功');
    }

    dialogVisible.value = false;
    loadData();
  } finally {
    submitting.value = false;
  }
}

// ===== 删除 =====
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除影厅「${row.name}」吗？有排片的影厅无法删除。`,
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
    await deleteHall(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (err) {
    // 拦截器已提示
  }
}

onMounted(() => {
  loadCinemaInfo();
  loadData();
});
</script>

<style scoped>
.hall-list {
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

.cinema-info {
  display: flex;
  align-items: baseline;
  gap: 10px;
}

.cinema-name {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  color: #1f2937;
}

.cinema-city {
  font-size: 13px;
  color: #6b7280;
}

.table-card {
  padding: 16px 20px;
}

.hall-name {
  font-weight: 500;
  color: #1f2937;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
