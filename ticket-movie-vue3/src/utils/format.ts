import type { Numeric } from '@/types'

/** 后端数字统一序列化为字符串，这里安全转成 number */
export function toNumber(value: Numeric | null | undefined, fallback = 0): number {
  if (value === null || value === undefined || value === '') return fallback
  const n = Number(value)
  return Number.isFinite(n) ? n : fallback
}

/** 金额展示，两位小数 */
export function money(value: Numeric | null | undefined): string {
  return toNumber(value).toFixed(2)
}

/** 整数金额（票价等通常为整数），避免显示 .00 */
export function priceText(value: Numeric | null | undefined): string {
  const n = toNumber(value)
  return Number.isInteger(n) ? String(n) : n.toFixed(2)
}

/** 评分展示，一位小数 */
export function ratingText(value: Numeric | null | undefined): string {
  if (value === null || value === undefined || value === '') return '暂无'
  return toNumber(value).toFixed(1)
}

/** 补零 */
export function pad(value: number): string {
  return String(value).padStart(2, '0')
}

/** 解析后端 "yyyy-MM-dd HH:mm:ss" / ISO / 时间戳 */
export function parseDate(value: string | null | undefined): Date | null {
  if (!value) return null
  const normalized = value.includes('T') ? value : value.replace(' ', 'T')
  const date = new Date(normalized)
  return Number.isNaN(date.getTime()) ? null : date
}

/** 只取日期部分 "yyyy-MM-dd" */
export function dateOnly(value: string | null | undefined): string {
  if (!value) return ''
  return value.slice(0, 10)
}

/** 只取 "HH:mm" */
export function timeOnly(value: string | null | undefined): string {
  if (!value) return ''
  const match = value.match(/(\d{2}):(\d{2})/)
  return match ? `${match[1]}:${match[2]}` : ''
}

/** 兼容后端的日期格式，输出 "MM月DD日" */
export function monthDay(value: string | null | undefined): string {
  const date = parseDate(value)
  if (!date) return ''
  return `${pad(date.getMonth() + 1)}月${pad(date.getDate())}日`
}

/** 今天起第 offset 天的 "yyyy-MM-dd" */
export function dateOffset(offset: number): string {
  const date = new Date()
  date.setDate(date.getDate() + offset)
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/** 星期几 */
export function weekdayLabel(value: string): string {
  const date = parseDate(value)
  if (!date) return ''
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()] ?? ''
}

/** 从错误对象提取后端错误文案 */
export function errorMessage(error: unknown, fallback = '请求失败，请稍后重试'): string {
  if (axiosLike(error)) {
    const body = error.response?.data as { errorMsg?: string; message?: string } | undefined
    if (body?.errorMsg) return body.errorMsg
    if (body?.message) return body.message
    if (error.response?.status === 502 || error.response?.status === 504) {
      return '无法连接服务器，请确认后端已启动'
    }
    if (error.code === 'ECONNABORTED') return '请求超时，请检查网络后重试'
    if (!error.response) return '无法连接服务器，请确认后端已启动'
    if (error.message) return error.message
  }
  if (error instanceof Error && error.message) return error.message
  return fallback
}

interface AxiosLike {
  message?: string
  code?: string
  response?: { status?: number; data?: unknown }
}

function axiosLike(error: unknown): error is AxiosLike {
  return typeof error === 'object' && error !== null && ('response' in error || 'code' in error)
}
