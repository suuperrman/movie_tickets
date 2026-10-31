<template>
  <div class="actor-list">
    <!-- 搜索筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.keyword"
            placeholder="演员姓名"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
        </el-form-item>

        <el-form-item label="状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option label="启用" :value="1" />
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
        <span class="table-title">演员列表</span>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新增演员
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />

        <el-table-column label="头像" width="80" align="center">
          <template #default="{ row }">
            <el-avatar :size="48" shape="square" :src="row.avatarUrl">
              <User />
            </el-avatar>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="姓名" width="140">
          <template #default="{ row }">
            <span class="actor-name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column label="性别" width="100" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.gender === 1" type="primary" effect="plain" size="small">
              男
            </el-tag>
            <el-tag v-else-if="row.gender === 2" type="danger" effect="plain" size="small">
              女
            </el-tag>
            <el-tag v-else type="info" effect="plain" size="small">未知</el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="intro" label="简介" min-width="200" show-overflow-tooltip />

        <el-table-column label="参演电影" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.movieCount > 0 ? 'success' : 'info'" size="small">
              {{ row.movieCount || 0 }} 部
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="创建时间" width="170" align="center" />

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
              {{ row.status === 1 ? '停用' : '启用' }}
            </el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">
              删除
            </el-button>
          </template>
        </el-table-column>
      </el-table>

      <!-- 分页 -->
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
      :title="isEdit ? '编辑演员' : '新增演员'"
      width="560px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
        class="actor-form"
      >
        <el-row :gutter="20">
          <el-col :span="8">
            <el-form-item label="头像" prop="avatarUrl">
              <el-upload
                class="avatar-uploader"
                :show-file-list="false"
                :before-upload="beforeAvatarUpload"
                :http-request="handleAvatarUpload"
                accept="image/*"
              >
                <div v-if="form.avatarUrl" class="avatar-preview">
                  <img :src="form.avatarUrl" alt="头像" />
                </div>
                <div v-else class="avatar-placeholder">
                  <el-icon :size="32"><Plus /></el-icon>
                  <span>上传头像</span>
                </div>
              </el-upload>
            </el-form-item>
          </el-col>
          <el-col :span="16">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="请输入演员姓名" maxlength="100" />
            </el-form-item>

            <el-form-item label="性别" prop="gender">
              <el-radio-group v-model="form.gender">
                <el-radio :value="1">男</el-radio>
                <el-radio :value="2">女</el-radio>
                <el-radio :value="0">未知</el-radio>
              </el-radio-group>
            </el-form-item>

            <el-form-item label="状态" prop="status">
              <el-radio-group v-model="form.status">
                <el-radio :value="1">启用</el-radio>
                <el-radio :value="0">停用</el-radio>
              </el-radio-group>
            </el-form-item>
          </el-col>
        </el-row>

        <el-form-item label="简介" prop="intro">
          <el-input
            v-model="form.intro"
            type="textarea"
            :rows="4"
            placeholder="请输入演员简介"
            maxlength="1000"
            show-word-limit
          />
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
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createActor,
  deleteActor,
  getActorDetail,
  getActorPage,
  updateActor,
  updateActorStatus,
} from '@/api/actor.js';
import { uploadImage } from '@/utils/upload.js';

const loading = ref(false);
const tableData = ref([]);

const queryForm = reactive({
  keyword: '',
  status: null,
});

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
});

// ===== 弹窗相关 =====
const dialogVisible = ref(false);
const formRef = ref(null);
const submitting = ref(false);
const isEdit = ref(false);
const editId = ref(null);

const form = reactive({
  name: '',
  avatarUrl: '',
  gender: 0,
  intro: '',
  status: 1,
});

const rules = {
  name: [{ required: true, message: '请输入演员姓名', trigger: 'blur' }],
  avatarUrl: [{ required: true, message: '请上传头像', trigger: 'change' }],
};

