<template>
  <div class="order-list">
    <!-- 搜索筛选区 -->
    <el-card shadow="never" class="filter-card">
      <el-form :inline="true" :model="queryForm" @submit.prevent>
        <el-form-item label="订单号">
          <el-input
            v-model="queryForm.orderNo"
            placeholder="订单号"
            clearable
            style="width: 200px"
            @keyup.enter="handleSearch"
          />
        </el-form-item>

        <el-form-item label="用户">
          <el-input
            v-model="queryForm.userId"
            placeholder="用户ID"
            clearable
            style="width: 100px"
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
            <el-option v-for="s in statusOptions" :key="s.value" :label="s.label" :value="s.value" />
          </el-select>
        </el-form-item>

        <el-form-item label="日期">
          <el-date-picker
            v-model="queryForm.date"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="下单日期"
            style="width: 150px"
          />
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
        <span class="table-title">订单列表</span>
        <div class="stats">
          <el-tag type="info" effect="plain">共 {{ pagination.total }} 条</el-tag>
        </div>
      </div>

      <el-table
        v-loading="loading"
        :data="tableData"
        style="width: 100%"
        stripe
      >
        <el-table-column prop="id" label="ID" width="80" align="center" />
        <el-table-column prop="orderNo" label="订单号" width="200">
          <template #default="{ row }">
            <span class="order-no">{{ row.orderNo }}</span>
          </template>
        </el-table-column>
        <el-table-column label="用户" width="140">
          <template #default="{ row }">
            <div class="user-cell">
              <el-avatar :size="28">{{ row.username?.charAt(0) }}</el-avatar>
              <span>{{ row.username }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="movieName" label="电影" min-width="160" />
        <el-table-column prop="cinemaName" label="影院" min-width="160" />
        <el-table-column label="票数" width="80" align="center">
          <template #default="{ row }">{{ row.seatCount }}张</template>
        </el-table-column>
        <el-table-column label="实付金额" width="120" align="center">
          <template #default="{ row }">
            <span class="price">¥{{ row.payAmount || '0.00' }}</span>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="statusTagType(row.status)" effect="dark" size="small">
              {{ statusLabel(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createdAt" label="下单时间" width="170" align="center" />
        <el-table-column label="操作" width="220" fixed="right" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="handleDetail(row)">
              详情
            </el-button>
            <!-- 已支付/已完成：管理员主动退款 -->
            <el-button
              v-if="row.status === 1 || row.status === 5"
              type="danger"
              link
              size="small"
              @click="handleRefund(row)"
            >
              退款
            </el-button>
            <!-- 退款中：审核用户的退款申请 -->
            <template v-if="row.status === 3">
              <el-button
                type="success"
                link
                size="small"
                @click="handleAudit(row, true)"
              >
                同意退款
              </el-button>
              <el-button
                type="warning"
                link
                size="small"
                @click="handleAudit(row, false)"
              >
                拒绝退款
              </el-button>
            </template>
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

    <!-- 订单详情抽屉 -->
    <el-drawer
      v-model="detailVisible"
      title="订单详情"
      size="520px"
      :before-close="closeDetail"
    >
      <div v-loading="detailLoading" class="order-detail">
        <template v-if="detail">
          <!-- 订单状态 -->
          <div class="detail-status" :class="'status-' + detail.status">
            <el-icon :size="28"><CircleCheck /></el-icon>
            <span class="status-text">{{ statusLabel(detail.status) }}</span>
          </div>

          <!-- 电影信息 -->
          <div class="detail-section">
            <div class="section-title">影片信息</div>
            <div class="movie-card">
              <img v-if="detail.moviePoster" :src="detail.moviePoster" class="movie-poster" />
              <div class="movie-info">
                <div class="movie-name">{{ detail.movieName }}</div>
                <div class="movie-meta">{{ detail.cinemaName }} · {{ detail.hallName }}</div>
                <div class="movie-meta">{{ detail.startTime }} 开场</div>
              </div>
            </div>
          </div>

          <!-- 座位信息 -->
          <div class="detail-section">
            <div class="section-title">座位信息</div>
            <div class="seat-list">
              <el-tag
                v-for="(item, idx) in detail.items"
                :key="idx"
                type="primary"
                effect="plain"
                size="default"
              >
                {{ item.seatCode }}
              </el-tag>
            </div>
          </div>

          <!-- 金额明细 -->
          <div class="detail-section">
            <div class="section-title">金额明细</div>
            <div class="amount-list">
              <div class="amount-row">
                <span>单价</span>
                <span>¥{{ detail.price }}</span>
              </div>
              <div class="amount-row">
                <span>数量</span>
                <span>{{ detail.seatCount }} 张</span>
              </div>
              <div class="amount-row">
                <span>电影总价</span>
                <span>¥{{ detail.totalAmount }}</span>
              </div>
              <div v-if="detail.discount && Number(detail.discount) > 0" class="amount-row discount">
                <span>优惠</span>
                <span>-¥{{ detail.discount }}</span>
              </div>
              <div class="amount-row total">
                <span>实付金额</span>
                <span class="pay-amount">¥{{ detail.payAmount }}</span>
              </div>
            </div>
          </div>

          <!-- 订单信息 -->
          <div class="detail-section">
            <div class="section-title">订单信息</div>
            <div class="info-grid">
              <div class="info-item">
                <span class="label">订单号</span>
                <span class="value">{{ detail.orderNo }}</span>
              </div>
              <div class="info-item">
                <span class="label">下单人</span>
                <span class="value">{{ detail.username }}</span>
              </div>
              <div class="info-item">
                <span class="label">下单时间</span>
                <span class="value">{{ detail.createdAt }}</span>
              </div>
              <div class="info-item">
                <span class="label">支付时间</span>
                <span class="value">{{ detail.payTime || '-' }}</span>
              </div>
              <div class="info-item">
                <span class="label">取消时间</span>
                <span class="value">{{ detail.cancelTime || '-' }}</span>
              </div>
              <template v-if="detail.status === 3 || detail.status === 4 || detail.refundApplyAt">
                <div class="info-item full">
                  <span class="label">退款原因</span>
                  <span class="value">{{ detail.refundReason || '-' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">申请时间</span>
                  <span class="value">{{ detail.refundApplyAt || '-' }}</span>
                </div>
                <div class="info-item">
                  <span class="label">审核时间</span>
                  <span class="value">{{ detail.refundAuditAt || '-' }}</span>
                </div>
              </template>
            </div>
          </div>

          <!-- 底部操作 -->
          <div class="detail-footer">
            <el-button @click="detailVisible = false">关闭</el-button>
            <template v-if="detail.status === 3">
              <el-button type="warning" @click="handleAudit(detail, false)">拒绝退款</el-button>
              <el-button type="success" @click="handleAudit(detail, true)">同意退款</el-button>
            </template>
            <el-button
              v-else-if="detail.status === 1 || detail.status === 5"
              type="danger"
              @click="handleRefund(detail)"
            >
              申请退款
            </el-button>
          </div>
        </template>
      </div>
    </el-drawer>

    <!-- 退款确认弹窗 -->
    <el-dialog
      v-model="refundDialogVisible"
      title="退款确认"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="订单号">
          <span>{{ refundOrder?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="退款金额">
          <span class="refund-amount">¥{{ refundOrder?.payAmount }}</span>
        </el-form-item>
        <el-form-item label="退款原因">
          <el-input
            v-model="refundReason"
            type="textarea"
            :rows="3"
            placeholder="请输入退款原因（可选）"
            maxlength="200"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="refundDialogVisible = false">取消</el-button>
        <el-button type="danger" :loading="refunding" @click="confirmRefund">
          确认退款
        </el-button>
      </template>
    </el-dialog>

    <!-- 退款审核弹窗 -->
    <el-dialog
      v-model="auditDialogVisible"
      :title="auditApprove ? '同意退款' : '拒绝退款'"
      width="440px"
      :close-on-click-modal="false"
    >
      <el-form label-width="80px">
        <el-form-item label="订单号">
          <span>{{ auditOrder?.orderNo }}</span>
        </el-form-item>
        <el-form-item label="退款金额">
          <span class="refund-amount">¥{{ auditOrder?.payAmount }}</span>
        </el-form-item>
        <el-form-item label="审核备注">
          <el-input
            v-model="auditReason"
            type="textarea"
            :rows="3"
            :placeholder="auditApprove ? '请输入退款备注（可选）' : '请输入拒绝原因，将展示给用户（建议填写）'"
            maxlength="200"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="auditDialogVisible = false">取消</el-button>
        <el-button
          :type="auditApprove ? 'success' : 'warning'"
          :loading="auditing"
          @click="confirmAudit"
        >
          {{ auditApprove ? '确认同意' : '确认拒绝' }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';

import { getOrderDetail, getOrderPage, refundOrder as refundOrderApi, auditRefund as auditRefundApi } from '@/api/order.js';

const statusOptions = [
  { label: '待支付', value: 0 },
  { label: '已支付', value: 1 },
  { label: '已取消', value: 2 },
  { label: '退款中', value: 3 },
  { label: '已退款', value: 4 },
  { label: '已完成', value: 5 },
];

function statusLabel(status) {
  const map = {
    0: '待支付', 1: '已支付', 2: '已取消',
    3: '退款中', 4: '已退款', 5: '已完成',
  };
  return map[status] || '未知';
}

function statusTagType(status) {
  const map = {
    0: 'warning', 1: 'success', 2: 'info',
    3: '', 4: 'info', 5: 'success',
  };
  return map[status] || 'info';
}

const loading = ref(false);
const tableData = ref([]);

const queryForm = reactive({
  orderNo: '',
  userId: '',
  status: null,
  date: '',
});

const pagination = reactive({
  pageNo: 1,
  pageSize: 10,
  total: 0,
});

// 详情抽屉
const detailVisible = ref(false);
const detailLoading = ref(false);
const detail = ref(null);

// 退款弹窗
const refundDialogVisible = ref(false);
const refundOrder = ref(null);
const refundReason = ref('');
const refunding = ref(false);

// 退款审核弹窗
const auditDialogVisible = ref(false);
const auditOrder = ref(null);
const auditApprove = ref(true);
const auditReason = ref('');
const auditing = ref(false);

async function loadData() {
  loading.value = true;
  try {
    const params = {
      pageNo: pagination.pageNo,
      pageSize: pagination.pageSize,
    };
    if (queryForm.orderNo?.trim()) params.orderNo = queryForm.orderNo.trim();
    if (queryForm.userId?.trim()) params.userId = queryForm.userId.trim();
    if (queryForm.status !== null && queryForm.status !== undefined && queryForm.status !== '') {
      params.status = queryForm.status;
    }
    if (queryForm.date) params.date = queryForm.date;
    const page = await getOrderPage(params);
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
  queryForm.orderNo = '';
  queryForm.userId = '';
  queryForm.status = null;
  queryForm.date = '';
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

// ===== 详情 =====
async function handleDetail(row) {
  detailVisible.value = true;
  detailLoading.value = true;
  try {
    detail.value = await getOrderDetail(row.id);
  } finally {
    detailLoading.value = false;
  }
}

function closeDetail(done) {
  detail.value = null;
  done();
}

// ===== 退款 =====
function handleRefund(row) {
  refundOrder.value = row;
  refundReason.value = '';
  refundDialogVisible.value = true;
}

async function confirmRefund() {
  refunding.value = true;
  try {
    await refundOrderApi(refundOrder.value.id, refundReason.value);
    ElMessage.success('退款成功');
    refundDialogVisible.value = false;
    if (detailVisible.value && detail.value?.id === refundOrder.value.id) {
      // 刷新详情
      detail.value = await getOrderDetail(refundOrder.value.id);
    }
    loadData();
  } catch (err) {
    // 拦截器已提示
  } finally {
    refunding.value = false;
  }
}

// ===== 退款审核 =====
function handleAudit(row, approve) {
  auditOrder.value = row;
  auditApprove.value = approve;
  auditReason.value = '';
  auditDialogVisible.value = true;
}

async function confirmAudit() {
  auditing.value = true;
  try {
    await auditRefundApi(auditOrder.value.id, auditApprove.value, auditReason.value);
    ElMessage.success(auditApprove.value ? '已同意退款' : '已拒绝退款');
    auditDialogVisible.value = false;
    if (detailVisible.value && detail.value?.id === auditOrder.value.id) {
      // 刷新详情
      detail.value = await getOrderDetail(auditOrder.value.id);
    }
    loadData();
  } catch (err) {
    // 拦截器已提示
  } finally {
    auditing.value = false;
  }
}

onMounted(loadData);
</script>

<style scoped>
.order-list {
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

.order-no {
  font-family: 'Courier New', monospace;
  font-size: 13px;
  color: #374151;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 8px;
}

.price {
  color: #e6a23c;
  font-weight: 600;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

/* 订单详情 */
.order-detail {
  display: flex;
  flex-direction: column;
  gap: 20px;
  padding: 10px 4px;
}

.detail-status {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px;
  border-radius: 12px;
}

.detail-status.status-0 { background: #fef3c7; color: #92400e; }
.detail-status.status-1 { background: #dcfce7; color: #166534; }
.detail-status.status-2 { background: #f3f4f6; color: #4b5563; }
.detail-status.status-3 { background: #dbeafe; color: #1e40af; }
.detail-status.status-4 { background: #f3f4f6; color: #4b5563; }
.detail-status.status-5 { background: #dcfce7; color: #166534; }

.status-text {
  font-size: 20px;
  font-weight: 600;
}

.detail-section {
  background: #fff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  padding: 16px;
}

.section-title {
  font-size: 14px;
  font-weight: 600;
  color: #374151;
  margin-bottom: 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #f3f4f6;
}

.movie-card {
  display: flex;
  gap: 14px;
}

.movie-poster {
  width: 70px;
  height: 96px;
  object-fit: cover;
  border-radius: 6px;
}

.movie-info {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 6px;
}

.movie-name {
  font-size: 16px;
  font-weight: 600;
  color: #1f2937;
}

.movie-meta {
  font-size: 13px;
  color: #6b7280;
}

.seat-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.amount-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.amount-row {
  display: flex;
  justify-content: space-between;
  font-size: 14px;
  color: #6b7280;
}

.amount-row.total {
  padding-top: 10px;
  border-top: 1px dashed #e5e7eb;
  color: #1f2937;
  font-weight: 500;
}

.amount-row.discount {
  color: #ef4444;
}

.pay-amount {
  color: #e6a23c;
  font-size: 18px;
  font-weight: 700;
}

.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.info-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.info-item.full {
  grid-column: 1 / -1;
}

.info-item .label {
  font-size: 12px;
  color: #9ca3af;
}

.info-item .value {
  font-size: 14px;
  color: #374151;
}

.detail-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  padding-top: 10px;
  border-top: 1px solid #f3f4f6;
}

.refund-amount {
  color: #ef4444;
  font-weight: 600;
  font-size: 16px;
}
</style>
