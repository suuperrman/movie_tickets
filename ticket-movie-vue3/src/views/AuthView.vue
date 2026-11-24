<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'

import { login, register } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { errorMessage } from '@/utils/format'

type AuthTab = 'login' | 'register'

interface LoginForm {
  phone: string
  password: string
}

interface RegisterForm {
  phone: string
  nickname: string
  password: string
  confirm: string
}

interface AuthTabItem {
  key: AuthTab
  label: string
}

const tabs: AuthTabItem[] = [
  { key: 'login', label: '登录' },
  { key: 'register', label: '注册' },
]

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const activeTab = ref<AuthTab>('login')
const registerSuccess = ref(false)
const loading = ref(false)
const globalError = ref('')

const loginForm = reactive<LoginForm>({ phone: '', password: '' })
const registerForm = reactive<RegisterForm>({
  phone: '',
  nickname: '',
  password: '',
  confirm: '',
})

const loginErrors = reactive<Record<keyof LoginForm, string>>({ phone: '', password: '' })
const registerErrors = reactive<Record<keyof RegisterForm, string>>({
  phone: '',
  nickname: '',
  password: '',
  confirm: '',
})

/** 1 开头，第二位 3-9，共 11 位 */
const PHONE_REGEX = /^1[3-9]\d{9}$/
/** 后端密码规则：≥8 位，必须包含大小写字母和数字 */
const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*#?&]{8,}$/

function clearErrors() {
  globalError.value = ''
  loginErrors.phone = ''
  loginErrors.password = ''
  registerErrors.phone = ''
  registerErrors.nickname = ''
  registerErrors.password = ''
  registerErrors.confirm = ''
}

function validateLogin(): boolean {
  if (!loginForm.phone.trim()) loginErrors.phone = '请输入手机号'
  else if (!PHONE_REGEX.test(loginForm.phone.trim())) loginErrors.phone = '手机号格式不正确'
  if (!loginForm.password) loginErrors.password = '请输入密码'
  return !loginErrors.phone && !loginErrors.password
}

function validateRegister(): boolean {
  if (!registerForm.phone.trim()) registerErrors.phone = '请输入手机号'
  else if (!PHONE_REGEX.test(registerForm.phone.trim())) registerErrors.phone = '手机号格式不正确'
  if (!registerForm.password) registerErrors.password = '请输入密码'
  else if (!PASSWORD_REGEX.test(registerForm.password)) {
    registerErrors.password = '密码至少 8 位，需包含大小写字母和数字'
  }
  if (!registerForm.confirm) registerErrors.confirm = '请再次输入密码'
  else if (registerForm.password !== registerForm.confirm) {
    registerErrors.confirm = '两次密码输入不一致'
  }
  return (
    !registerErrors.phone && !registerErrors.password && !registerErrors.confirm
  )
}

function handleTabChange(tab: AuthTab) {
  if (activeTab.value === tab) return
  activeTab.value = tab
  registerSuccess.value = false
  clearErrors()
}

