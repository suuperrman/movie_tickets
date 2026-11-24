/**
 * 与后端 DTO 对齐的类型定义。
 *
 * 后端 Jackson 开启了 WRITE_NUMBERS_AS_STRINGS，所有数字（Long / Integer / BigDecimal）
 * 在 JSON 中都会返回字符串，因此这里统一使用 `Numeric`，读取时用 `toNumber()` 归一化。
 */
export type Numeric = number | string

/** 后端统一响应结构（http 拦截器会解包 data） */
export interface Result<T> {
  success: boolean
  errorMsg: string
  data: T
  total?: Numeric
}

/** MyBatis-Plus 分页对象 */
export interface PageResult<T> {
  records: T[]
  total: Numeric
  current: Numeric
  size: Numeric
  pages: Numeric
}

// ===== 用户 / 认证 =====

export interface UserInfo {
  id: Numeric
  phone: string
  nickname: string
  avatar: string | null
  /** 0 普通用户 1 管理员 */
  role: Numeric
}

export interface TokenResponse {
  accessToken: string
  expiresIn: Numeric
  tokenType: string
  user: UserInfo
}

export interface SessionInfo {
  sessionId: string
  deviceType: string
  ip: string
  loginTime: string
  current: boolean
}

export interface LoginPayload {
  phone: string
  password: string
  captchaId?: string
  captchaCode?: string
}

export interface RegisterPayload {
  phone: string
  password: string
  nickname?: string
  captchaId?: string
  captchaCode?: string
}

export interface CaptchaResult {
  captchaId: string
  captchaImage: string
  required: boolean
}

export interface UpdateProfilePayload {
  nickname?: string
  avatar?: string
}

export interface ChangePasswordPayload {
  oldPassword: string
  newPassword: string
}

// ===== 电影 / 排片 =====

export interface MovieListItem {
  id: Numeric
  name: string
  director: string
  duration: Numeric
  releaseDate: string
  posterUrl: string | null
  /** 1 即将上映 2 正在热映 */
  status: Numeric
  description: string
  /** 逗号分隔，如 "动作,科幻" */
  categories: string
  minPrice: Numeric | null
  cinemaCount: Numeric | null
  scheduleCount?: Numeric | null
  avgRating: Numeric | null
  reviewCount: Numeric | null
}

export interface MovieCategory {
  id: Numeric
  name: string
}

export interface MovieActor {
  id: Numeric
  name: string
  avatarUrl: string | null
  roleName: string
}

export interface MovieScheduleItem {
  scheduleId: Numeric
  cinemaName: string
  hallName: string
  startTime: string
  endTime: string
  price: Numeric
  availableSeat: Numeric
}

export interface MovieDetail {
  id: Numeric
  name: string
  director: string
  duration: Numeric
  releaseDate: string
  posterUrl: string | null
  description: string
  status: Numeric
  categories: MovieCategory[]
  actors: MovieActor[]
  minPrice: Numeric | null
  cinemaCount: Numeric | null
  schedules?: MovieScheduleItem[]
  avgRating: Numeric | null
  reviewCount: Numeric | null
}

export interface CinemaSchedule {
  cinemaId: Numeric
  cinemaName: string
  city: string
  address: string
  phone: string
  minPrice: Numeric
  firstScheduleTime: string
  firstScheduleEndTime: string
  scheduleCount: Numeric
}

export interface Cinema {
  id: Numeric
  name: string
  city: string
  address: string
  phone: string
  status: Numeric
  hallCount: Numeric
  createdAt: string
}

export interface CinemaMovie {
  id: Numeric
  name: string
  posterUrl: string | null
  duration: Numeric
  director: string
  minPrice: Numeric | null
  scheduleCount: Numeric
}

export interface Schedule {
  scheduleId: Numeric
  hallId: Numeric
  hallName: string
  startTime: string
  endTime: string
  price: Numeric
  totalSeat: Numeric
  availableSeat: Numeric
  /** 1 可售 0 停售 */
  status: Numeric
}

export interface SeatMap {
  movieName: string
  cinemaName: string
  hallName: string
  startTime: string
  price: Numeric
  rowCount: Numeric
  colCount: Numeric
  /** 2 bit / 座，Base64；00 可售 01 锁定 10 已售 11 无此座位 */
  seatStatusBitmap: string
}

/** 座位状态 */
export const SeatStatus = {
  Available: 0,
  Locked: 1,
  Sold: 2,
} as const

// ===== 订单 =====

export interface OrderItem {
  seatCode: string
  rowNo: Numeric
  colNo: Numeric
  price: Numeric
}

export interface OrderDetail {
  id: Numeric
  orderNo: string
  userId: Numeric
  username: string
  /** 0 待支付 1 已支付 2 已取消 3 退款中 4 已退款 5 已完成 */
  status: Numeric
  movieId: Numeric
  movieName: string
  moviePoster: string | null
  cinemaId: Numeric
  cinemaName: string
  cinemaAddress: string
  hallId: Numeric
  hallName: string
  startTime: string
  endTime: string
  price: Numeric
  seatCount: Numeric
  totalAmount: Numeric
  discount: Numeric
  payAmount: Numeric
  expireAt: string
  payTime: string
  cancelTime: string
  refundReason: string
  refundApplyAt: string
  refundAuditAt: string
  createdAt: string
  items: OrderItem[]
}

/** 创建订单接口返回的 Order 实体 */
export interface CreatedOrder {
  id: Numeric
  orderNo: string
  userId: Numeric
  scheduleId: Numeric
  movieId: Numeric
  cinemaId: Numeric
  seatCount: Numeric
  totalAmount: Numeric
  discount: Numeric
  payAmount: Numeric
  status: Numeric
  expireAt: string
  payTime: string | null
  cancelTime: string | null
  createdAt: string
}

export interface UserOrderItem {
  id: Numeric
  orderNo: string
  status: Numeric
  movieId: Numeric
  movieName: string
  moviePoster: string | null
  cinemaName: string
  hallName: string
  startTime: string
  seatCount: Numeric
  payAmount: Numeric
  createdAt: string
  payTime: string
}

/** 座位位置（行/列），下单时由服务端解析为具体座位 */
export interface SeatPosition {
  rowNo: number
  colNo: number
}

export interface CreateOrderPayload {
  scheduleId: Numeric
  seats: SeatPosition[]
}

export interface MyOrderQuery {
  pageNo?: number
  pageSize?: number
  status?: number
}

// ===== 评价 =====

export interface Review {
  id: Numeric
  movieId: Numeric
  movieName: string
  userId: Numeric
  nickname: string
  avatar: string | null
  /** 1-5 星 */
  rating: Numeric
  content: string
  /** 0 待审核 1 已通过 2 已拒绝 */
  status: Numeric
  createdAt: string
}

export interface CreateReviewPayload {
  movieId: Numeric
  orderId?: Numeric
  rating: number
  content?: string
}

// ===== OSS =====

export interface StsCredentials {
  AccessKeyId: string
  AccessKeySecret: string
  SecurityToken: string
  Expiration: string
  bucket: string
  region: string
  endpoint: string
  dir: string
}
