import request from './request'
import type { KnowledgeBaseVO, CreateKbRequest, DocumentVO, DocumentDetailVO, CategoryVO } from '@/types/kb'
import axios from 'axios'

export function listKbApi(page = 1, size = 20, category = ''): Promise<{ records: KnowledgeBaseVO[]; total: number }> {
  return request.get('/kb', { params: { page, size, category: category || undefined } })
}

export function fetchCategoriesApi(): Promise<CategoryVO[]> {
  return request.get('/kb/categories')
}

export function createKbApi(data: CreateKbRequest): Promise<KnowledgeBaseVO> {
  return request.post('/kb', data)
}

export function deleteKbApi(id: number): Promise<null> {
  return request.delete(`/kb/${id}`)
}

export function updateKbApi(id: number, data: { name?: string; description?: string; category?: string }): Promise<null> {
  return request.put(`/kb/${id}`, data)
}

export function getKbDetailApi(id: number): Promise<KnowledgeBaseVO> {
  return request.get(`/kb/${id}`)
}

export function getKbStatsApi(id: number): Promise<any> {
  return request.get(`/kb/${id}/stats`)
}

export function listUserDocsApi(page = 1, size = 20): Promise<{ records: DocumentVO[]; total: number }> {
  return request.get('/doc/user', { params: { page, size } })
}

export function listDocsApi(kbId: number, page = 1, size = 20): Promise<{ records: DocumentVO[]; total: number }> {
  return request.get(`/doc/${kbId}`, { params: { page, size } })
}

export function uploadDocApi(kbId: number, file: File): Promise<DocumentVO> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('kbId', String(kbId))
  return request.post('/doc/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 120000
  })
}

export function uploadDocWithProgress(
  kbId: number,
  file: File,
  onProgress: (percent: number) => void,
  signal?: AbortSignal
): Promise<DocumentVO> {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('kbId', String(kbId))

  const token = localStorage.getItem('token')
  return new Promise((resolve, reject) => {
    axios.post('/api/v1/doc/upload', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
        ...(token ? { Authorization: `Bearer ${token}` } : {})
      },
      timeout: 120000,
      signal,
      onUploadProgress: (progressEvent) => {
        if (progressEvent.total) {
          const percent = Math.round((progressEvent.loaded * 100) / progressEvent.total)
          onProgress(percent)
        }
      }
    })
    .then(res => {
      const { code, data, message } = res.data
      if (code === 200) {
        resolve(data)
      } else {
        reject(new Error(message || '上传失败'))
      }
    })
    .catch(err => {
      if (axios.isCancel(err)) {
        reject(new Error('已取消'))
      } else {
        reject(err)
      }
    })
  })
}

export function deleteDocApi(id: number): Promise<null> {
  return request.delete(`/doc/${id}`)
}

export function getDocDetailApi(id: number): Promise<DocumentDetailVO> {
  return request.get(`/doc/detail/${id}`)
}

export function retryDocApi(id: number): Promise<null> {
  return request.post(`/doc/${id}/retry`)
}
