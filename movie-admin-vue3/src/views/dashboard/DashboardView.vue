<template>
  <div v-loading="loading" class="dashboard">
    <el-alert
      v-if="loadError"
      type="error"
      :closable="false"
      show-icon
      class="error-alert"
    >
      <template #title>数据加载失败：{{ loadError }}</template>
      <el-button size="small" type="danger" plain @click="loadDashboard">
        重新加载
      </el-button>
    </el-alert>

    <!-- 数据卡片 -->
    <el-row :gutter="20">
      <el-col :span="6" v-for="item in stats" :key="item.title">
        <el-card class="stat-card" shadow="hover">
          <div class="stat-content">
            <div class="stat-info">
              <div class="stat-value">{{ item.value }}</div>
              <div class="stat-label">{{ item.title }}</div>
            </div>
            <div class="stat-icon" :style="{ background: item.color }">
              <el-icon :size="28">
                <component :is="item.icon" />
              </el-icon>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 图表区 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="16">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">销售趋势（近 7 天）</span>
          </template>
          <div ref="chartRef" class="chart-container"></div>
        </el-card>
      </el-col>
      <el-col :span="8">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">热门电影 TOP 5</span>
          </template>
          <el-table :data="topMovies" style="width: 100%" empty-text="暂无已支付订单数据">
            <el-table-column prop="rank" label="排名" width="60" align="center">
              <template #default="{ $index }">
                <el-tag
                  :type="$index < 3 ? 'danger' : 'info'"
                  size="small"
                  effect="dark"
                >
                  {{ $index + 1 }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="name" label="电影名称" show-overflow-tooltip />
            <el-table-column prop="sales" label="票房(元)" width="110" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue';
import * as echarts from 'echarts';

import { getMoviePage } from '@/api/movie.js';
import { getOrderPage } from '@/api/order.js';
import { getUserPage } from '@/api/user.js';

const chartRef = ref(null);
let chartInstance = null;

const loading = ref(false);
const loadError = ref('');
const topMovies = ref([]);
const trend = ref([]);

const stats = reactive([
  { title: '今日票房（元）', value: '0', icon: 'Money', color: 'linear-gradient(135deg, #10b981, #059669)' },
  { title: '今日订单数', value: '0', icon: 'Tickets', color: 'linear-gradient(135deg, #3b82f6, #2563eb)' },
  { title: '用户总数', value: '0', icon: 'User', color: 'linear-gradient(135deg, #f59e0b, #d97706)' },
  { title: '电影总数', value: '0', icon: 'Film', color: 'linear-gradient(135deg, #8b5cf6, #7c3aed)' },
]);

function pad(num) {
  return String(num).padStart(2, '0');
}

function formatDate(date) {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}

function lastNDays(n) {
  const days = [];
  for (let i = n - 1; i >= 0; i -= 1) {
    const date = new Date();
    date.setHours(0, 0, 0, 0);
    date.setDate(date.getDate() - i);
    days.push(date);
  }
  return days;
}

function formatMoney(value) {
  return Number(value || 0).toLocaleString('zh-CN', { maximumFractionDigits: 2 });
}

// 已支付/退款中/已完成计入销售额，待支付/已取消/已退款不计入
function isCountableOrder(order) {
  return ![0, 2, 4].includes(order.status);
}

async function loadDashboard() {
  loading.value = true;
  loadError.value = '';

  try {
    const days = lastNDays(7);
    const [moviePage, userPage, ...dayPages] = await Promise.all([
      getMoviePage({ pageNo: 1, pageSize: 1 }),
      getUserPage({ pageNo: 1, pageSize: 1 }),
      ...days.map((day) =>
        getOrderPage({ pageNo: 1, pageSize: 200, date: formatDate(day) }),
      ),
    ]);

    const movieSales = new Map();
    const daily = days.map((day, index) => {
      const page = dayPages[index] || {};
      const records = page.records || [];
      let sales = 0;

      records.forEach((order) => {
        if (!isCountableOrder(order)) return;
        const amount = Number(order.payAmount || 0);
        const validAmount = Number.isFinite(amount) ? amount : 0;
        sales += validAmount;
        if (order.movieName) {
          movieSales.set(
            order.movieName,
            (movieSales.get(order.movieName) || 0) + validAmount,
          );
        }
      });

      return {
        date: formatDate(day),
        label: `${pad(day.getMonth() + 1)}-${pad(day.getDate())}`,
        sales: Number(sales.toFixed(2)),
        orders: page.total ?? records.length,
      };
    });

    trend.value = daily;

    const today = daily[daily.length - 1] || { sales: 0, orders: 0 };
    stats[0].value = formatMoney(today.sales);
    stats[1].value = formatMoney(today.orders);
    stats[2].value = formatMoney(userPage?.total || 0);
    stats[3].value = formatMoney(moviePage?.total || 0);

    topMovies.value = Array.from(movieSales.entries())
      .sort((a, b) => b[1] - a[1])
      .slice(0, 5)
      .map(([name, sales]) => ({ name, sales: formatMoney(sales) }));

    await nextTick();
    renderChart();
  } catch (err) {
    loadError.value = err?.message || '网络异常，请稍后重试';
  } finally {
    loading.value = false;
  }
}

function renderChart() {
  if (!chartRef.value) return;
  if (!chartInstance) {
    chartInstance = echarts.init(chartRef.value);
  }

  chartInstance.setOption(
    {
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value) => `¥${formatMoney(value)}`,
      },
      grid: {
        left: '3%',
        right: '4%',
        bottom: '3%',
        containLabel: true,
      },
      xAxis: {
        type: 'category',
        boundaryGap: false,
        data: trend.value.map((item) => item.label),
      },
      yAxis: {
        type: 'value',
        axisLabel: {
          formatter: (value) => `¥${formatMoney(value)}`,
        },
      },
      series: [
        {
          name: '票房（元）',
          type: 'line',
          smooth: true,
          areaStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: 'rgba(16, 185, 129, 0.3)' },
              { offset: 1, color: 'rgba(16, 185, 129, 0.02)' },
            ]),
          },
          lineStyle: {
            color: '#10b981',
            width: 2,
          },
          itemStyle: {
            color: '#10b981',
          },
          data: trend.value.map((item) => item.sales),
        },
      ],
    },
    true,
  );
}

function handleResize() {
  chartInstance?.resize();
}

onMounted(async () => {
  await loadDashboard();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  chartInstance?.dispose();
  chartInstance = null;
});
</script>

<style scoped>
.dashboard {
  padding: 4px;
  min-height: 200px;
}

.error-alert {
  margin-bottom: 16px;
}

.error-alert :deep(.el-alert__content) {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.stat-card {
  border-radius: 10px;
}

.stat-content {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #1f2937;
  margin-bottom: 4px;
}

.stat-label {
  font-size: 13px;
  color: #6b7280;
}

.stat-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: grid;
  place-items: center;
  color: #fff;
}

.chart-container {
  height: 320px;
}
</style>
