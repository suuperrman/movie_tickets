import http from '@/utils/http';

/**
 * 管理端 - 电影分页查询
 * GET /api/admin/movie/page
 */
export function getMoviePage(params) {
  return http.get('/admin/movie/page', { params });
}

/**
 * 管理端 - 电影详情
 * GET /api/admin/movie/{id}
 */
export function getMovieDetail(id) {
  return http.get(`/admin/movie/${id}`);
}

/**
 * 管理端 - 新增电影
 * POST /api/admin/movie
 */
export function createMovie(data) {
  return http.post('/admin/movie', data);
}

/**
 * 管理端 - 修改电影
 * PUT /api/admin/movie/{id}
 */
export function updateMovie(id, data) {
  return http.put(`/admin/movie/${id}`, data);
}

/**
 * 管理端 - 删除电影
 * DELETE /api/admin/movie/{id}
 */
export function deleteMovie(id) {
  return http.delete(`/admin/movie/${id}`);
}

/**
 * 管理端 - 电影上下架
 * PATCH /api/admin/movie/{id}/status
 */
export function updateMovieStatus(id, status) {
  return http.patch(`/admin/movie/${id}/status`, { status });
}
