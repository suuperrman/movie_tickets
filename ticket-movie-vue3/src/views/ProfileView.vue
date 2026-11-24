<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'

import { changePassword, updateProfile } from '@/api/auth'
import { useUserStore } from '@/stores/user'
import { errorMessage } from '@/utils/format'

const router = useRouter()
const userStore = useUserStore()

// 与后端保持一致：至少 8 位，包含大小写字母和数字
const PASSWORD_REGEX = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*#?&]{8,}$/

const loading = ref(false)
const loadError = ref('')
const maskedPhone = ref('')

const profileForm = reactive({ nickname: '' })
const profileSaving = ref(false)
const profileMessage = ref('')
const profileError = ref('')

const passwordForm = reactive({ oldPassword: '', newPassword: '', confirmPassword: '' })
const passwordSaving = ref(false)
const passwordMessage = ref('')
const passwordError = ref('')

function maskPhone(phone: string): string {
  if (!phone) return ''
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

async function loadProfile() {
  loading.value = true
  loadError.value = ''
  try {
    const info = await userStore.fetchProfile()
    profileForm.nickname = info?.nickname ?? ''
    maskedPhone.value = maskPhone(info?.phone ?? '')
  } catch (e) {
    loadError.value = errorMessage(e, '加载用户信息失败')
  } finally {
    loading.value = false
  }
}

async function saveProfile() {
  profileError.value = ''
  profileMessage.value = ''
  const nickname = profileForm.nickname.trim()
  if (!nickname) {
    profileError.value = '昵称不能为空'
    return
  }
  if (nickname.length > 50) {
    profileError.value = '昵称最长 50 个字符'
    return
  }
  profileSaving.value = true
  try {
    const info = await updateProfile({ nickname })
    userStore.updateUserInfo(info)
    profileForm.nickname = info.nickname ?? nickname
    profileMessage.value = '资料已保存'
  } catch (e) {
    profileError.value = errorMessage(e, '保存失败')
  } finally {
    profileSaving.value = false
  }
}

async function savePassword() {
  passwordError.value = ''
  passwordMessage.value = ''
  if (!passwordForm.oldPassword) {
    passwordError.value = '请输入原密码'
    return
  }
  if (!PASSWORD_REGEX.test(passwordForm.newPassword)) {
    passwordError.value = '新密码至少 8 位，需包含大小写字母和数字'
    return
  }
  if (passwordForm.newPassword !== passwordForm.confirmPassword) {
    passwordError.value = '两次输入的新密码不一致'
    return
  }
  passwordSaving.value = true
  try {
    await changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
    })
    passwordMessage.value = '密码修改成功'
    passwordForm.oldPassword = ''
    passwordForm.newPassword = ''
    passwordForm.confirmPassword = ''
  } catch (e) {
    passwordError.value = errorMessage(e, '密码修改失败')
  } finally {
    passwordSaving.value = false
  }
}

async function handleLogout() {
  await userStore.logoutRemote()
  router.push({ name: 'home' })
}

onMounted(() => {
  void loadProfile()
})
</script>

