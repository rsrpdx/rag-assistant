import type { MessageVO } from '@/types/chat'

const BASE = '/api/v1/chat'

export function askChatStream(
  sessionId: number,
  question: string,
  callbacks: {
    onToken: (token: string) => void
    onDone: (msg: MessageVO) => void
    onError: (err: string) => void
  }
): AbortController {
  const controller = new AbortController()
  const token = localStorage.getItem('token')
  const url = `${BASE}/session/${sessionId}/ask-stream?question=${encodeURIComponent(question)}`

  const timeoutId = setTimeout(() => {
    controller.abort()
    callbacks.onError('请求超时，请重试')
  }, 120000)

  fetch(url, {
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    signal: controller.signal
  })
    .then(async (response) => {
      if (!response.ok) {
        clearTimeout(timeoutId)
        const text = await response.text().catch(() => '')
        callbacks.onError(text || `请求失败 (${response.status})`)
        return
      }

      const body = response.body
      if (!body) {
        clearTimeout(timeoutId)
        callbacks.onError('浏览器不支持流式读取')
        return
      }

      const reader = body.getReader()
      const decoder = new TextDecoder('utf-8')
      let buffer = ''

      try {
        while (true) {
          const { done, value } = await reader.read()
          if (done) {
            if (buffer.trim()) {
              processRemainingBuffer(buffer, callbacks)
            }
            break
          }

          const chunk = decoder.decode(value, { stream: true })
          buffer += chunk

          const doubleNewlineIdx = buffer.lastIndexOf('\n\n')
          if (doubleNewlineIdx >= 0) {
            const complete = buffer.substring(0, doubleNewlineIdx + 2)
            buffer = buffer.substring(doubleNewlineIdx + 2)
            processSSEChunk(complete, callbacks)
          }
        }
      } catch (readErr: any) {
        if (readErr.name !== 'AbortError') {
          console.error('[SSE] Read error:', readErr)
          callbacks.onError(readErr.message || '流读取失败')
        }
      } finally {
        clearTimeout(timeoutId)
        try { reader.cancel() } catch (_) { /* ignore */ }
      }
    })
    .catch((err: any) => {
      clearTimeout(timeoutId)
      if (err.name === 'AbortError') return
      console.error('[SSE] Fetch error:', err)
      callbacks.onError(err.message || '网络错误')
    })

  return controller
}

function processSSEChunk(chunk: string, callbacks: {
  onToken: (token: string) => void
  onDone: (msg: MessageVO) => void
  onError: (err: string) => void
}) {
  const messages = chunk.split('\n\n')
  for (const message of messages) {
    if (!message.trim()) continue
    processSSEMessage(message, callbacks)
  }
}

function processSSEMessage(message: string, callbacks: {
  onToken: (token: string) => void
  onDone: (msg: MessageVO) => void
  onError: (err: string) => void
}) {
  const lines = message.split('\n')
  let eventType = ''
  const dataLines: string[] = []

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) continue

    if (line.startsWith('event:')) {
      eventType = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  const data = dataLines.join('\n')
  if (!data && eventType !== 'done') return

  switch (eventType) {
    case 'done':
      try {
        const parsed = JSON.parse(data)
        callbacks.onDone({
          id: null,
          sessionId: 0,
          role: 'AI',
          content: '',
          sources: parsed.sources || '[]',
          tokensUsed: 0,
          createdAt: new Date().toISOString()
        } as MessageVO)
      } catch {
        callbacks.onDone({
          id: null, sessionId: 0, role: 'AI', content: '',
          sources: '[]', tokensUsed: 0, createdAt: new Date().toISOString()
        } as MessageVO)
      }
      break
    case 'error':
      callbacks.onError(data)
      break
    default:
      if (data) {
        callbacks.onToken(data)
      }
      break
  }
}

function processRemainingBuffer(text: string, callbacks: {
  onToken: (token: string) => void
  onDone: (msg: MessageVO) => void
  onError: (err: string) => void
}) {
  const lines = text.split('\n')
  let eventType = ''
  let dataLines: string[] = []

  for (const rawLine of lines) {
    const line = rawLine.trim()
    if (!line) continue
    if (line.startsWith('event:')) {
      eventType = line.slice(6).trim()
    } else if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  const data = dataLines.join('\n')
  if (eventType === 'done') {
    try {
      const parsed = JSON.parse(data)
      callbacks.onDone({
        id: null, sessionId: 0, role: 'AI', content: '',
        sources: parsed.sources || '[]', tokensUsed: 0,
        createdAt: new Date().toISOString()
      } as MessageVO)
    } catch {
      callbacks.onDone({
        id: null, sessionId: 0, role: 'AI', content: '',
        sources: '[]', tokensUsed: 0, createdAt: new Date().toISOString()
      } as MessageVO)
    }
  } else if (eventType === 'error') {
    callbacks.onError(data)
  } else if (data) {
    callbacks.onToken(data)
  }
}

export async function askChat(sessionId: number, question: string): Promise<MessageVO> {
  const res = await fetch(`${BASE}/session/${sessionId}/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(localStorage.getItem('token') ? { Authorization: `Bearer ${localStorage.getItem('token')}` } : {})
    },
    body: JSON.stringify({ question })
  })
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `请求失败 (${res.status})`)
  }
  const json = await res.json()
  if (json.code === 200) return json.data
  throw new Error(json.message || '请求失败')
}
