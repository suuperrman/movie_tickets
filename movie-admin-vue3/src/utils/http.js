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

// 响应拦截器：统一处理 Result 格式
http.interceptors.response.use(
  (response) => {
    const res = response.data;
    // 后端 Result 格式：{ success, errorMsg, data, total }
    if (res && typeof res.success === 'boolean') {
      if (res.success) {
        return res.data;
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
