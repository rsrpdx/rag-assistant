<template>
  <div class="login-page">
    <div class="particles">
      <div class="particle" v-for="i in 8" :key="i" />
    </div>

    <div class="login-card">
      <div class="login-logo">
        <div class="logo-icon">
          <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <circle cx="12" cy="12" r="10"/>
            <path d="M12 16v-4"/>
            <path d="M12 8h.01"/>
          </svg>
        </div>
        <h1>RAG Assistant</h1>
        <p>AI知识库问答平台</p>
      </div>

      <form class="login-form" @submit.prevent="handleLogin">
        <el-input
          v-model="form.username"
          placeholder="用户名"
          :prefix-icon="User"
          size="large"
          @keyup.enter="handleLogin"
        />

        <el-input
          ref="passwordInputRef" v-model="form.password"
          type="password"
          placeholder="密码"
          :prefix-icon="Lock"
          size="large"
          show-password
          @keyup.enter="handleLogin"
        />

        <div class="login-error">{{ errorMsg }}</div>

        <button
          class="login-btn"
          :class="{ 'is-loading': loading }"
          :disabled="loading"
          type="submit"
        >
          <span v-if="loading">
            <svg class="spin" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
              <path d="M21 12a9 9 0 1 1-6.219-8.56"/>
            </svg>
            登录中...
          </span>
          <span v-else>登录</span>
        </button>
      </form>

      <div class="login-footer">
        没有账号？        <a href="javascript:void(0)" @click="showRegister = true">注册</a>
      </div>
    </div>

    <!-- Register Dialog -->
    <el-dialog
      v-model="showRegister"
      title="注册账号"
      width="400px"
      top="20vh"
      :close-on-click-modal="false"
      custom-class="register-dialog"
    >
      <el-form
        ref="registerFormRef"
        :model="registerForm"
        :rules="registerRules"
        label-position="top"
        size="large"
      >
        <el-form-item label="用户名" prop="username">
          <el-input v-model="registerForm.username" placeholder="3-50个字符" />
        </el-form-item>
        <el-form-item label="邮箱" prop="email">
          <el-input v-model="registerForm.email" placeholder="选填" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="registerForm.password"
            type="password"
            placeholder="6-50个字符"
            show-password
          />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input
            v-model="registerForm.confirmPassword"
            type="password"
            placeholder="再次输入密码"
            show-password
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showRegister = false">取消</el-button>
        <el-button
          type="primary"
          :loading="registerLoading"
          @click="handleRegister"
        >
          注册
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, computed } from 'vue'
import { User, Lock } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const authStore = useAuthStore()

const form = reactive({
  username: '',
  password: ''
})

const loading = ref(false)
const errorMsg = ref('')

const showRegister = ref(false)
const registerLoading = ref(false)
const registerFormRef = ref<FormInstance>()
const passwordInputRef = ref()

const registerForm = reactive({
  username: '',
  email: '',
  password: '',
  confirmPassword: ''
})

const registerRules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 3, max: 50, message: '用户名长度需在3-50个字符之间', trigger: 'blur' }
  ],
  email: [
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value && !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value)) {
          callback(new Error('请输入有效的邮箱地址'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 50, message: '密码长度需在6-50个字符之间', trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: '请再次输入密码', trigger: 'blur' },
    {
      validator: (_rule: any, value: string, callback: Function) => {
        if (value !== registerForm.password) {
          callback(new Error('两次输入的密码不一致'))
        } else {
          callback()
        }
      },
      trigger: 'blur'
    }
  ]
}

async function handleLogin() {
  if (!form.username || !form.password) {
    errorMsg.value = '请输入用户名和密码'
    return
  }

  loading.value = true
  errorMsg.value = ''

  try {
    await authStore.login({
      username: form.username,
      password: form.password
    })
  } catch (e: any) {
    errorMsg.value = e.message || '登录失败，请重试'
  } finally {
    loading.value = false
  }
}

