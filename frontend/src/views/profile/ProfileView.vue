<template>
  <div class="profile-page">
    <div class="profile-card">
      <!-- Avatar -->
      <div class="profile-avatar">
        <input
          ref="avatarInputRef"
          type="file"
          accept="image/*"
          hidden
          @change="handleAvatarChange"
        />
        <div class="avatar-circle" :class="{ clickable: true }" @click="triggerAvatarInput" :title="'点击更换头像'">
          <img v-if="profile?.avatar" :src="profile.avatar" class="avatar-img" />
          <span v-else class="avatar-letter">{{ avatarLetter }}</span>
          <div class="avatar-overlay">
            <el-icon :size="14"><Upload /></el-icon>
          </div>
        </div>
        <h2 class="profile-name">{{ profile?.nickname || profile?.username }}</h2>
        <p class="profile-email">{{ profile?.email || '未设置邮箱' }}</p>
      </div>

      <div class="profile-divider" />

      <!-- Info -->
      <div class="profile-info">
        <div class="info-row">
          <span class="info-label">用户名</span>
          <span class="info-value">{{ profile?.username }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">昵称</span>
          <span class="info-value">{{ profile?.nickname || '未设置' }}</span>
          <button class="info-edit" @click="openEdit('nickname')">
            <el-icon :size="14"><Edit /></el-icon>
          </button>
        </div>
        <div class="info-row">
          <span class="info-label">邮箱</span>
          <span class="info-value">{{ profile?.email || '未设置' }}</span>
          <button class="info-edit" @click="openEdit('email')">
            <el-icon :size="14"><Edit /></el-icon>
          </button>
        </div>
        <div class="info-row">
          <span class="info-label">手机号</span>
          <span class="info-value">{{ profile?.phone || '未绑定' }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">角色</span>
          <span class="info-value role-badge">{{ roleText }}</span>
        </div>
        <div class="info-row">
          <span class="info-label">注册时间</span>
          <span class="info-value">{{ formatDate(profile?.createdAt) }}</span>
        </div>
      </div>

      <div class="profile-divider" />

      <!-- Actions -->
      <div class="profile-actions">
        <button class="action-btn ghost" @click="showPasswordDialog = true">
          <el-icon :size="14"><Lock /></el-icon>
          修改密码
        </button>
        <button class="action-btn ghost danger" @click="handleLogout">
          <el-icon :size="14"><SwitchButton /></el-icon>
          退出登录
        </button>
      </div>
    </div>

    <!-- Edit Nickname Dialog -->
    <el-dialog
      v-model="showNicknameDialog"
      title="修改昵称"
      width="360px"
      top="30vh"
      custom-class="profile-dialog"
    >
      <el-form @submit.prevent="handleUpdateNickname">
        <el-input v-model="nicknameForm" placeholder="输入新昵称" maxlength="50" />
      </el-form>
      <template #footer>
        <el-button @click="showNicknameDialog = false">取消</el-button>
        <el-button type="primary" :loading="updatingProfile" @click="handleUpdateNickname">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- Edit Email Dialog -->
    <el-dialog
      v-model="showEmailDialog"
      title="修改邮箱"
      width="360px"
      top="30vh"
      custom-class="profile-dialog"
    >
      <el-form @submit.prevent="handleUpdateEmail">
        <el-input v-model="emailForm" placeholder="输入新邮箱" />
      </el-form>
      <template #footer>
        <el-button @click="showEmailDialog = false">取消</el-button>
        <el-button type="primary" :loading="updatingProfile" @click="handleUpdateEmail">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- Edit Phone Dialog -->
    <el-dialog
      v-model="showPhoneDialog"
      title="修改手机号"
      width="360px"
      top="30vh"
      custom-class="profile-dialog"
    >
      <el-form @submit.prevent="handleUpdatePhone">
        <el-input v-model="phoneForm" placeholder="输入手机号" maxlength="20" />
      </el-form>
      <template #footer>
        <el-button @click="showPhoneDialog = false">取消</el-button>
        <el-button type="primary" :loading="updatingProfile" @click="handleUpdatePhone">
          保存
        </el-button>
      </template>
    </el-dialog>

    <!-- Password Dialog -->
    <el-dialog
      v-model="showPasswordDialog"
      title="修改密码"
      width="360px"
      top="25vh"
      custom-class="profile-dialog"
    >
      <el-form
        ref="passwordFormRef"
        :model="passwordForm"
        :rules="passwordRules"
        label-position="top"
        size="large"
      >
        <el-form-item label="当前密码" prop="oldPassword">
          <el-input v-model="passwordForm.oldPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="passwordForm.newPassword" type="password" show-password />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="passwordForm.confirmPassword" type="password" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showPasswordDialog = false">取消</el-button>
        <el-button type="primary" :loading="updatingPassword" @click="handleUpdatePassword">
          保存
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Edit, Lock, SwitchButton, Upload } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { getProfileApi, updateProfileApi, updatePasswordApi, uploadAvatarApi } from '@/api/user'
import type { UserProfileVO } from '@/api/user'

const authStore = useAuthStore()
const profile = ref<UserProfileVO | null>(null)

const avatarInputRef = ref<HTMLInputElement>()
const avatarUploading = ref(false)

function triggerAvatarInput() {
  avatarInputRef.value?.click()
}

async function handleAvatarChange(e: Event) {
  const file = (e.target as HTMLInputElement).files?.[0]
  if (!file) return
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.warning('头像文件不能超过2MB')
    return
  }
  if (!file.type.startsWith('image/')) {
    ElMessage.warning('仅支持图片格式')
    return
  }
  avatarUploading.value = true
  try {
    const avatarUrl = await uploadAvatarApi(file)
    if (profile.value) {
      profile.value.avatar = avatarUrl
    }
    ElMessage.success('头像已更新')
  } catch (e: any) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    avatarUploading.value = false
    ;(e.target as HTMLInputElement).value = ''
  }
}

