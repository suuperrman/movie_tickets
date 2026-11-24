import { http } from '@/services/http'
import type {
  CreateOrderPayload,
  CreatedOrder,
  MyOrderQuery,
  OrderDetail,
  PageResult,
  UserOrderItem,
} from '@/types'

/** 创建订单（锁座） */
export function createOrder(payload: CreateOrderPayload) {
  return http.post<CreatedOrder>('/order', payload)
}

/** 订单详情 */
export function getOrderDetail(orderId: string | number) {
  return http.get<OrderDetail>(`/order/${orderId}`)
}

/** 取消订单（释放锁座） */
export function cancelOrder(orderId: string | number) {
  return http.post<void>(`/order/${orderId}/cancel`)
}

/** 支付成功通知 */
export function payOrder(orderId: string | number) {
  return http.post<void>(`/order/${orderId}/pay`)
}

/** 我的订单列表 */
export function getMyOrders(params: MyOrderQuery = {}) {
  return http.get<PageResult<UserOrderItem>>('/order/my', { params })
}

/** 申请退款 */
export function applyRefund(orderId: string | number, reason?: string) {
  return http.post<void>(`/order/${orderId}/refund`, { reason })
}
