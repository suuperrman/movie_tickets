import { defineStore } from 'pinia';

import { login, getAdminInfo } from '@/api/auth';

const TOKEN_KEY = 'movie_admin_token';
const USER_KEY = 'movie_admin_user';

export const useUserStore = defineStore('adminUser', {
  state: () => ({
    token: localStorage.getItem(TOKEN_KEY) || '',
    userInfo: JSON.parse(localStorage.getItem(USER_KEY) || 'null'),
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    username: (state) => state.userInfo?.nickname || state.userInfo?.phone || '',
    isAdmin: (state) => state.userInfo?.role === 1 || state.userInfo?.isAdmin === true,
  },

  actions: {
    async login(phone, password) {
      const data = await login({ phone, password });
      this.token = data.accessToken;
      this.userInfo = data.user || null;
      localStorage.setItem(TOKEN_KEY, data.accessToken);
      if (this.userInfo) {
        localStorage.setItem(USER_KEY, JSON.stringify(this.userInfo));
      }
      return data;
    },

    async fetchProfile() {
      const data = await getAdminInfo();
      this.userInfo = data;
      localStorage.setItem(USER_KEY, JSON.stringify(data));
      return data;
    },

    logout() {
      this.token = '';
      this.userInfo = null;
      localStorage.removeItem(TOKEN_KEY);
      localStorage.removeItem(USER_KEY);
    },
  },
});
