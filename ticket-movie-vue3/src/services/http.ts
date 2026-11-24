import axios, { type AxiosRequestConfig } from 'axios'

import { getToken, removeToken } from './auth'

const REQUEST_TIMEOUT_MS = 10000

const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  timeout: REQUEST_TIMEOUT_MS,
  withCredentials: true,
})

// 请求拦截：自动注入 JWT
client.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

// 响应拦截：解包 Result.data，业务失败转成 Error
client.interceptors.response.use(
  (response) => {
    const body = response.data as unknown
    if (body && typeof body === 'object' && 'success' in body) {
      const result = body as { success: boolean; errorMsg?: string; data: unknown }
      if (result.success) return result.data as unknown as typeof response
      return Promise.reject(new Error(result.errorMsg || '请求失败'))
    }
    return body as unknown as typeof response
  },
  (error) => {
    if (axios.isAxiosError(error) && error.response?.status === 401) {
      removeToken()
      window.dispatchEvent(new CustomEvent('auth:logout'))
    }
    return Promise.reject(error)
  },
)

export const http = {
  get<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return client.get(url, config) as unknown as Promise<T>
  },
  post<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return client.post(url, data, config) as unknown as Promise<T>
  },
  put<T>(url: string, data?: unknown, config?: AxiosRequestConfig): Promise<T> {
    return client.put(url, data, config) as unknown as Promise<T>
  },
  delete<T>(url: string, config?: AxiosRequestConfig): Promise<T> {
    return client.delete(url, config) as unknown as Promise<T>
  },
}
