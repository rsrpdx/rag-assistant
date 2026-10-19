import request from './request'
import type { SessionVO, MessageVO, CreateSessionRequest } from '@/types/chat'
import type { KnowledgeBaseVO } from '@/types/kb'

export function listSessionsApi(kbId: number): Promise<SessionVO[]> {
  return request.get('/chat/session', { params: { kbId } })
}

export function createSessionApi(data: CreateSessionRequest): Promise<SessionVO> {
  return request.post('/chat/session', data)
}

export function deleteSessionApi(id: number): Promise<null> {
  return request.delete(`/chat/session/${id}`)
}

export function getMessagesApi(sessionId: number): Promise<MessageVO[]> {
  return request.get(`/chat/session/${sessionId}/messages`)
}

export function listKnowledgeBasesApi(): Promise<KnowledgeBaseVO[]> {
  return request.get('/kb').then((res: any) => (res.records || []) as KnowledgeBaseVO[])
}
