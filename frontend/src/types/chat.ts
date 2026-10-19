export interface SessionVO {
  id: number
  title: string
  kbId: number
  messageCount: number
  createdAt: string
  updatedAt: string
}

export interface MessageVO {
  id: number | null
  sessionId: number
  role: 'USER' | 'AI'
  content: string
  sources: string | null
  tokensUsed: number
  createdAt: string
}

export interface CreateSessionRequest {
  kbId: number
  title?: string
}

export interface AskRequest {
  question: string
}

