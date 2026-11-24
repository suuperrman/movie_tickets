import { http } from '@/services/http'
import type { Cinema, CinemaMovie, PageResult } from '@/types'

export interface CinemaPageParams {
  pageNo?: number
  pageSize?: number
  /** 影院名称模糊搜索 */
  keyword?: string
  /** 城市（精确） */
  city?: string
  /** 地址模糊搜索 */
  address?: string
}

/** 影院分页（仅正常营业） */
export function getCinemaPage(params: CinemaPageParams = {}) {
  return http.get<PageResult<Cinema>>('/cinema/page', { params })
}

/** 已开业城市列表 */
export function getCinemaCities() {
  return http.get<string[]>('/cinema/cities')
}

/** 影院详情 */
export function getCinemaDetail(id: string | number) {
  return http.get<Cinema>(`/cinema/${id}`)
}

/** 影院当日正在放映的电影 */
export function getCinemaMovies(id: string | number, params: { date?: string } = {}) {
  return http.get<CinemaMovie[]>(`/cinema/${id}/movies`, { params })
}
