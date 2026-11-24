import axios from 'axios';
import { ElMessage } from 'element-plus';

import { useUserStore } from '@/stores/user';

const http = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 15000,
});

// 请求拦截器：注入 Token
http.interceptors.request.use(
  (config) => {
    const userStore = useUserStore();
    if (userStore.token) {
      config.headers.Authorization = `Bearer ${userStore.token}`;
    }
    return config;
  },
  (error) => Promise.reject(error),
);

// 后端为了兼容雪花 ID，会把部分数字序列化成字符串（如 status、gender），
// 这里按字段名白名单还原为数字，保证模板里的 === 1、单选组回显等判断正常；
// id、手机号等字段不在白名单内，仍保持字符串，避免精度丢失
const NUMERIC_KEYS = new Set([
  'status',
  'gender',
  'seatType',
  'rowNo',
  'colNo',
  'rowCount',
  'colCount',
  'seatCount',
  'duration',
  'year',
  'score',
  'total',
  'size',
  'current',
  'pages',
  'pageNo',
  'pageSize',
]);

function normalizeNumbers(value) {
  if (Array.isArray(value)) {
    value.forEach((item, index) => {
      value[index] = normalizeNumbers(item);
    });
    return value;
  }
  if (value && typeof value === 'object') {
    Object.keys(value).forEach((key) => {
      const item = value[key];
      if (typeof item === 'string' && NUMERIC_KEYS.has(key) && /^-?\d+(\.\d+)?$/.test(item)) {
        value[key] = Number(item);
      } else {
        value[key] = normalizeNumbers(item);
      }
    });
  }
  return value;
}

// 响应拦截器：统一处理 Result 格式
http.interceptors.response.use(
  (response) => {
    const res = response.data;
    // 后端 Result 格式：{ success, errorMsg, data, total }
    if (res && typeof res.success === 'boolean') {
      if (res.success) {
        return normalizeNumbers(res.data);
      } else {
        ElMessage.error(res.errorMsg || '请求失败');
        return Promise.reject(new Error(res.errorMsg || '请求失败'));
      }
    }
    return res;
  },
  (error) => {
    if (error.response) {
      const { status } = error.response;
      if (status === 401) {
        ElMessage.error('登录已过期，请重新登录');
        const userStore = useUserStore();
        userStore.logout();
        window.location.href = '/login';
      } else if (status === 403) {
        ElMessage.error('没有权限访问该资源');
      } else if (status === 500) {
        ElMessage.error('服务器内部错误');
      } else {
        ElMessage.error(error.response.data?.errorMsg || `请求失败 (${status})`);
      }
    } else if (error.request) {
      ElMessage.error('网络错误，请检查网络连接');
    } else {
      ElMessage.error(error.message || '请求失败');
    }
    return Promise.reject(error);
  },
);

export { http };
export default http;