// ===== 列表方法 =====
async function loadData() {
  loading.value = true;
  try {
    const params = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    };
    if (queryForm.keyword?.trim()) {
      params.keyword = queryForm.keyword.trim();
    }
    if (queryForm.status !== null && queryForm.status !== undefined && queryForm.status !== '') {
      params.status = queryForm.status;
    }
    const page = await getActorPage(params);
    tableData.value = page.records || [];
    pagination.total = page.total || 0;
  } catch (err) {
    // 错误已在拦截器提示
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
    const detail = await getActorDetail(row.id);
    form.name = detail.name;
    form.avatarUrl = detail.avatarUrl;
    form.gender = detail.gender ?? 0;
    form.intro = detail.intro || '';
    form.status = detail.status ?? 1;
    dialogVisible.value = true;
  } catch (err) {
    // 错误已在拦截器提示
  }
}

function resetForm() {
  form.name = '';
  form.avatarUrl = '';
  form.gender = 0;
  form.intro = '';
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
    const payload = {
      name: form.name,
      avatarUrl: form.avatarUrl,
      gender: form.gender,
      intro: form.intro,
      status: form.status,
    };

    if (isEdit.value) {
      await updateActor(editId.value, payload);
      ElMessage.success('修改成功');
    } else {
      await createActor(payload);
      ElMessage.success('新增成功');
    }

    dialogVisible.value = false;
    loadData();
  } catch (err) {
    // 错误已在拦截器提示
  } finally {
    submitting.value = false;
  }
}

// ===== 启停用 =====
async function handleToggleStatus(row) {
  const action = row.status === 1 ? '停用' : '启用';
  try {
    await ElMessageBox.confirm(`确认${action}演员「${row.name}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  try {
    const newStatus = row.status === 1 ? 0 : 1;
    await updateActorStatus(row.id, newStatus);
    ElMessage.success(`${action}成功`);
    loadData();
  } catch (err) {
    // 错误已在拦截器提示
  }
}

// ===== 删除 =====
async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除演员「${row.name}」吗？有参演电影的演员无法删除。`,
      '删除确认',
      {
        type: 'warning',
        confirmButtonText: '确定删除',
        cancelButtonText: '取消',
        confirmButtonClass: 'el-button--danger',
      },
    );
  } catch {
    return;
  }

  try {
    await deleteActor(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (err) {
    // 错误已在拦截器提示
  }
}

// ===== 头像上传 =====
function beforeAvatarUpload(file) {
  const isImage = file.type.startsWith('image/');
  if (!isImage) {
    ElMessage.error('只能上传图片文件');
    return false;
  }
  const isLt3M = file.size / 1024 / 1024 < 3;
  if (!isLt3M) {
    ElMessage.error('头像大小不能超过 3MB');
    return false;
  }
  return true;
}

async function handleAvatarUpload({ file }) {
  try {
    const result = await uploadImage(file, {
      dir: 'movie-ticket/actor/',
      maxSize: 3 * 1024 * 1024,
    });
    form.avatarUrl = result.url;
    ElMessage.success('上传成功');
  } catch (err) {
    ElMessage.error(err.message || '上传失败');
  }
}

onMounted(loadData);
</script>

<style scoped>
.actor-list {
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

.actor-name {
  font-weight: 500;
  color: #1f2937;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

/* 弹窗表单 */
.actor-form {
  padding: 10px 10px 0;
}

.avatar-uploader {
  width: 100px;
  height: 100px;
}

.avatar-preview {
  width: 100px;
  height: 100px;
  border-radius: 50%;
  overflow: hidden;
  border: 1px solid #dcdfe6;
  cursor: pointer;
}

.avatar-preview img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-placeholder {
  width: 100px;
  height: 100px;
  border: 2px dashed #dcdfe6;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 4px;
  color: #9ca3af;
  font-size: 12px;
  cursor: pointer;
  transition: all 0.2s;
}

.avatar-placeholder:hover {
  border-color: var(--el-color-primary);
  color: var(--el-color-primary);
}
</style>
