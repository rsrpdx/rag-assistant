import axios from 'axios'
import type { AxiosInstance, AxiosResponse, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import type { ApiResponse } from '@/types'

const request: AxiosInstance = axios.create({
  baseURL: '/api/v1',
  timeout: 30000,
  headers: {
    'Content-Type': 'application/json'
  }
})

request.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    const token = localStorage.getItem('token')
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

request.interceptors.response.use(
  (response: AxiosResponse<ApiResponse>) => {
    const { code, message, data } = response.data
    if (code === 200) {
      return data as any
    }
    ElMessage.error(message || '请求失败')
    return Promise.reject(new Error(message))
  },
  (error) => {
    if (error.response) {
      const { status, data } = error.response
      if (status === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('userInfo')
        router.push({ path: '/login', query: { redirect: router.currentRoute.value.fullPath } })
      } else if (status >= 500) {
        ElMessage.error(data?.message || '服务器错误，请稍后重试')
      } else if (status === 403) {
        ElMessage.error('权限不足，无法执行此操作')
      } else if (status === 404) {
        ElMessage.error(data?.message || '请求的资源不存在')
      } else {
        ElMessage.error(data?.message || `请求错误 (${status})`)
      }
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请检查网络后重试')
    } else if (error.code === 'ERR_NETWORK' || !navigator.onLine) {
      ElMessage.error('网络连接失败，请检查网络后重试')
    } else {
      ElMessage.error('网络连接失败，请检查网络后重试')
    }
    return Promise.reject(error)
  }
)

export default request
