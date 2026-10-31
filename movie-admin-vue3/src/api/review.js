import http from '@/utils/http';

/**
 * 管理端 - 评价分页
 * GET /api/admin/review/page
 */
export function getReviewPage(params) {
  return http.get('/admin/review/page', { params });
}

/**
 * 管理端 - 审核评价（0待审核 1已通过 2已拒绝）
 * PATCH /api/admin/review/{id}/status
 */
export function updateReviewStatus(id, status) {
  return http.patch(`/admin/review/${id}/status`, { status });
}

/**
 * 管理端 - 删除评价
 * DELETE /api/admin/review/{id}
 */
export function deleteReview(id) {
  return http.delete(`/admin/review/${id}`);
}
