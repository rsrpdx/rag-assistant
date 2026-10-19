import request from './request'
import type { LoginRequest, RegisterRequest, LoginResponse } from '@/types'

export function loginApi(data: LoginRequest): Promise<LoginResponse> {
  return request.post('/auth/login', data)
}

export function registerApi(data: RegisterRequest): Promise<null> {
  return request.post('/auth/register', data)
}

export function logoutApi(): Promise<null> {
  return request.post('/auth/logout')
}
