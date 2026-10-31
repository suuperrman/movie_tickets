<template>
  <div class="user-list">
    <!-- 搜索筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.keyword"
            placeholder="昵称/手机号"
            clearable
            style="width: 220px"
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
            <el-option label="正常" :value="1" />
            <el-option label="禁用" :value="0" />
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
        <span class="table-title">用户列表</span>
        <div class="stats">
          <el-tag type="info" effect="plain">共 {{ pagination.total }} 位用户</el-tag>
        </div>
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
            <el-avatar :size="36" :src="row.avatar">
              <User />
            </el-avatar>
          </template>
        </el-table-column>
        <el-table-column prop="nickname" label="昵称" width="140">
          <template #default="{ row }">
            <span class="username">{{ row.nickname }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" width="150" align="center">
          <template #default="{ row }">{{ maskPhone(row.phone) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'danger'" effect="dark" size="small">
              {{ row.status === 1 ? '正常' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastLoginAt" label="最后登录" width="170" align="center">
          <template #default="{ row }">{{ row.lastLoginAt || '-' }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="注册时间" width="170" align="center" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              :type="row.status === 1 ? 'warning' : 'success'"
              link
              size="small"
              @click="handleToggleStatus(row)"
            >
              {{ row.status === 1 ? '禁用' : '启用' }}
            </el-button>
            <el-button type="primary" link size="small" @click="handleResetPwd(row)">
              重置密码
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

    <!-- 重置密码弹窗 -->
    <el-dialog
      v-model="resetPwdVisible"
      title="重置密码"
      width="420px"
      :close-on-click-modal="false"
      @closed="resetPwdForm = null"
    >
      <el-form
        ref="resetPwdRef"
        :model="resetPwdForm"
        :rules="resetPwdRules"
        label-width="90px"
      >
        <el-form-item label="用户">
          <span>{{ resetPwdForm?.nickname }}</span>
        </el-form-item>
        <el-form-item label="新密码" prop="password">
          <el-input
            v-model="resetPwdForm.password"
            type="password"
            placeholder="请输入新密码"
            show-password
          />
          <div class="form-tip">留空则重置为默认密码 123456</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="confirmResetPwd">
          确认重置
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import { getUserPage, resetUserPassword, updateUserStatus } from '@/api/user.js';

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

// 重置密码弹窗
const resetPwdVisible = ref(false);
const resetPwdRef = ref(null);
const resetting = ref(false);
const resetPwdForm = reactive({
  userId: null,
  nickname: '',
  password: '',
});

const resetPwdRules = {
  password: [
    {
      validator: (_rule, value, callback) => {
        if (value && value.length < 6) {
          callback(new Error('密码至少 6 位'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

function maskPhone(phone) {
  if (!phone) return '-';
  if (phone.length < 7) return phone;
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2');
}

async function loadData() {
  loading.value = true;
  try {
    const params = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    };
    if (queryForm.keyword?.trim()) params.keyword = queryForm.keyword.trim();
    if (queryForm.status !== null && queryForm.status !== undefined && queryForm.status !== '') {
      params.status = queryForm.status;
    }
    const page = await getUserPage(params);
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

// ===== 启用/禁用 =====
async function handleToggleStatus(row) {
  const action = row.status === 1 ? '禁用' : '启用';
  try {
    await ElMessageBox.confirm(
      `确认${action}用户「${row.nickname}」吗？${action === '禁用' ? '禁用后该用户将无法登录。' : ''}`,
      '提示',
      {
        type: 'warning',
      },
    );
  } catch {
    return;
  }
  try {
    await updateUserStatus(row.id, row.status === 1 ? 0 : 1);
    ElMessage.success(`${action}成功`);
    loadData();
  } catch (err) {
    // 拦截器已提示
  }
}

// ===== 重置密码 =====
function handleResetPwd(row) {
  resetPwdForm.userId = row.id;
  resetPwdForm.nickname = row.nickname;
  resetPwdForm.password = '';
  resetPwdVisible.value = true;
}

async function confirmResetPwd() {
  if (resetPwdRef.value) {
    try {
      await resetPwdRef.value.validate();
    } catch {
      return;
    }
  }

  resetting.value = true;
  try {
    await resetUserPassword(resetPwdForm.userId, resetPwdForm.password || undefined);
    ElMessage.success(resetPwdForm.password ? '密码重置成功' : '密码已重置为默认密码 123456');
    resetPwdVisible.value = false;
  } catch (err) {
    // 拦截器已提示
  } finally {
    resetting.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.user-list {
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

.username {
  font-weight: 500;
  color: #1f2937;
}

.form-tip {
  margin-top: 6px;
  font-size: 12px;
  color: #9ca3af;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