async function handleRegister() {
  if (!registerFormRef.value) return

  const valid = await registerFormRef.value.validate().catch(() => false)
  if (!valid) return

  registerLoading.value = true
  try {
    await authStore.register({
      username: registerForm.username,
      password: registerForm.password,
      email: registerForm.email || undefined
    })
    ElMessage.success('注册成功，请输入密码登录')
    showRegister.value = false
    form.username = registerForm.username
    form.password = ''
    errorMsg.value = ''
    setTimeout(() => passwordInputRef.value?.focus(), 100)
  } catch (e: any) {
    ElMessage.error(e.message || '注册失败')
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped>
@keyframes float {
  0%, 100% {
    transform: translateY(0) translateX(0);
    opacity: 0.3;
  }
  25% {
    transform: translateY(-30px) translateX(15px);
    opacity: 0.5;
  }
  50% {
    transform: translateY(-10px) translateX(-10px);
    opacity: 0.4;
  }
  75% {
    transform: translateY(-40px) translateX(5px);
    opacity: 0.3;
  }
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: var(--color-bg-primary);
  position: relative;
  overflow: hidden;
}

.login-page::before {
  content: '';
  position: absolute;
  top: -50%;
  left: -50%;
  width: 200%;
  height: 200%;
  background:
    radial-gradient(ellipse at 20% 50%, rgba(99, 102, 241, 0.08) 0%, transparent 50%),
    radial-gradient(ellipse at 80% 20%, rgba(129, 140, 248, 0.05) 0%, transparent 50%),
    radial-gradient(ellipse at 50% 80%, rgba(99, 102, 241, 0.06) 0%, transparent 50%);
  pointer-events: none;
}

.particles {
  position: absolute;
  inset: 0;
  overflow: hidden;
  pointer-events: none;
}

.particle {
  position: absolute;
  width: 3px;
  height: 3px;
  background: var(--color-accent);
  border-radius: 50%;
  opacity: 0.3;
  animation: float 8s infinite;
}

.particle:nth-child(1) { top: 15%; left: 10%; animation-delay: 0s; width: 4px; height: 4px; }
.particle:nth-child(2) { top: 25%; right: 15%; animation-delay: 1s; }
.particle:nth-child(3) { top: 60%; left: 5%; animation-delay: 2s; width: 2px; height: 2px; }
.particle:nth-child(4) { top: 70%; right: 10%; animation-delay: 0.5s; }
.particle:nth-child(5) { top: 40%; left: 20%; animation-delay: 3s; }
.particle:nth-child(6) { top: 80%; left: 30%; animation-delay: 1.5s; }
.particle:nth-child(7) { bottom: 20%; right: 25%; animation-delay: 2.5s; width: 2px; height: 2px; }
.particle:nth-child(8) { top: 10%; right: 35%; animation-delay: 4s; }

.login-card {
  position: relative;
  width: 400px;
  padding: 48px 40px 40px;
  background: color-mix(in srgb, var(--color-bg-secondary) 80%, transparent);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-xl, 16px);
  animation: fadeInUp 0.5s ease-out;
  z-index: 1;
}

.login-logo {
  text-align: center;
  margin-bottom: 40px;
}

.login-logo .logo-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  border-radius: var(--radius-lg, 12px);
  font-size: 22px;
  color: #fff;
  margin-bottom: 12px;
}

.login-logo h1 {
  font-size: 20px;
  font-weight: 600;
  color: var(--color-text-primary);
  letter-spacing: -0.3px;
}

.login-logo p {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-top: 4px;
}

.login-form {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.login-form :deep(.el-input__wrapper) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  box-shadow: none !important;
  padding: 0 14px;
  transition: border-color 0.2s;
}

.login-form :deep(.el-input__wrapper:hover) {
  border-color: var(--color-border-hover);
}

.login-form :deep(.el-input__wrapper.is-focus) {
  border-color: var(--color-accent);
}

.login-form :deep(.el-input__inner) {
  height: 44px;
  color: var(--color-text-primary);
  font-size: 14px;
}

.login-form :deep(.el-input__inner::placeholder) {
  color: var(--color-text-tertiary);
}

.login-form :deep(.el-input__prefix-inner) {
  color: var(--color-text-tertiary);
  font-size: 16px;
}

.login-btn {
  width: 100%;
  height: 44px;
  font-size: 14px;
  font-weight: 500;
  border-radius: var(--radius-md, 8px);
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  border: none;
  color: #fff;
  cursor: pointer;
  transition: opacity 0.2s, transform 0.15s;
  letter-spacing: 0.3px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-btn:hover {
  opacity: 0.9;
}

.login-btn:active {
  transform: scale(0.98);
}

.login-btn.is-loading {
  opacity: 0.7;
  pointer-events: none;
}

.login-btn .spin {
  animation: spin 0.8s linear infinite;
  margin-right: 6px;
}

.login-footer {
  text-align: center;
  margin-top: 24px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.login-footer a {
  margin-left: 4px;
  font-weight: 500;
  color: var(--color-accent);
  text-decoration: none;
}

.login-footer a:hover {
  color: var(--color-accent-hover);
}

.login-error {
  text-align: center;
  font-size: 13px;
  color: var(--color-error);
  min-height: 20px;
  animation: fadeInUp 0.2s ease-out;
}

:deep(.register-dialog) {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-xl, 16px);
  border: 1px solid var(--color-border);
}

:deep(.register-dialog .el-dialog__header) {
  padding: 24px 24px 0;
  margin: 0;
}

:deep(.register-dialog .el-dialog__title) {
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 600;
}

:deep(.register-dialog .el-dialog__body) {
  padding: 24px;
}

:deep(.register-dialog .el-dialog__footer) {
  padding: 0 24px 24px;
  border-top: none;
}

:deep(.register-dialog .el-form-item__label) {
  color: var(--color-text-secondary);
  font-size: 13px;
  padding-bottom: 4px;
}

:deep(.register-dialog .el-input__wrapper) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  box-shadow: none !important;
}

:deep(.register-dialog .el-input__wrapper.is-focus) {
  border-color: var(--color-accent);
}

:deep(.register-dialog .el-input__inner) {
  color: var(--color-text-primary);
}

:deep(.register-dialog .el-button--primary) {
  background: var(--color-accent);
  border: none;
}

:deep(.register-dialog .el-button--primary:hover) {
  background: var(--color-accent-hover);
}

:deep(.register-dialog .el-button--default) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  color: var(--color-text-primary);
}

:deep(.register-dialog .el-button--default:hover) {
  border-color: var(--color-border-hover);
}
</style>
