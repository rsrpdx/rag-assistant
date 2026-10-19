import request from './request'

export interface UserProfileVO {
  id: number
  username: string
  nickname: string
  email: string
  avatar: string
  phone: string
  role: string
  createdAt: string
}

export interface UpdateProfileRequest {
  nickname?: string
  email?: string
  phone?: string
}

export interface UpdatePasswordRequest {
  oldPassword: string
  newPassword: string
}

export function getProfileApi(): Promise<UserProfileVO> {
  return request.get('/user/profile')
}

export function updateProfileApi(data: UpdateProfileRequest): Promise<null> {
  return request.put('/user/profile', data)
}

export function updatePasswordApi(data: UpdatePasswordRequest): Promise<null> {
  return request.put('/user/password', data)
}

export function uploadAvatarApi(file: File): Promise<string> {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/user/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 30000
  })
}
