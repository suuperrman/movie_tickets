import http from '@/utils/http';

/**
 * 管理端 - 用户分页
 * GET /api/admin/user/page
 */
export function getUserPage(params) {
  return http.get('/admin/user/page', { params });
}

/**
 * 管理端 - 用户详情
 * GET /api/admin/user/{id}
 */
export function getUserDetail(id) {
  return http.get(`/admin/user/${id}`);
}

/**
 * 管理端 - 启用/禁用用户
 * PATCH /api/admin/user/{id}/status
 */
export function updateUserStatus(id, status) {
  return http.patch(`/admin/user/${id}/status`, { status });
}

/**
 * 管理端 - 重置密码
 * POST /api/admin/user/{id}/reset-pwd
 */
export function resetUserPassword(id, password) {
  return http.post(`/admin/user/${id}/reset-pwd`, { password });
}
