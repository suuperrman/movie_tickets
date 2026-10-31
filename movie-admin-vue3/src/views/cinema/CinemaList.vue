<template>
  <div class="cinema-list">
    <!-- 搜索筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.keyword"
            placeholder="影院名称"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="城市">
          <el-input
            v-model="queryForm.city"
            placeholder="城市"
            clearable
            style="width: 140px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option label="正常" :value="1" />
            <el-option label="停用" :value="0" />
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
        <span class="table-title">影院列表</span>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新增影院
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="影院名称" min-width="180">
          <template #default="{ row }">
            <span class="cinema-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="city" label="城市" width="100" align="center" />
        <el-table-column prop="address" label="地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="phone" label="联系电话" width="140" align="center" />
        <el-table-column label="影厅数" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.hallCount > 0 ? 'success' : 'info'" size="small">
              {{ row.hallCount || 0 }} 个
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small">
              {{ row.status === 1 ? '正常' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" align="center" />
        <el-table-column label="操作" width="260" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleHall(row)">
              影厅管理
            </el-button>
            <el-button type="primary" link size="small" @click="handleEdit(row)">
              编辑
            </el-button>
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '停用' : '启用' }}
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
      :title="isEdit ? '编辑影院' : '新增影院'"
      width="560px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="90px"
      >
        <el-form-item label="影院名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入影院名称" maxlength="100" />
        </el-form-item>

        <el-row :gutter="16">
          <el-col :span="12">
            <el-form-item label="所在城市" prop="city">
              <el-input v-model="form.city" placeholder="如：北京" maxlength="50" />
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="联系电话" prop="phone">
              <el-input v-model="form.phone" placeholder="请输入联系电话" maxlength="20" />
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="详细地址" prop="address">
          <el-input
            v-model="form.address"
            type="textarea"
            :rows="2"
            placeholder="请输入详细地址"
            maxlength="255"
            show-word-limit
          />
        </el-form-item>

        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">正常营业</el-radio>
            <el-radio :value="0">已停用</el-radio>
          </el-radio-group>
        </el-form-item>
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
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createCinema,
  deleteCinema,
  getCinemaDetail,
  getCinemaPage,
  updateCinema,
  updateCinemaStatus,
} from '@/api/cinema.js';

const router = useRouter();

const loading = ref(false);
const tableData = ref([]);

const queryForm = reactive({
  keyword: '',
  city: '',
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
  name: '',
  city: '',
  address: '',
  phone: '',
  status: 1,
});

const rules = {
  name: [{ required: true, message: '请输入影院名称', trigger: 'blur' }],
  city: [{ required: true, message: '请输入城市', trigger: 'blur' }],
  address: [{ required: true, message: '请输入地址', trigger: 'blur' }],
  phone: [{ required: true, message: '请输入联系电话', trigger: 'blur' }],
};

async function loadData() {
  loading.value = true;
  try {
    const params = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    };
    if (queryForm.keyword?.trim()) params.keyword = queryForm.keyword.trim();
    if (queryForm.city?.trim()) params.city = queryForm.city.trim();
    if (queryForm.status !== null && queryForm.status !== undefined && queryForm.status !== '') {
      params.status = queryForm.status;
    }
    const page = await getCinemaPage(params);
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
  queryForm.keyword = '';
  queryForm.city = '';
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

function handleHall(row) {
  router.push(`/cinema/${row.id}/halls`);
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
    const detail = await getCinemaDetail(row.id);
    form.name = detail.name;
    form.city = detail.city;
    form.address = detail.address;
    form.phone = detail.phone;
    form.status = detail.status ?? 1;
    dialogVisible.value = true;
  } catch (err) {
    // 拦截器已提示
  }
}

function resetForm() {
  form.name = '';
  form.city = '';
  form.address = '';
  form.phone = '';
  form.status = 1;
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
    const payload = { ...form };
    if (isEdit.value) {
      await updateCinema(editId.value, payload);
      ElMessage.success('修改成功');
    } else {
      await createCinema(payload);
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
  const action = row.status === 1 ? '停用' : '启用';
  try {
    await ElMessageBox.confirm(`确认${action}影院「${row.name}」吗？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await updateCinemaStatus(row.id, row.status === 1 ? 0 : 1);
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
      `确认删除影院「${row.name}」吗？有影厅或排片的影院无法删除。`,
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
    await deleteCinema(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (err) {
    // 拦截器已提示
  }
}

onMounted(loadData);
</script>

<style scoped>
.cinema-list {
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

.cinema-name {
  font-weight: 500;
  color: #1f2937;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
