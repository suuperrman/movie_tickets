<template>
  <div class="category-list">
    <el-card shadow="never" class="table-card">
      <div class="table-header">
        <span class="table-title">分类管理</span>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新增分类
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="name" label="分类名称" min-width="200">
          <template #default="{ row }">
            <span class="cat-name">{{ row.name }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="sort" label="排序" width="100" align="center" />
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small">
              {{ row.status === 1 ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="180" align="center" />
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
    </el-card>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑分类' : '新增分类'"
      width="420px"
      :close-on-click-modal="false"
      @closed="handleDialogClosed"
    >
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="80px"
      >
        <el-form-item label="分类名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="50" />
        </el-form-item>
        <el-form-item label="排序" prop="sort">
          <el-input-number
            v-model="form.sort"
            :min="0"
            :max="999"
            controls-position="right"
            style="width: 100%"
          />
          <div class="form-tip">数字越小越靠前</div>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">停用</el-radio>
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
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  createCategory,
  deleteCategory,
  getCategoryList,
  updateCategory,
} from '@/api/category.js';

const loading = ref(false);
const tableData = ref([]);

// 弹窗
const dialogVisible = ref(false);
const formRef = ref(null);
const submitting = ref(false);
const isEdit = ref(false);
const editId = ref(null);

const form = reactive({
  name: '',
  sort: 0,
  status: 1,
});

const rules = {
  name: [{ required: true, message: '请输入分类名称', trigger: 'blur' }],
  sort: [{ required: true, message: '请输入排序值', trigger: 'blur' }],
};

async function loadData() {
  loading.value = true;
  try {
    const list = await getCategoryList();
    tableData.value = list;
  } catch (err) {
    // 拦截器已提示
  } finally {
    loading.value = false;
  }
}

function handleCreate() {
  isEdit.value = false;
  editId.value = null;
  resetForm();
  dialogVisible.value = true;
}

function handleEdit(row) {
  isEdit.value = true;
  editId.value = row.id;
  form.name = row.name;
  form.sort = row.sort ?? 0;
  form.status = row.status ?? 1;
  dialogVisible.value = true;
}

function resetForm() {
  form.name = '';
  form.sort = 0;
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
      sort: form.sort,
      status: form.status,
    };

    if (isEdit.value) {
      await updateCategory(editId.value, payload);
      ElMessage.success('修改成功');
    } else {
      await createCategory(payload);
      ElMessage.success('新增成功');
    }

    dialogVisible.value = false;
    loadData();
  } finally {
    submitting.value = false;
  }
}

async function handleToggleStatus(row) {
  const action = row.status === 1 ? '停用' : '启用';
  try {
    await ElMessageBox.confirm(`确认${action}分类「${row.name}」吗？`, '提示', {
      type: 'warning',
    });
  } catch {
    return;
  }
  try {
    await updateCategory(row.id, {
      name: row.name,
      sort: row.sort,
      status: row.status === 1 ? 0 : 1,
    });
    ElMessage.success(`${action}成功`);
    loadData();
  } catch (err) {
    // 拦截器已提示
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除分类「${row.name}」吗？有关联电影的分类无法删除。`,
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
    await deleteCategory(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (err) {
    // 拦截器已提示
  }
}

onMounted(loadData);
</script>

<style scoped>
.category-list {
  max-width: 800px;
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

.cat-name {
  font-weight: 500;
  color: #1f2937;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
