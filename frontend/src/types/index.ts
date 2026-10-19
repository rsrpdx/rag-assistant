export interface LoginRequest {
  username: string
  password: string
}

export interface RegisterRequest {
  username: string
  password: string
  email?: string
}

export interface UserInfo {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  role: string
}

export interface LoginResponse {
  token: string
  userInfo: UserInfo
}

export interface ApiResponse<T = any> {
  code: number
  message: string
  data: T
  timestamp: number
}
