const TOKEN_KEY = 'movie_ticker_token'

/** 读取本地 JWT Access Token */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

/** 写入 JWT Access Token */
export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

/** 清除 JWT Access Token */
export function removeToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}

/** 当前是否有登录态 */
export function isLoggedIn(): boolean {
  return Boolean(getToken())
}