const avatarLetter = computed(() => {
  const name = profile.value?.nickname || profile.value?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const roleText = computed(() => {
  if (profile.value?.role === 'ADMIN') return '管理员'
  return '普通用户'
})

const showNicknameDialog = ref(false)
const showEmailDialog = ref(false)
const showPasswordDialog = ref(false)
const nicknameForm = ref('')
const emailForm = ref('')
const showPhoneDialog = ref(false)
const phoneForm = ref('')
const updatingProfile = ref(false)

const passwordForm = ref({ oldPassword: '', newPassword: '', confirmPassword: '' })
const updatingPassword = ref(false)
const passwordFormRef = ref<FormInstance>()
const passwordRules: FormRules = {
  oldPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度需在6-50个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请确认新密码', trigger: 'blur' },
    {
      validator: (_r: any, v: string, cb: Function) => {
        if (v !== passwordForm.value.newPassword) cb(new Error('两次密码不一致'))
        else cb()
      },
      trigger: 'blur'
    }
  ]
}

onMounted(async () => {
  try {
    profile.value = await getProfileApi()
  } catch {
    // fallback to store
    if (authStore.userInfo) {
      profile.value = {
        id: authStore.userInfo.id,
        username: authStore.userInfo.username,
        nickname: authStore.userInfo.nickname,
        email: authStore.userInfo.email,
        avatar: authStore.userInfo.avatar,
        phone: '',
        role: authStore.userInfo.role,
        createdAt: ''
      }
    }
  }
})

function openEdit(field: 'nickname' | 'email' | 'phone') {
  if (field === 'nickname') {
    nicknameForm.value = profile.value?.nickname || ''
    showNicknameDialog.value = true
  } else if (field === 'email') {
    emailForm.value = profile.value?.email || ''
    showEmailDialog.value = true
  } else {
    phoneForm.value = profile.value?.phone || ''
    showPhoneDialog.value = true
  }
}

async function handleUpdateNickname() {
  if (!nicknameForm.value.trim()) return
  updatingProfile.value = true
  try {
    await updateProfileApi({ nickname: nicknameForm.value.trim() })
    if (profile.value) profile.value.nickname = nicknameForm.value.trim()
    ElMessage.success('昵称已更新')
    showNicknameDialog.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    updatingProfile.value = false
  }
}

async function handleUpdateEmail() {
  if (!emailForm.value.trim()) return
  updatingProfile.value = true
  try {
    await updateProfileApi({ email: emailForm.value.trim() })
    if (profile.value) profile.value.email = emailForm.value.trim()
    ElMessage.success('邮箱已更新')
    showEmailDialog.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    updatingProfile.value = false
  }
}

async function handleUpdatePhone() {
  const phone = phoneForm.value.trim()
  if (!phone) {
    ElMessage.warning('请输入手机号')
    return
  }
  if (!/^[0-9+\-\s]{6,20}$/.test(phone)) {
    ElMessage.warning('手机号格式不正确')
    return
  }
  updatingProfile.value = true
  try {
    await updateProfileApi({ phone })
    if (profile.value) profile.value.phone = phone
    ElMessage.success('手机号已更新')
    showPhoneDialog.value = false
  } catch (e: any) {
    ElMessage.error(e.message || '更新失败')
  } finally {
    updatingProfile.value = false
  }
}

async function handleUpdatePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid) return

  updatingPassword.value = true
  try {
    await updatePasswordApi({
      oldPassword: passwordForm.value.oldPassword,
      newPassword: passwordForm.value.newPassword
    })
    ElMessage.success('密码已修改')
    showPasswordDialog.value = false
    passwordForm.value = { oldPassword: '', newPassword: '', confirmPassword: '' }
  } catch (e: any) {
    ElMessage.error(e.message || '密码修改失败')
  } finally {
    updatingPassword.value = false
  }
}