async function handleLogin() {
  clearErrors()
  if (!validateLogin()) return

  loading.value = true
  try {
    const data = await login({
      phone: loginForm.phone.trim(),
      password: loginForm.password,
    })
    userStore.login(data.accessToken, data.user)
    const redirect = typeof route.query.redirect === 'string' ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (error) {
    globalError.value = errorMessage(error, '登录失败，请检查手机号和密码')
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  clearErrors()
  if (!validateRegister()) return

  loading.value = true
  try {
    await register({
      phone: registerForm.phone.trim(),
      password: registerForm.password,
      nickname: registerForm.nickname.trim() || undefined,
    })
    registerSuccess.value = true
    loginForm.phone = registerForm.phone.trim()
    loginForm.password = ''
    activeTab.value = 'login'
  } catch (error) {
    globalError.value = errorMessage(error, '注册失败，请稍后重试')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <!-- 顶部跑马灯 -->
  <div class="topstrip" aria-hidden="true">
    <div class="marquee">
      <div class="marquee-group">
        <span>MEMBER ACCESS</span>
        <span>在线选座</span>
        <span>电子票根</span>
        <span>订单管理</span>
        <span>MOVIE TICKER</span>
        <span>灯暗下来 · 故事开始</span>
      </div>
      <div class="marquee-group">
        <span>MEMBER ACCESS</span>
        <span>在线选座</span>
        <span>电子票根</span>
        <span>订单管理</span>
        <span>MOVIE TICKER</span>
        <span>灯暗下来 · 故事开始</span>
      </div>
    </div>
  </div>

  <header class="topbar">
    <router-link class="brand" to="/">
      <span class="brand-mark">
        <svg
          width="20"
          height="20"
          viewBox="0 0 24 24"
          fill="none"
          stroke="currentColor"
          stroke-width="1.5"
        >
          <rect x="3" y="4" width="18" height="16" rx="1.5" />
          <path d="M7 4v16M17 4v16M3 9h4M3 15h4M17 9h4M17 15h4" />
        </svg>
      </span>
      <span class="brand-text">
        <strong>MOVIE TICKER</strong>
        <small>电影票务</small>
      </span>
    </router-link>
    <nav class="nav">
      <router-link class="nav-link" to="/">首页</router-link>
      <router-link class="nav-link" to="/search">搜索</router-link>
      <router-link class="nav-link is-active" to="/auth">登录 / 注册</router-link>
    </nav>
  </header>

  <main class="auth">
    <!-- 左侧红色场刊 -->
    <aside class="auth-aside">
      <div class="aside-inner">
        <p class="aside-kicker"><span class="num">03</span> MEMBERS ONLY / 会员入场</p>
        <h1>灯暗下来，<br /><span class="stroke-white">故事</span>开始。</h1>
        <p class="aside-text">
          登录后挑选场次与座位，管理你的订单。把接下来的两个小时，交给一块银幕。
        </p>

        <div class="benefits">
          <div class="benefit">
            <span class="no">01</span>
            <strong>在线选座</strong>
            <small>ONLINE SEAT</small>
          </div>
          <div class="benefit">
            <span class="no">02</span>
            <strong>影院取票</strong>
            <small>PICK UP</small>
          </div>
          <div class="benefit">
            <span class="no">03</span>
            <strong>电子票根</strong>
            <small>E-TICKET</small>
          </div>
        </div>

        <div class="ticket-stub" aria-hidden="true">
          <div class="stub-top"><span>ADMIT ONE</span><span>SCREEN 01 · 18:30</span></div>
          <div class="barcode"></div>
          <div class="stub-bottom">MT-2026-09128 / MEMBER ENTRY</div>
        </div>
      </div>
      <span class="ghost" aria-hidden="true">CINEMA</span>
    </aside>

    <!-- 右侧表单 -->
    <section class="auth-panel">
      <div class="mobile-intro">
        <p>MEMBERS ONLY / 会员入场</p>
        <h1>灯暗下来，故事开始。</h1>
      </div>

      <div class="form-card">
        <div class="form-top"><span>MEMBER ACCESS</span><span>NO. 09128</span></div>
        <div class="form-body">
          <h2>{{ activeTab === 'login' ? '欢迎回来' : '加入会员' }}</h2>
          <p class="form-sub">
            {{
              activeTab === 'login'
                ? '手机号登录快速抢票，或注册新账号。'
                : '创建账号，在线选座、管理订单。'
            }}
          </p>

          <div class="auth-tabs" role="tablist" aria-label="登录或注册">
            <button
              v-for="tab in tabs"
              :id="`tab-${tab.key}`"
              :key="tab.key"
              class="auth-tab"
              :class="{ 'is-active': activeTab === tab.key }"
              type="button"
              role="tab"
              :aria-selected="activeTab === tab.key"
              :aria-controls="`panel-${tab.key}`"
              :tabindex="activeTab === tab.key ? 0 : -1"
              @click="handleTabChange(tab.key)"
            >
              {{ tab.label }}
            </button>
          </div>

          <p v-if="globalError" class="form-banner" role="alert">{{ globalError }}</p>

          <p v-if="registerSuccess" class="form-banner" role="status">
            注册成功，请使用手机号与密码登录。
          </p>

          <!-- 登录 -->
          <form
            v-if="activeTab === 'login'"
            id="panel-login"
            class="auth-form"
            role="tabpanel"
            aria-labelledby="tab-login"
            novalidate
            @submit.prevent="handleLogin"
          >
            <div class="field">
              <label for="login-phone">手机号</label>
              <input
                id="login-phone"
                v-model="loginForm.phone"
                type="tel"
                inputmode="numeric"
                maxlength="11"
                placeholder="请输入 11 位手机号"
                autocomplete="tel"
                :class="{ 'is-invalid': loginErrors.phone }"
                :aria-invalid="Boolean(loginErrors.phone)"
                :aria-describedby="loginErrors.phone ? 'login-phone-error' : undefined"
              />
              <span id="login-phone-error" class="field-error" aria-live="polite">
                {{ loginErrors.phone }}
              </span>
            </div>

            <div class="field">
              <label for="login-password">密码</label>
              <input
                id="login-password"
                v-model="loginForm.password"
                type="password"
                placeholder="请输入密码"
                autocomplete="current-password"
                :class="{ 'is-invalid': loginErrors.password }"
                :aria-invalid="Boolean(loginErrors.password)"
                :aria-describedby="loginErrors.password ? 'login-password-error' : undefined"
              />
              <span id="login-password-error" class="field-error" aria-live="polite">
                {{ loginErrors.password }}
              </span>
            </div>

            <button class="submit" type="submit" :disabled="loading">
              {{ loading ? '登录中…' : '登录' }}
            </button>
            <p class="auth-terms">
              登录即表示同意 <a href="#">《用户协议》</a> 与 <a href="#">《隐私政策》</a>
            </p>
          </form>

          <!-- 注册 -->
          <form
            v-else
            id="panel-register"
            class="auth-form"
            role="tabpanel"
            aria-labelledby="tab-register"
            novalidate
            @submit.prevent="handleRegister"
          >
            <div class="field">
              <label for="reg-phone">手机号</label>
              <input
                id="reg-phone"
                v-model="registerForm.phone"
                type="tel"
                inputmode="numeric"
                maxlength="11"
                placeholder="请输入 11 位手机号"
                autocomplete="tel"
                :class="{ 'is-invalid': registerErrors.phone }"
                :aria-invalid="Boolean(registerErrors.phone)"
                :aria-describedby="registerErrors.phone ? 'reg-phone-error' : undefined"
              />
              <span id="reg-phone-error" class="field-error" aria-live="polite">
                {{ registerErrors.phone }}
              </span>
            </div>

            <div class="field">
              <label for="reg-nickname">昵称（可选）</label>
              <input
                id="reg-nickname"
                v-model="registerForm.nickname"
                type="text"
                placeholder="显示给其他用户"
                autocomplete="nickname"
              />
            </div>

            <div class="field">
              <label for="reg-password">密码</label>
              <input
                id="reg-password"
                v-model="registerForm.password"
                type="password"
                placeholder="至少 8 位，包含大小写字母和数字"
                autocomplete="new-password"
                :class="{ 'is-invalid': registerErrors.password }"
                :aria-invalid="Boolean(registerErrors.password)"
                :aria-describedby="registerErrors.password ? 'reg-password-error' : undefined"
              />
              <span id="reg-password-error" class="field-error" aria-live="polite">
                {{ registerErrors.password }}
              </span>
              <span class="field-hint">建议使用 8 位以上、包含大小写字母与数字的密码。</span>
            </div>

            <div class="field">
              <label for="reg-confirm">确认密码</label>
              <input
                id="reg-confirm"
                v-model="registerForm.confirm"
                type="password"
                placeholder="再次输入密码"
                autocomplete="new-password"
                :class="{ 'is-invalid': registerErrors.confirm }"
                :aria-invalid="Boolean(registerErrors.confirm)"
                :aria-describedby="registerErrors.confirm ? 'reg-confirm-error' : undefined"
              />
              <span id="reg-confirm-error" class="field-error" aria-live="polite">
                {{ registerErrors.confirm }}
              </span>
            </div>

            <button class="submit" type="submit" :disabled="loading">
              {{ loading ? '注册中…' : '注册' }}
            </button>
            <p class="auth-terms">注册成功后自动返回登录，使用手机号与密码进入。</p>
          </form>
        </div>
      </div>
    </section>
  </main>
</template>

<style scoped>
/* ==========================================================================
   顶部跑马灯
   ========================================================================== */

.topstrip {
  display: flex;
  align-items: center;
  height: 32px;
  overflow: hidden;
  color: var(--white);
  background: var(--red);
  border-bottom: 2px solid var(--ink);
}

.marquee {
  display: flex;
  width: max-content;
  animation: marquee 32s linear infinite;
}

.marquee-group {
  display: flex;
  flex-shrink: 0;
  align-items: center;
}

.marquee span {
  position: relative;
  padding: 0 30px;
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.18em;
  line-height: 32px;
  white-space: nowrap;
}

.marquee span::after {
  content: '◆';
  position: absolute;
  top: 0;
  right: -3px;
  font-size: 6px;
  opacity: 0.6;
}

@keyframes marquee {
  from {
    transform: translateX(0);
  }
  to {
    transform: translateX(-50%);
  }
}

/* ==========================================================================
   分屏入场
   ========================================================================== */

.auth {
  display: grid;
  grid-template-columns: 1fr 1fr;
  min-height: calc(100vh - 108px);
}

/* ---------- 左侧红色场刊 ---------- */
.auth-aside {
  position: relative;
  display: flex;
  align-items: center;
  overflow: hidden;
  padding: 56px clamp(28px, 5vw, 72px);
  color: var(--white);
  background: var(--red);
}

.auth-aside::before {
  content: '';
  position: absolute;
  inset: 0;
  background-image: radial-gradient(rgba(255, 255, 255, 0.55) 1px, transparent 1px);
  background-size: 14px 14px;
  opacity: 0.1;
  -webkit-mask-image: linear-gradient(145deg, #000 0, transparent 62%);
  mask-image: linear-gradient(145deg, #000 0, transparent 62%);
  pointer-events: none;
}

.aside-inner {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 520px;
}

.aside-kicker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
  font-family: var(--font-mono);
  font-size: 9.5px;
  font-weight: 500;
  letter-spacing: 0.18em;
}

.aside-kicker .num {
  display: inline-grid;
  place-items: center;
  width: 26px;
  height: 26px;
  color: var(--red);
  background: var(--white);
  font-size: 9.5px;
}

.aside h1 {
  margin-bottom: 22px;
  font-family: var(--font-ui);
  font-size: clamp(38px, 4.4vw, 64px);
  font-weight: 900;
  line-height: 1.14;
  letter-spacing: -0.03em;
}

.stroke-white {
  color: transparent;
  -webkit-text-stroke: 2px var(--white);
}

.aside-text {
  max-width: 34ch;
  color: rgba(255, 255, 255, 0.82);
  font-size: 13.5px;
  line-height: 1.95;
}

.benefits {
  margin-top: 34px;
  border-top: 1px solid rgba(255, 255, 255, 0.38);
}

.benefit {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 11px 0;
  border-bottom: 1px solid rgba(255, 255, 255, 0.38);
}

.benefit .no {
  font-family: var(--font-display);
  font-size: 19px;
  line-height: 1;
  opacity: 0.6;
}

.benefit strong {
  font-size: 12.5px;
  font-weight: 700;
  letter-spacing: 0.05em;
}

.benefit small {
  margin-left: auto;
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.16em;
  opacity: 0.68;
}

/* 票根装饰 */
.ticket-stub {
  position: relative;
  max-width: 340px;
  margin-top: 34px;
  padding: 13px 16px 12px;
  background: rgba(20, 17, 16, 0.1);
  border: 1.5px dashed rgba(255, 255, 255, 0.72);
}

.ticket-stub::before,
.ticket-stub::after {
  content: '';
  position: absolute;
  top: 50%;
  width: 14px;
  height: 14px;
  background: var(--red);
  border: 1.5px dashed rgba(255, 255, 255, 0.72);
  border-radius: 50%;
  transform: translateY(-50%);
}

.ticket-stub::before {
  left: -9px;
}

.ticket-stub::after {
  right: -9px;
}

.stub-top {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 9px;
  color: rgba(255, 255, 255, 0.82);
  font-family: var(--font-mono);
  font-size: 8px;
  letter-spacing: 0.14em;
}

.barcode {
  height: 20px;
  background: repeating-linear-gradient(
    90deg,
    rgba(255, 255, 255, 0.9) 0 2px,
    transparent 2px 4px,
    rgba(255, 255, 255, 0.9) 4px 5px,
    transparent 5px 9px
  );
  opacity: 0.85;
}

.stub-bottom {
  margin-top: 7px;
  color: rgba(255, 255, 255, 0.68);
  font-family: var(--font-mono);
  font-size: 7.5px;
  letter-spacing: 0.16em;
}

.ghost {
  position: absolute;
  bottom: -34px;
  left: -8px;
  z-index: 1;
  color: transparent;
  font-family: var(--font-display);
  font-size: clamp(120px, 16vw, 230px);
  line-height: 1;
  letter-spacing: 0.02em;
  white-space: nowrap;
  -webkit-text-stroke: 1.5px rgba(255, 255, 255, 0.2);
  pointer-events: none;
  user-select: none;
}

/* ---------- 右侧表单 ---------- */
.auth-panel {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 52px 32px;
  background: var(--paper);
}

.auth-panel::before {
  content: '';
  position: absolute;
  top: 26px;
  right: 26px;
  width: 170px;
  height: 170px;
  background-image: radial-gradient(var(--red) 1.1px, transparent 1.1px);
  background-size: 10px 10px;
  opacity: 0.16;
  -webkit-mask-image: radial-gradient(circle, #000 0, transparent 70%);
  mask-image: radial-gradient(circle, #000 0, transparent 70%);
  pointer-events: none;
}

.form-card {
  position: relative;
  z-index: 2;
  width: 100%;
  max-width: 452px;
  background: var(--white);
  border: 2px solid var(--ink);
  box-shadow: 10px 10px 0 var(--red);
  animation: card-in 720ms var(--ease) both;
}

.form-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 9px 18px;
  color: var(--white);
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.14em;
  background: var(--ink);
}

.form-body {
  padding: 30px 30px 26px;
}

.form-body h2 {
  margin-bottom: 6px;
  font-family: var(--font-ui);
  font-size: 30px;
  font-weight: 900;
  line-height: 1.2;
  letter-spacing: -0.025em;
}

.form-sub {
  margin-bottom: 20px;
  color: var(--gray);
  font-size: 12px;
}

.auth-tabs {
  display: grid;
  grid-template-columns: 1fr 1fr;
  margin-bottom: 20px;
  border: 2px solid var(--ink);
}

.auth-tab {
  padding: 11px 8px;
  color: var(--ink);
  font-size: 13px;
  font-weight: 700;
  background: var(--white);
  border: 0;
  transition:
    color 160ms ease,
    background-color 160ms ease;
}

.auth-tab + .auth-tab {
  border-left: 2px solid var(--ink);
}

.auth-tab:hover:not(.is-active) {
  color: var(--red);
}

.auth-tab.is-active {
  color: var(--white);
  background: var(--red);
}

.form-banner {
  margin-bottom: 16px;
  padding: 9px 12px;
  color: var(--red-deep);
  font-size: 11.5px;
  font-weight: 500;
  line-height: 1.5;
  background: rgba(217, 42, 31, 0.07);
  border-left: 3px solid var(--red);
}

.auth-form {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.field label {
  color: var(--ink);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.08em;
}

.field input {
  width: 100%;
  height: 48px;
  padding: 0 13px;
  color: var(--ink);
  font-family: var(--font-ui);
  font-size: 14px;
  background: #fff;
  border: 2px solid var(--ink);
  outline: none;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease;
}

.field input::placeholder {
  color: var(--muted);
}

.field input:focus {
  border-color: var(--red);
  box-shadow: 3px 3px 0 var(--red);
}

.field input.is-invalid {
  background: #fff4f2;
  border-color: var(--red);
}

.field-error {
  min-height: 13px;
  color: var(--red);
  font-size: 10.5px;
  line-height: 1.4;
}

.field-hint {
  color: var(--muted);
  font-size: 10.5px;
  line-height: 1.5;
}

.submit {
  width: 100%;
  height: 50px;
  margin-top: 2px;
  color: var(--white);
  font-size: 14px;
  font-weight: 700;
  letter-spacing: 0.06em;
  background: var(--red);
  border: 2px solid var(--ink);
  box-shadow: 4px 4px 0 var(--ink);
  transition:
    transform 160ms ease,
    box-shadow 160ms ease,
    background-color 160ms ease;
}

.submit:hover {
  background: var(--red-deep);
  box-shadow: 3px 3px 0 var(--ink);
  transform: translate(1px, 1px);
}

.submit:disabled {
  opacity: 0.6;
  cursor: not-allowed;
  transform: none;
  box-shadow: 4px 4px 0 var(--ink);
}

.auth-terms {
  margin-top: 14px;
  color: var(--muted);
  font-size: 10px;
  line-height: 1.7;
  text-align: center;
}

.auth-terms a {
  color: var(--ink);
  font-weight: 500;
  border-bottom: 1px solid var(--red);
}

.auth-terms a:hover {
  color: var(--red);
}

/* 移动端简介 */
.mobile-intro {
  display: none;
}

/* ==========================================================================
   动效
   ========================================================================== */

@keyframes card-in {
  from {
    opacity: 0;
    transform: translateY(20px);
  }
  to {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.001ms !important;
    animation-iteration-count: 1 !important;
    transition-duration: 0.001ms !important;
  }
}

/* ==========================================================================
   响应式
   ========================================================================== */

@media (max-width: 1080px) {
  .auth-aside {
    padding: 48px 36px;
  }

  .ticket-stub {
    max-width: 100%;
  }
}

@media (max-width: 920px) {
  .auth {
    display: block;
    min-height: 0;
  }

  .auth-aside {
    display: none;
  }

  .auth-panel {
    min-height: calc(100vh - 100px);
    padding: 0 18px 40px;
  }

  .mobile-intro {
    display: block;
    width: calc(100% + 36px);
    margin: 0 -18px 28px;
    padding: 26px 20px 24px;
    color: var(--white);
    background: var(--red);
    border-bottom: 2px solid var(--ink);
  }

  .mobile-intro p {
    margin-bottom: 10px;
    font-family: var(--font-mono);
    font-size: 8.5px;
    letter-spacing: 0.2em;
    opacity: 0.8;
  }

  .mobile-intro h1 {
    font-size: 34px;
    font-weight: 900;
    line-height: 1.2;
    letter-spacing: -0.03em;
  }

  .form-card {
    box-shadow: 7px 7px 0 var(--red);
  }

  .form-body {
    padding: 24px 20px 22px;
  }
}

@media (max-width: 620px) {
  .auth-panel {
    min-height: calc(100vh - 100px);
  }

  .form-body h2 {
    font-size: 26px;
  }

  .mobile-intro h1 {
    font-size: 30px;
  }
}
</style>
