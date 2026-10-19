import { defineStore } from 'pinia'
import { ref } from 'vue'
import { loginApi, registerApi, logoutApi } from '@/api/auth'
import type { LoginRequest, RegisterRequest, UserInfo } from '@/types'
import router from '@/router'

export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem('token') || '')
  const userInfo = ref<UserInfo | null>(
    JSON.parse(localStorage.getItem('userInfo') || 'null')
  )

  const isLoggedIn = ref(!!token.value)

  async function login(data: LoginRequest) {
    const res = await loginApi(data)
    token.value = res.token
    userInfo.value = res.userInfo
    localStorage.setItem('token', res.token)
    localStorage.setItem('userInfo', JSON.stringify(res.userInfo))
    isLoggedIn.value = true
    const redirect = router.currentRoute.value.query.redirect as string
    router.push(redirect || '/dashboard')
  }

  async function register(data: RegisterRequest) {
    await registerApi(data)
  }

  async function logout() {
    try {
      await logoutApi()
    } finally {
      token.value = ''
      userInfo.value = null
      isLoggedIn.value = false
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
      router.push('/login')
    }
  }

  return { token, userInfo, isLoggedIn, login, register, logout }
})
