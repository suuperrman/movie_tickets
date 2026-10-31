import http from '@/utils/http';

/**
 * 管理员登录
 * POST /api/auth/login
 */
export function login(data) {
  return http.post('/auth/login', data);
}

/**
 * 获取当前管理员信息
 * GET /api/auth/me
 */
export function getAdminInfo() {
  return http.get('/auth/me');
}

/**
 * 退出登录
 * POST /api/auth/logout
 */
export function logoutApi() {
  return http.post('/auth/logout');
}
