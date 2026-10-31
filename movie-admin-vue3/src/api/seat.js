import http from '@/utils/http';

/**
 * 管理端 - 座位分页（按影厅）
 * GET /api/admin/seat/page
 */
export function getSeatPage(params) {
  return http.get('/admin/seat/page', { params });
}

/**
 * 管理端 - 批量生成座位
 * POST /api/admin/seat/batch-generate
 *
 * @param {number} hallId 影厅 ID
 * @returns {Promise<number>} 生成的座位数
 */
export function batchGenerateSeats(hallId) {
  return http.post('/admin/seat/batch-generate', null, {
    params: { hallId },
  });
}

/**
 * 管理端 - 新增座位
 * POST /api/admin/seat
 */
export function createSeat(data) {
  return http.post('/admin/seat', data);
}

/**
 * 管理端 - 修改座位
 * PUT /api/admin/seat/{id}
 */
export function updateSeat(id, data) {
  return http.put(`/admin/seat/${id}`, data);
}

/**
 * 管理端 - 删除座位
 * DELETE /api/admin/seat/{id}
 */
export function deleteSeat(id) {
  return http.delete(`/admin/seat/${id}`);
}
