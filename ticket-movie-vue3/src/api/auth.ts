import { http } from '@/services/http'
import type {
  CaptchaResult,
  ChangePasswordPayload,
  LoginPayload,
  RegisterPayload,
  SessionInfo,
  TokenResponse,
  UpdateProfilePayload,
  UserInfo,
} from '@/types'

/** 登录（手机号 + 密码） */
export function login(payload: LoginPayload) {
  return http.post<TokenResponse>('/auth/login', payload)
}

/** 注册 */
export function register(payload: RegisterPayload) {
  return http.post<UserInfo>('/auth/register', payload)
}

/** 图形验证码（连续失败 3 次后需要） */
export function getCaptcha(phone: string) {
  return http.get<CaptchaResult>('/auth/captcha', { params: { phone } })
}

/** 当前登录用户信息 */
export function getMe() {
  return http.get<UserInfo>('/auth/me')
}

/** 退出登录 */
export function logoutApi() {
  return http.post<void>('/auth/logout')
}

/** 修改昵称 / 头像 */
export function updateProfile(payload: UpdateProfilePayload) {
  return http.put<UserInfo>('/auth/profile', payload)
}

/** 修改密码 */
export function changePassword(payload: ChangePasswordPayload) {
  return http.post<void>('/auth/change-password', payload)
}

/** 活跃会话列表 */
export function getSessions() {
  return http.get<SessionInfo[]>('/auth/sessions')
}

/** 强制下线某个会话 */
export function revokeSession(sessionId: string) {
  return http.delete<void>(`/auth/sessions/${sessionId}`)
}
