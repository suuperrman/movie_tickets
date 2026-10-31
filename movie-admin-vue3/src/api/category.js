import http from '@/utils/http';

/**
 * 分类列表（全部，用于下拉选择）
 * GET /api/admin/category/list
 */
export function getCategoryList() {
  return http.get('/admin/category/list');
}

/**
 * 新增分类
 * POST /api/admin/category
 */
export function createCategory(data) {
  return http.post('/admin/category', data);
}

/**
 * 修改分类
 * PUT /api/admin/category/{id}
 */
export function updateCategory(id, data) {
  return http.put(`/admin/category/${id}`, data);
}

/**
 * 删除分类
 * DELETE /api/admin/category/{id}
 */
export function deleteCategory(id) {
  return http.delete(`/admin/category/${id}`);
}
