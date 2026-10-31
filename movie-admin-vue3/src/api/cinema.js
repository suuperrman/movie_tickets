import http from '@/utils/http';

/**
 * 管理端 - 影院分页
 * GET /api/admin/cinema/page
 */
export function getCinemaPage(params) {
  return http.get('/admin/cinema/page', { params });
}

/**
 * 管理端 - 影院详情
 * GET /api/admin/cinema/{id}
 */
export function getCinemaDetail(id) {
  return http.get(`/admin/cinema/${id}`);
}

/**
 * 管理端 - 新增影院
 * POST /api/admin/cinema
 */
export function createCinema(data) {
  return http.post('/admin/cinema', data);
}

/**
 * 管理端 - 修改影院
 * PUT /api/admin/cinema/{id}
 */
export function updateCinema(id, data) {
  return http.put(`/admin/cinema/${id}`, data);
}

/**
 * 管理端 - 删除影院
 * DELETE /api/admin/cinema/{id}
 */
export function deleteCinema(id) {
  return http.delete(`/admin/cinema/${id}`);
}

/**
 * 管理端 - 影院启停用
 * PATCH /api/admin/cinema/{id}/status
 */
export function updateCinemaStatus(id, status) {
  return http.patch(`/admin/cinema/${id}/status`, { status });
}
