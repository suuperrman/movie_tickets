import { http } from '@/services/http'
import type {
  CinemaSchedule,
  MovieDetail,
  MovieListItem,
  PageResult,
  Review,
  Schedule,
  SeatMap,
} from '@/types'

export interface MoviePageParams {
  pageNo?: number
  pageSize?: number
  city?: string
}

export interface MovieSearchParams extends MoviePageParams {
  keyword: string
  categoryId?: number
}

/** 正在热映 */
export function getNowShowing(params: MoviePageParams = {}) {
  return http.get<PageResult<MovieListItem>>('/movie/now-showing', { params })
}

/** 即将上映 */
export function getComingSoon(params: MoviePageParams = {}) {
  return http.get<PageResult<MovieListItem>>('/movie/coming-soon', { params })
}

/** 电影详情 */
export function getMovieDetail(id: string | number) {
  return http.get<MovieDetail>(`/movie/${id}`)
}

/** 搜索电影 */
export function searchMovies(params: MovieSearchParams) {
  return http.get<PageResult<MovieListItem>>('/movie/search', { params })
}

/** 有排片的影院 */
export function getMovieCinemas(
  movieId: string | number,
  params: { city?: string; date?: string } = {},
) {
  return http.get<CinemaSchedule[]>(`/movie/${movieId}/cinemas`, { params })
}

/** 某影院某日期的场次 */
export function getMovieSessions(
  movieId: string | number,
  params: { cinemaId: string | number; date?: string },
) {
  return http.get<Schedule[]>(`/movie/${movieId}/sessions`, { params })
}

/** 公开评价列表 */
export function getMovieReviews(
  movieId: string | number,
  params: { pageNo?: number; pageSize?: number } = {},
) {
  return http.get<PageResult<Review>>(`/movie/${movieId}/reviews`, { params })
}

/** 场次座位图 */
export function getSeatMap(scheduleId: string | number) {
  return http.get<SeatMap>(`/movie/schedule/${scheduleId}/seats`)
}
