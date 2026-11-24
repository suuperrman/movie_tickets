import { http } from '@/services/http'
import type { CreateReviewPayload, Review } from '@/types'

/** 发布评价 */
export function createReview(payload: CreateReviewPayload) {
  return http.post<Review>('/review', payload)
}

/** 当前用户对某部电影的评价（未评价返回 null） */
export function getMyReviewOfMovie(movieId: string | number) {
  return http.get<Review | null>(`/review/mine/${movieId}`)
}
