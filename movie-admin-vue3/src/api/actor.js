import http from '@/utils/http';

/**
 * 管理端 - 演员分页
 * GET /api/admin/actor/page
 */
export function getActorPage(params) {
  return http.get('/admin/actor/page', { params });
}

/**
 * 管理端 - 演员详情
 * GET /api/admin/actor/{id}
 */
export function getActorDetail(id) {
  return http.get(`/admin/actor/${id}`);
}

/**
 * 管理端 - 新增演员
 * POST /api/admin/actor
 */
export function createActor(data) {
  return http.post('/admin/actor', data);
}

/**
 * 管理端 - 修改演员
 * PUT /api/admin/actor/{id}
 */
export function updateActor(id, data) {
  return http.put(`/admin/actor/${id}`, data);
}

/**
 * 管理端 - 删除演员
 * DELETE /api/admin/actor/{id}
 */
export function deleteActor(id) {
  return http.delete(`/admin/actor/${id}`);
}

/**
 * 管理端 - 演员启/停用
 * PATCH /api/admin/actor/{id}/status
 */
export function updateActorStatus(id, status) {
  return http.patch(`/admin/actor/${id}/status`, { status });
}
