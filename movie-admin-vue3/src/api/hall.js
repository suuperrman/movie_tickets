import http from '@/utils/http';

/**
 * 管理端 - 影厅分页
 * GET /api/admin/hall/page
 */
export function getHallPage(params) {
  return http.get('/admin/hall/page', { params });
}

/**
 * 管理端 - 影厅详情
 * GET /api/admin/hall/{id}
 */
export function getHallDetail(id) {
  return http.get(`/admin/hall/${id}`);
}

/**
 * 管理端 - 新增影厅
 * POST /api/admin/hall
 */
export function createHall(data) {
  return http.post('/admin/hall', data);
}

/**
 * 管理端 - 修改影厅
 * PUT /api/admin/hall/{id}
 */
export function updateHall(id, data) {
  return http.put(`/admin/hall/${id}`, data);
}

/**
 * 管理端 - 删除影厅
 * DELETE /api/admin/hall/{id}
 */
export function deleteHall(id) {
  return http.delete(`/admin/hall/${id}`);
}
