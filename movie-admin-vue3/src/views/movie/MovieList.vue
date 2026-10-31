<template>
  <div class="movie-list">
    <!-- 搜索筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="关键词">
          <el-input
            v-model="queryForm.keyword"
            placeholder="电影名称/导演"
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
            <el-option label="上架" :value="1" />
            <el-option label="下架" :value="0" />
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
        <span class="table-title">电影列表</span>
        <el-button type="primary" @click="handleCreate">
          <el-icon><Plus /></el-icon>新增电影
        </el-button>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />

        <el-table-column label="海报" width="90" align="center">
          <template #default="{ row }">
            <div class="poster-cell">
              <img v-if="row.posterUrl" :src="row.posterUrl" :alt="row.name" />
              <div v-else class="poster-placeholder">🎬</div>
            </div>
          </template>
        </el-table-column>

        <el-table-column prop="name" label="电影名称" min-width="160">
          <template #default="{ row }">
            <span class="movie-name" :title="row.name">{{ row.name }}</span>
          </template>
        </el-table-column>

        <el-table-column prop="director" label="导演" width="120" />

        <el-table-column label="分类" width="160">
          <template #default="{ row }">
            <el-tag
              v-for="(cat, idx) in row.categories?.split(',')?.slice(0, 2)"
              :key="idx"
              size="small"
              type="info"
              effect="plain"
              style="margin-right: 4px"
            >
              {{ cat }}
            </el-tag>
            <el-tag
              v-if="row.categories?.split(',').length > 2"
              size="small"
              type="info"
              effect="plain"
            >
              +{{ row.categories.split(',').length - 2 }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column prop="duration" label="时长" width="100" align="center">
          <template #default="{ row }">{{ row.duration }} 分钟</template>
        </el-table-column>

        <el-table-column prop="releaseDate" label="上映日期" width="120" align="center" />

        <el-table-column label="排片数" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.scheduleCount > 0 ? 'success' : 'info'" size="small">
              {{ row.scheduleCount || 0 }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'info'" effect="dark" size="small">
              {{ row.status === 1 ? '上架' : '下架' }}
            </el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="240" fixed="right" align="center">
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
              {{ row.status === 1 ? '下架' : '上架' }}
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
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';

import {
  deleteMovie,
  getMoviePage,
  updateMovieStatus,
} from '@/api/movie.js';

const router = useRouter();

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
    const page = await getMoviePage(params);
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

function handleCreate() {
  router.push('/movie/create');
}

function handleEdit(row) {
  router.push(`/movie/edit/${row.id}`);
}

async function handleToggleStatus(row) {
  const action = row.status === 1 ? '下架' : '上架';
  try {
    await ElMessageBox.confirm(`确认${action}电影「${row.name}」吗？`, '提示', {
      type: 'warning',
      confirmButtonText: '确定',
      cancelButtonText: '取消',
    });
  } catch {
    return;
  }

  try {
    const newStatus = row.status === 1 ? 0 : 1;
    await updateMovieStatus(row.id, newStatus);
    ElMessage.success(`${action}成功`);
    loadData();
  } catch (err) {
    // 错误已在拦截器提示
  }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(
      `确认删除电影「${row.name}」吗？有进行中场次的电影无法删除。`,
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
    await deleteMovie(row.id);
    ElMessage.success('删除成功');
    loadData();
  } catch (err) {
    // 错误已在拦截器提示
  }
}

onMounted(loadData);
</script>

<style scoped>
.movie-list {
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

.poster-cell {
  width: 60px;
  height: 80px;
  border-radius: 4px;
  overflow: hidden;
  background: #f3f4f6;
}

.poster-cell img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-placeholder {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  font-size: 24px;
}

.movie-name {
  font-weight: 500;
  color: #1f2937;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
