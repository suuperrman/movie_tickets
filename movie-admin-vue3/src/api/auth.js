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

/**
 * 修改个人资料（昵称、头像）
 * PUT /api/auth/profile
 */
export function updateProfile(data) {
  return http.put('/auth/profile', data);
}

/**
 * 修改密码
 * POST /api/auth/change-password
 */
export function changePassword(data) {
  return http.post('/auth/change-password', data);
}