<template>
  <main class="page wrap">
    <header class="profile-head">
      <p class="kicker">05 / 账户 / ACCOUNT</p>
      <h1 class="page-title">个人中心<span class="dot">。</span></h1>
      <p class="page-sub">管理你的昵称与登录密码，随时查看自己的观影记录。</p>
    </header>

    <div v-if="loading" class="state-box">
      <span class="spinner"></span>
      <strong>正在读取账户信息</strong>
      <p>稍等，正在核对你的会员卡。</p>
    </div>

    <div v-else-if="loadError" class="state-box">
      <span class="state-glyph">!</span>
      <strong>账户信息加载失败</strong>
      <p>{{ loadError }}</p>
      <button class="btn btn-primary" type="button" @click="loadProfile">重试</button>
    </div>

    <template v-else>
      <section class="overview">
        <div class="avatar">
          <img v-if="userStore.avatar" :src="userStore.avatar" alt="" />
          <span v-else>{{ userStore.nickname.slice(0, 1).toUpperCase() }}</span>
        </div>
        <div class="overview-info">
          <p class="overview-kicker">MEMBER / {{ maskedPhone }}</p>
          <h2>{{ userStore.nickname || '未设置昵称' }}</h2>
          <p class="overview-sub">{{ maskedPhone }}</p>
        </div>
        <button class="btn btn-sm logout-btn" type="button" @click="handleLogout">退出登录</button>
      </section>

      <div class="profile-grid">
        <section class="panel">
          <div class="panel-head">
            <h2>基本资料</h2>
            <span class="mono">PROFILE</span>
          </div>
          <div class="panel-body">
            <div class="form-field">
              <label for="nickname">昵称</label>
              <input
                id="nickname"
                v-model="profileForm.nickname"
                maxlength="50"
                placeholder="请输入昵称"
              />
              <span class="form-hint">展示在评价与个人中心，最多 50 个字符</span>
            </div>

            <p v-if="profileError" class="tip is-error" role="alert">{{ profileError }}</p>
            <p v-if="profileMessage" class="tip is-success" role="status">{{ profileMessage }}</p>

            <div class="panel-actions">
              <button class="btn btn-primary" type="button" :disabled="profileSaving" @click="saveProfile">
                {{ profileSaving ? '保存中…' : '保存资料' }}
              </button>
            </div>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>修改密码</h2>
            <span class="mono">SECURITY</span>
          </div>
          <div class="panel-body">
            <div class="form-field">
              <label for="old-password">原密码</label>
              <input
                id="old-password"
                v-model="passwordForm.oldPassword"
                type="password"
                autocomplete="current-password"
                placeholder="请输入当前密码"
              />
            </div>

            <div class="form-field">
              <label for="new-password">新密码</label>
              <input
                id="new-password"
                v-model="passwordForm.newPassword"
                type="password"
                autocomplete="new-password"
                placeholder="至少 8 位，包含大小写字母和数字"
              />
            </div>

            <div class="form-field">
              <label for="confirm-password">确认新密码</label>
              <input
                id="confirm-password"
                v-model="passwordForm.confirmPassword"
                type="password"
                autocomplete="new-password"
                placeholder="再次输入新密码"
              />
            </div>

            <p v-if="passwordError" class="tip is-error" role="alert">{{ passwordError }}</p>
            <p v-if="passwordMessage" class="tip is-success" role="status">{{ passwordMessage }}</p>

            <div class="panel-actions">
              <button class="btn btn-primary" type="button" :disabled="passwordSaving" @click="savePassword">
                {{ passwordSaving ? '提交中…' : '修改密码' }}
              </button>
            </div>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>

<style scoped>
.profile-head {
  margin-bottom: 28px;
}

.dot {
  color: var(--red);
}

.overview {
  display: flex;
  align-items: center;
  gap: 18px;
  margin-bottom: 22px;
  padding: 24px;
  color: var(--white);
  background: var(--ink);
  border: 2px solid var(--ink);
  box-shadow: 10px 10px 0 var(--red);
}

.avatar {
  display: grid;
  place-items: center;
  width: 66px;
  height: 66px;
  flex-shrink: 0;
  overflow: hidden;
  color: var(--white);
  font-family: var(--font-display);
  font-size: 28px;
  background: var(--red);
  border: 2px solid var(--white);
  border-radius: 50%;
}

.avatar img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.overview-info {
  flex: 1;
  min-width: 0;
}

.overview-kicker {
  color: #ff9b91;
  font-family: var(--font-mono);
  font-size: 8.5px;
  letter-spacing: 0.16em;
}

.overview-info h2 {
  margin-top: 4px;
  overflow: hidden;
  font-size: 28px;
  font-weight: 900;
  letter-spacing: -0.025em;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.overview-sub {
  margin-top: 3px;
  color: rgba(255, 255, 255, 0.55);
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.06em;
}

.logout-btn {
  flex-shrink: 0;
  color: var(--white);
  background: transparent;
  border-color: rgba(255, 255, 255, 0.4);
  box-shadow: none;
}

.logout-btn:hover {
  color: var(--red);
  border-color: var(--red);
}

.profile-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
  align-items: start;
}

.panel-body {
  display: flex;
  flex-direction: column;
  gap: 15px;
}

.panel-actions {
  display: flex;
  justify-content: flex-end;
  margin-top: 4px;
}

.tip {
  padding: 9px 12px;
  font-size: 11.5px;
  line-height: 1.6;
  border-left: 3px solid;
}

.tip.is-error {
  color: var(--red);
  background: rgba(217, 42, 31, 0.07);
}

.tip.is-success {
  color: var(--ink);
  background: rgba(20, 17, 16, 0.06);
  border-left-color: var(--ink);
}

@media (max-width: 820px) {
  .profile-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 620px) {
  .overview {
    flex-wrap: wrap;
    box-shadow: 7px 7px 0 var(--red);
  }

  .logout-btn {
    width: 100%;
  }
}
</style>