async function handleLogout() {
  try {
    await ElMessageBox.confirm('确定退出登录？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'info'
    })
    await authStore.logout()
  } catch {
    // cancelled
  }
}

function formatDate(iso?: string): string {
  if (!iso) return '—'
  return iso.slice(0, 10)
}
</script>

<style scoped>
.profile-page {
  display: flex;
  justify-content: center;
  padding: 48px 24px 80px;
}

.profile-card {
  width: 420px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl, 16px);
  padding: 40px 32px 32px;
}

.profile-avatar {
  text-align: center;
  margin-bottom: 8px;
}

.avatar-circle {
  width: 72px;
  height: 72px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  display: flex;
  align-items: center;
  justify-content: center;
  margin: 0 auto 14px;
  position: relative;
  overflow: hidden;
}

.avatar-circle.clickable {
  cursor: pointer;
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  border-radius: 50%;
}

.avatar-overlay {
  position: absolute;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  opacity: 0;
  transition: opacity 0.2s;
}

.avatar-circle.clickable:hover .avatar-overlay {
  opacity: 1;
}

.avatar-letter {
  font-size: 26px;
  font-weight: 700;
  color: #fff;
}

.profile-name {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 2px;
}

.profile-email {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.profile-divider {
  height: 1px;
  background: var(--color-border);
  margin: 20px 0;
}

.profile-info {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.info-row {
  display: flex;
  align-items: center;
  padding: 12px 0;
  border-bottom: 1px dashed var(--color-border);
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  width: 80px;
  font-size: 13px;
  color: var(--color-text-tertiary);
  flex-shrink: 0;
}

.info-value {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-primary);
}

.role-badge {
  display: inline-block;
  padding: 2px 8px;
  background: rgba(99, 102, 241, 0.12);
  color: var(--color-accent);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
  width: auto;
}

.info-edit {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  border-radius: 6px;
  cursor: pointer;
  opacity: 0;
  transition: all 0.15s;
}

.info-row:hover .info-edit {
  opacity: 1;
}

.info-edit:hover {
  background: var(--color-bg-tertiary);
  color: var(--color-accent);
}

.profile-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.action-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  padding: 10px;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  background: transparent;
  color: var(--color-text-primary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.15s;
}

.action-btn.ghost:hover {
  background: var(--color-bg-tertiary);
  border-color: var(--color-border-hover);
}

.action-btn.danger {
  color: var(--color-error);
  border-color: rgba(239, 68, 68, 0.2);
}

.action-btn.danger:hover {
  background: rgba(239, 68, 68, 0.08);
  border-color: var(--color-error);
}

:deep(.profile-dialog) {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-xl, 16px);
  border: 1px solid var(--color-border);
}

:deep(.profile-dialog .el-dialog__header) {
  padding: 24px 24px 0;
}

:deep(.profile-dialog .el-dialog__title) {
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 600;
}

:deep(.profile-dialog .el-dialog__body) {
  padding: 20px 24px;
}

:deep(.profile-dialog .el-dialog__footer) {
  padding: 0 24px 24px;
}

:deep(.profile-dialog .el-input__wrapper) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  box-shadow: none !important;
}

:deep(.profile-dialog .el-input__wrapper.is-focus) {
  border-color: var(--color-accent);
}

:deep(.profile-dialog .el-input__inner) {
  color: var(--color-text-primary);
}

:deep(.profile-dialog .el-form-item__label) {
  color: var(--color-text-secondary);
  font-size: 13px;
}
</style>
