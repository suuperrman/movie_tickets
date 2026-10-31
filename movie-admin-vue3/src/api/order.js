import http from '@/utils/http';

/**
 * 管理端 - 订单分页
 * GET /api/admin/order/page
 */
export function getOrderPage(params) {
  return http.get('/admin/order/page', { params });
}

/**
 * 管理端 - 订单详情
 * GET /api/admin/order/{id}
 */
export function getOrderDetail(id) {
  return http.get(`/admin/order/${id}`);
}

/**
 * 管理端 - 手动退款
 * POST /api/admin/order/{id}/refund
 */
export function refundOrder(id, reason) {
  return http.post(`/admin/order/${id}/refund`, { reason: reason || '' });
}

/**
 * 管理端 - 退款审核
 * POST /api/admin/order/{id}/refund/audit
 *
 * @param {number|string} id
 * @param {boolean} approve true同意退款 false拒绝退款
 * @param {string} [reason] 审核备注
 */
export function auditRefund(id, approve, reason) {
  return http.post(`/admin/order/${id}/refund/audit`, { approve, reason: reason || '' });
}
