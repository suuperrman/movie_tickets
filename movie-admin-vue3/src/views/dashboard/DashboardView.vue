<template>
  <div class="dashboard">
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
          <el-table :data="topMovies" style="width: 100%">
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
            <el-table-column prop="name" label="电影名称" />
            <el-table-column prop="sales" label="票房(万)" width="90" align="right" />
          </el-table>
        </el-card>
      </el-col>
    </el-row>

    <!-- 快捷入口 -->
    <el-row :gutter="20" style="margin-top: 20px">
      <el-col :span="24">
        <el-card shadow="hover">
          <template #header>
            <span class="card-title">快捷操作</span>
          </template>
          <div class="quick-actions">
            <el-button type="primary" @click="$router.push('/movie/create')">
              <el-icon><Plus /></el-icon>新增电影
            </el-button>
            <el-button type="success" @click="$router.push('/cinema/list')">
              <el-icon><OfficeBuilding /></el-icon>影院管理
            </el-button>
            <el-button type="warning" @click="$router.push('/schedule/list')">
              <el-icon><Calendar /></el-icon>排片管理
            </el-button>
            <el-button type="info" @click="$router.push('/order/list')">
              <el-icon><Tickets /></el-icon>订单管理
            </el-button>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref, nextTick } from 'vue';
import * as echarts from 'echarts';

const chartRef = ref(null);
let chartInstance = null;

const stats = [
  { title: '今日票房（元）', value: '128,560', icon: 'Money', color: 'linear-gradient(135deg, #10b981, #059669)' },
  { title: '今日订单数', value: '328', icon: 'Tickets', color: 'linear-gradient(135deg, #3b82f6, #2563eb)' },
  { title: '活跃用户', value: '1,256', icon: 'User', color: 'linear-gradient(135deg, #f59e0b, #d97706)' },
  { title: '上映电影', value: '18', icon: 'Film', color: 'linear-gradient(135deg, #8b5cf6, #7c3aed)' },
];

const topMovies = [
  { name: '流浪地球 3', sales: 8920 },
  { name: '哪吒之魔童闹海', sales: 7650 },
  { name: '飞驰人生 3', sales: 5430 },
  { name: '第二十条', sales: 4210 },
  { name: '热辣滚烫', sales: 3890 },
];

function initChart() {
  if (!chartRef.value) return;
  chartInstance = echarts.init(chartRef.value);

  const option = {
    tooltip: {
      trigger: 'axis',
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
      data: ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
    },
    yAxis: {
      type: 'value',
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
        data: [45000, 52000, 48000, 61000, 78000, 125000, 128560],
      },
    ],
  };

  chartInstance.setOption(option);
}

onMounted(() => {
  nextTick(() => {
    initChart();
    window.addEventListener('resize', () => chartInstance?.resize());
  });
});
</script>

<style scoped>
.dashboard {
  padding: 4px;
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

.quick-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
</style>
