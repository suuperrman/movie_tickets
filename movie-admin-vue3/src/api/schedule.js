import http from '@/utils/http';

/**
 * 管理端 - 场次分页
 * GET /api/admin/schedule/page
 */
export function getSchedulePage(params) {
  return http.get('/admin/schedule/page', { params });
}

/**
 * 管理端 - 场次详情
 * GET /api/admin/schedule/{id}
 */
export function getScheduleDetail(id) {
  return http.get(`/admin/schedule/${id}`);
}

/**
 * 管理端 - 新增场次
 * POST /api/admin/schedule
 */
export function createSchedule(data) {
  return http.post('/admin/schedule', data);
}

/**
 * 管理端 - 修改场次
 * PUT /api/admin/schedule/{id}
 */
export function updateSchedule(id, data) {
  return http.put(`/admin/schedule/${id}`, data);
}

/**
 * 管理端 - 删除场次
 * DELETE /api/admin/schedule/{id}
 */
export function deleteSchedule(id) {
  return http.delete(`/admin/schedule/${id}`);
}

/**
 * 管理端 - 场次启停用（停售/开售）
 * PATCH /api/admin/schedule/{id}/status
 */
export function updateScheduleStatus(id, status) {
  return http.patch(`/admin/schedule/${id}/status`, { status });
}
