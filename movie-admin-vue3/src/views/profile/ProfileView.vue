<template>
  <div class="profile-page">
    <el-row :gutter="20">
      <el-col :xs="24" :lg="10">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">基本资料</span>
          </template>

          <div class="avatar-block">
            <el-upload
              class="avatar-uploader"
              :show-file-list="false"
              :http-request="handleAvatarUpload"
              accept="image/*"
            >
              <el-avatar
                :size="88"
                :src="profileForm.avatar || undefined"
                class="avatar"
              >
                {{ usernameChar }}
              </el-avatar>
              <div class="avatar-tip">
                {{ uploading ? '上传中...' : '点击更换头像' }}
              </div>
            </el-upload>
          </div>

          <el-form
            ref="profileFormRef"
            :model="profileForm"
            :rules="profileRules"
            label-width="80px"
          >
            <el-form-item label="手机号">
              <el-input :model-value="userInfo.phone || '-'" disabled />
            </el-form-item>
            <el-form-item label="昵称" prop="nickname">
              <el-input
                v-model="profileForm.nickname"
                maxlength="50"
                show-word-limit
                placeholder="请输入昵称"
              />
            </el-form-item>
            <el-form-item label="角色">
              <el-tag :type="userInfo.role === 1 ? 'success' : 'info'">
                {{ userInfo.role === 1 ? '管理员' : '普通用户' }}
              </el-tag>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="saving" @click="handleSaveProfile">
                保存资料
              </el-button>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>

      <el-col :xs="24" :lg="14">
        <el-card shadow="never">
          <template #header>
            <span class="card-title">修改密码</span>
          </template>

          <el-form
            ref="pwdFormRef"
            :model="pwdForm"
            :rules="pwdRules"
            label-width="100px"
            class="pwd-form"
          >
            <el-form-item label="原密码" prop="oldPassword">
              <el-input
                v-model="pwdForm.oldPassword"
                type="password"
                show-password
                autocomplete="current-password"
                placeholder="请输入原密码"
              />
            </el-form-item>
            <el-form-item label="新密码" prop="newPassword">
              <el-input
                v-model="pwdForm.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="至少 8 位，包含大小写字母和数字"
              />
            </el-form-item>
            <el-form-item label="确认新密码" prop="confirmPassword">
              <el-input
                v-model="pwdForm.confirmPassword"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="请再次输入新密码"
              />
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :loading="changing" @click="handleChangePassword">
                修改密码
              </el-button>
              <span class="pwd-tip">修改成功后需要重新登录</span>
            </el-form-item>
          </el-form>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';

import { changePassword, updateProfile } from '@/api/auth.js';
import { useUserStore } from '@/stores/user';
import { uploadImage } from '@/utils/upload.js';

const router = useRouter();
const userStore = useUserStore();

const userInfo = computed(() => userStore.userInfo || {});
const usernameChar = computed(() => {
  const name = userInfo.value.nickname || userInfo.value.phone || '';
  return name ? name.charAt(0).toUpperCase() : 'A';
});

const profileFormRef = ref(null);
const pwdFormRef = ref(null);
const uploading = ref(false);
const saving = ref(false);
const changing = ref(false);

const profileForm = reactive({
  nickname: '',
  avatar: '',
});

const pwdForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const profileRules = {
  nickname: [{ max: 50, message: '昵称最长 50 个字符', trigger: 'blur' }],
};

const pwdRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)[a-zA-Z\d@$!%*#?&]{8,}$/,
      message: '密码至少 8 位，需包含大小写字母和数字',
      trigger: 'blur',
    },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (rule, value, callback) => {
        if (value !== pwdForm.newPassword) {
          callback(new Error('两次输入的密码不一致'));
        } else {
          callback();
        }
      },
      trigger: 'blur',
    },
  ],
};

function syncProfileForm(info) {
  if (!info) return;
  profileForm.nickname = info.nickname || '';
  profileForm.avatar = info.avatar || '';
}

watch(() => userStore.userInfo, syncProfileForm, { immediate: true });

async function handleAvatarUpload({ file }) {
  uploading.value = true;
  try {
    const result = await uploadImage(file, {
      bizDir: 'avatar',
      maxSize: 2 * 1024 * 1024,
    });
    profileForm.avatar = result.url;
    ElMessage.success('头像上传成功，点击「保存资料」生效');
  } catch (err) {
    ElMessage.error(err.message || '头像上传失败');
  } finally {
    uploading.value = false;
  }
}

async function handleSaveProfile() {
  try {
    await profileFormRef.value.validate();
  } catch {
    return;
  }

  saving.value = true;
  try {
    const data = await updateProfile({
      nickname: profileForm.nickname,
      avatar: profileForm.avatar,
    });
    userStore.updateUserInfo(data || profileForm);
    ElMessage.success('资料已保存');
  } catch (err) {
    // 拦截器已提示
  } finally {
    saving.value = false;
  }
}

async function handleChangePassword() {
  try {
    await pwdFormRef.value.validate();
  } catch {
    return;
  }

  changing.value = true;
  try {
    await changePassword({
      oldPassword: pwdForm.oldPassword,
      newPassword: pwdForm.newPassword,
    });
    ElMessage.success('密码修改成功，请重新登录');
    userStore.logout();
    router.push('/login');
  } catch (err) {
    // 拦截器已提示
  } finally {
    changing.value = false;
  }
}
</script>

<style scoped>
.profile-page {
  padding: 4px;
}

.card-title {
  font-weight: 600;
  font-size: 15px;
}

.avatar-block {
  display: flex;
  justify-content: center;
  margin-bottom: 20px;
}

.avatar-uploader {
  text-align: center;
  cursor: pointer;
}

.avatar {
  background: var(--el-color-primary);
  font-size: 32px;
}

.avatar-tip {
  margin-top: 8px;
  font-size: 12px;
  color: #6b7280;
}

.pwd-form {
  max-width: 460px;
}

.pwd-tip {
  margin-left: 12px;
  font-size: 12px;
  color: #9ca3af;
}
</style>
