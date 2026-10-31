<template>
  <div class="review-list">
    <!-- 筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="电影ID">
          <el-input
            v-model="queryForm.movieId"
            placeholder="按电影ID筛选"
            clearable
            style="width: 160px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="审核状态">
          <el-select
            v-model="queryForm.status"
            placeholder="全部状态"
            clearable
            style="width: 140px"
          >
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="已拒绝" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item label="评分">
          <el-select
            v-model="queryForm.rating"
            placeholder="全部评分"
            clearable
            style="width: 140px"
          >
            <el-option v-for="n in 5" :key="n" :label="`${n} 星`" :value="n" />
          </el-select>
        </el-form-item>

        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.keyword"
            placeholder="评价内容"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
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
        <span class="table-title">评价列表</span>
        <span class="table-tip">先发后审：用户发布即展示，此处可拒绝或删除违规内容</span>
      </div>

      <el-table v-loading="loading" :data="tableData" style="width: 100%" stripe>
        <el-table-column prop="id" label="ID" width="180" align="center" show-overflow-tooltip />

        <el-table-column label="电影" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">
            <span class="movie-name">{{ row.movieName || `#${row.movieId}` }}</span>
          </template>
        </el-table-column>

        <el-table-column label="用户" width="150" show-overflow-tooltip>
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28" :src="row.avatar">{{ row.nickname?.charAt(0) }}</el-avatar>
              <span>{{ row.nickname }}</span>
            </div>
          </template>
        </el-table-column>

        <el-table-column label="评分" width="150" align="center">
          <template #default="{ row }">
            <span class="stars">
              <span
                v-for="n in 5"
                :key="n"
                class="star"
                :class="{ on: n <= row.rating }"
              >★</span>
            </span>
          </template>
        </el-table-column>

        <el-table-column prop="content" label="评价内容" min-width="240" show-overflow-tooltip>
          <template #default="{ row }">
            <span v-if="row.content">{{ row.content }}</span>
            <span v-else class="muted">（未填写文字评价）</span>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusInfo(row.status).type" effect="dark" size="small">
              {{ statusInfo(row.status).text }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="createdAt" label="评价时间" width="150" align="center" />

        <el-table-column label="操作" width="200" fixed="right" align="center">
          <template #default="{ row }">
            <el-button
              v-if="row.status !== 1"
              type="success"
              link
              size="small"
              @click="handleStatus(row, 1)"
            >通过</el-button>
            <el-button
              v-if="row.status !== 2"
              type="warning"
              link
              size="small"
              @click="handleStatus(row, 2)"
            >拒绝</el-button>
            <el-button type="danger" link size="small" @click="handleDelete(row)">删除</el-button>
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import { deleteReview, getReviewPage, updateReviewStatus } from '@/api/review.js';

const loading = ref(false);
const tableData = ref([]);

const queryForm = reactive({
  movieId: '',
  status: null,
  rating: null,
  keyword: '',
});

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
});

const STATUS_MAP = {
  0: { text: '待审核', type: 'info' },
  1: { text: '已通过', type: 'success' },
  2: { text: '已拒绝', type: 'danger' },
};

function statusInfo(status) {
  return STATUS_MAP[status] || { text: '未知', type: 'info' };
}

async function loadData() {
  loading.value = true;
  try {
    const params = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    };
    if (queryForm.movieId) {
      params.movieId = queryForm.movieId;
    }
    if (queryForm.status !== null && queryForm.status !== undefined && queryForm.status !== '') {
      params.status = queryForm.status;
    }
    if (queryForm.rating !== null && queryForm.rating !== undefined && queryForm.rating !== '') {
      params.rating = queryForm.rating;
    }
    if (queryForm.keyword?.trim()) {
      params.keyword = queryForm.keyword.trim();
    }
    const page = await getReviewPage(params);
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
  queryForm.movieId = '';
  queryForm.status = null;
  queryForm.rating = null;
  queryForm.keyword = '';
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

async function handleStatus(row, status) {
  const action = status === 1 ? '通过' : '拒绝';
  try {
    await ElMessageBox.confirm(
      `确认${action}该条评价吗？${status === 2 ? '拒绝后该评价将不再展示给用户。' : ''}`,
      '审核确认',
      { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' },
    );
  } catch {
    return;
  }

  try {
    await updateReviewStatus(row.id, status);
    ElMessage.success(`${action}成功`);
    loadData();
  } catch (err) {
    // 错误已在拦截器提示
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm('确认删除该条评价吗？删除后不可恢复。', '删除确认', {
      type: 'warning',
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      confirmButtonClass: 'el-button--danger',
    });
  } catch {
    return;
  }

  try {
    await deleteReview(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (err) {
    // 错误已在拦截器提示
  }
}

onMounted(loadData);
</script>

<style scoped>
.review-list {
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
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.table-title {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.table-tip {
  font-size: 12px;
  color: #9ca3af;
}

.movie-name {
  font-weight: 500;
  color: #1f2937;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.stars {
  font-size: 14px;
  letter-spacing: 1px;
}

.star {
  color: #dcdfe6;
}

.star.on {
  color: #f0a020;
}

.muted {
  color: #9ca3af;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
