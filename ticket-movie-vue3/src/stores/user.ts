import { defineStore } from 'pinia'

import { getMe, logoutApi } from '@/api/auth'
import { getToken, removeToken, setToken } from '@/services/auth'
import type { UserInfo } from '@/types'

interface UserState {
  accessToken: string | null
  userInfo: UserInfo | null
}

export const useUserStore = defineStore('user', {
  state: (): UserState => ({
    accessToken: getToken(),
    userInfo: null,
  }),

  getters: {
    isLoggedIn: (state) => Boolean(state.accessToken),
    nickname: (state) => state.userInfo?.nickname || '影迷',
    phone: (state) => state.userInfo?.phone ?? '',
    avatar: (state) => state.userInfo?.avatar ?? '',
  },

  actions: {
    /** 登录成功后写入 Token 与用户信息 */
    login(accessToken: string, userInfo: UserInfo) {
      setToken(accessToken)
      this.accessToken = accessToken
      this.userInfo = userInfo
    },

    /** 刷新页面后从 localStorage 恢复 Token */
    restore() {
      this.accessToken = getToken()
    },

    /** 有 Token 但无用户信息时拉取 */
    async fetchProfile() {
      const data = await getMe()
      this.userInfo = data
      return data
    },

    /** 确保用户信息可用 */
    async ensureProfile() {
      this.restore()
      if (!this.accessToken) return null
      if (this.userInfo) return this.userInfo
      return this.fetchProfile()
    },

    /** 更新本地用户信息 */
    updateUserInfo(info: Partial<UserInfo>) {
      if (this.userInfo) this.userInfo = { ...this.userInfo, ...info }
    },

    /** 清空登录态 */
    logout() {
      removeToken()
      this.accessToken = null
      this.userInfo = null
    },

    /** 调用后端退出并清空本地登录态 */
    async logoutRemote() {
      try {
        await logoutApi()
      } catch {
        // 后端不可用时仍然清理本地登录态
      } finally {
        this.logout()
      }
    },
  },
})
