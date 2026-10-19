<template>
  <div class="chat-page">
    <!-- Left Sidebar -->
    <aside class="chat-sidebar">
      <div class="sidebar-header">
        <div class="sidebar-logo">
          <router-link to="/dashboard" class="logo-link">
            <div class="logo-icon">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
              </svg>
            </div>
            <span>RAG</span>
          </router-link>
        </div>
      </div>

      <div class="sidebar-kb">
        <el-select
          v-model="currentKbId"
          placeholder="选择知识库"
          size="small"
          class="kb-selector"
          @change="onKbChange"
        >
          <el-option
            v-for="kb in kbList"
            :key="kb.id"
            :label="kb.name"
            :disabled="kb.documentCount === 0"
            :value="kb.id"
          >
            <span>{{ kb.name }}</span>
            <span class="kb-option-count">{{ kb.documentCount > 0 ? kb.documentCount + '个文档' : '空' }}</span>
          </el-option>
        </el-select>
      </div>

      <div class="sidebar-new">
        <button class="new-chat-btn" @click="createNewSession">
          <el-icon :size="14"><Plus /></el-icon>
          <span>新对话</span>
        </button>
      </div>

      <div class="sidebar-sessions" ref="sessionListRef">
        <div class="sessions-header">
          <span class="sessions-header-title">对话列表</span>
          <span v-if="sessions.length > 0" class="sessions-header-count">{{ sessions.length }}</span>
        </div>
        <TransitionGroup name="session-list" tag="div" class="session-list-inner">
          <div
            v-for="s in sessions"
            :key="s.id"
            class="session-item"
            :class="{ active: currentSessionId === s.id }"
            @click="switchSession(s.id)"
          >
            <div class="session-indicator" />
            <el-icon :size="15" class="session-icon"><ChatDotSquare /></el-icon>
            <div class="session-info">
              <span class="session-title">{{ s.title || '新对话' }}</span>
              <span class="session-time">{{ formatTime(s.updatedAt) }}</span>
            </div>
            <button
              class="session-delete"
              @click.stop="deleteSession(s.id)"
              title="删除对话"
            >
              <el-icon :size="12"><Close /></el-icon>
            </button>
          </div>
        </TransitionGroup>
        <div v-if="sessions.length === 0" class="session-empty">
          <el-icon :size="28"><ChatDotSquare /></el-icon>
          <p>暂无对话记录</p>
          <p class="session-empty-hint">选择知识库后开始提问</p>
        </div>
      </div>
    </aside>

    <!-- Right Main Area -->
    <main class="chat-main">
      <!-- Messages -->
      <div class="messages-area" ref="messagesRef" @scroll="onScroll">
        <div class="messages-inner">
          <div v-if="messages.length === 0 && !isLoading" class="welcome-screen">
            <div class="welcome-icon">
              <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
              </svg>
            </div>
            <h2 class="welcome-title">开始提问</h2>
            <p class="welcome-desc">AI将基于你的知识库内容回答</p>
          </div>

          <div
            v-for="(msg, mi) in messages"
            :key="msg.id || mi"
            class="message-row"
            :class="{ 'is-user': msg.role === 'USER', 'is-ai': msg.role === 'AI' }"
          >
            <div class="message-avatar">
              <img v-if="msg.role === 'USER' && userAvatarUrl" :src="userAvatarUrl" class="chat-avatar-img" />
              <span v-else-if="msg.role === 'USER'">{{ userAvatar }}</span>
              <svg v-else width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/>
              </svg>
            </div>
            <div class="message-bubble">
              <div class="message-content" v-html="renderMarkdown(msg.content)" />
              <div v-if="msg.role === 'AI' && msg.sources" class="message-sources">
                <div
                  v-for="(src, si) in parseSources(msg.sources)"
                  :key="si"
                  class="source-chip"
                  @click="toggleSource(mi, si)"
                >
                  <span class="source-label">来源 {{ si + 1 }}</span>
                  <span class="source-score">{{ (src.score * 100).toFixed(0) }}%</span>
                </div>
              </div>
            </div>
          </div>

          <!-- AI Response: Thinking / Streaming -->
          <div v-if="isStreaming" class="message-row is-ai">
            <div class="message-avatar">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><circle cx="12" cy="12" r="10"/><path d="M12 16v-4"/><path d="M12 8h.01"/></svg>
            </div>
            <div class="message-bubble">
              <!-- Thinking animation (before first token) -->
              <div v-if="isThinking" class="thinking-indicator">
                <span class="thinking-dot dot-1"></span>
                <span class="thinking-dot dot-2"></span>
                <span class="thinking-dot dot-3"></span>
                <span class="thinking-label">\u601d\u8003\u4e2d...</span>
              </div>
              <!-- Streaming content (after first token) -->
              <div v-else class="streaming-wrapper">
                <div class="message-content" v-html="streamingContentHtml" />
                <span class="typing-cursor" v-if="!streamingDone">|</span>
              </div>
            </div>
          </div>

          <div ref="scrollAnchor" />
        </div>
      </div>

      <!-- Input Area -->
      <div class="input-area">
        <div class="input-wrapper">
          <textarea
            ref="textareaRef"
            v-model="inputText"
            class="chat-input"
            placeholder="输入问题... Enter发送 / Shift+Enter换行"
            rows="1"
            @keydown="onKeydown"
            @input="autoResize"
            :disabled="isStreaming"
          />
          <button
            v-if="!isStreaming"
            class="send-btn"
            :disabled="!inputText.trim() || !currentSessionId"
            @click="sendMessage"
          >
            <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/>
            </svg>
          </button>
          <button v-else class="send-btn is-stopping" @click="stopStreaming">
            <span class="send-stop">停止</span>
          </button>
        </div>
        <p class="input-hint">AI生成内容仅供参考，请以知识库原文为准</p>
      </div>
    </main>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick, watch } from 'vue'
import { marked } from 'marked'
import { useRoute } from 'vue-router'
import { Plus, ChatDotSquare, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { listSessionsApi, createSessionApi, deleteSessionApi, getMessagesApi, listKnowledgeBasesApi } from '@/api/chat'
import { askChatStream } from '@/api/chat-sse'
import type { SessionVO, MessageVO } from '@/types/chat'
import type { KnowledgeBaseVO } from '@/types/kb'

const authStore = useAuthStore()
const route = useRoute()

const currentKbId = ref<number | ''>('')
const kbList = ref<KnowledgeBaseVO[]>([])
const sessions = ref<SessionVO[]>([])
const currentSessionId = ref<number | null>(null)
const messages = ref<MessageVO[]>([])
const inputText = ref('')
const isLoading = ref(false)
const isStreaming = ref(false)
const streamingDone = ref(false)
const isThinking = ref(false)
const streamingContent = ref('')
const expandedSource = ref<{ msgIdx: number; srcIdx: number } | null>(null)
const messagesRef = ref<HTMLDivElement>()
const scrollAnchor = ref<HTMLDivElement>()
const textareaRef = ref<HTMLTextAreaElement>()
const sessionListRef = ref<HTMLDivElement>()

const userAvatarUrl = computed(() => authStore.userInfo?.avatar || '')

const userAvatar = computed(() => {
  const name = authStore.userInfo?.nickname || authStore.userInfo?.username || 'U'
  return name.charAt(0).toUpperCase()
})

const streamingContentHtml = computed(() => renderMarkdown(streamingContent.value))

onMounted(async () => {
  await loadKbList()
  // Handle sessionId from query param (e.g., coming from dashboard recent chats)
  const targetSessionId = route.query.sessionId
  if (targetSessionId) {
    const sid = Number(targetSessionId)
    if (!isNaN(sid)) {
      currentSessionId.value = sid
      await loadMessages()
    }
  }
  // Handle question from query param (coming from dashboard search)
  const targetQuestion = route.query.question
  if (targetQuestion && typeof targetQuestion === 'string') {
    inputText.value = targetQuestion
    await nextTick()
    if (!currentSessionId.value && currentKbId.value) {
      await createNewSession()
    }
    if (currentSessionId.value) {
      await nextTick()
      sendMessage()
    }
  }
})

watch(currentKbId, async (val) => {
  if (val) {
    await loadSessions()
  }
})

async function loadKbList() {
  try {
    const list = await listKnowledgeBasesApi()
    kbList.value = list
    if (list.length > 0) {
      currentKbId.value = list[0].id
    }
  } catch {
    // ignore
  }
}

async function loadSessions() {
  if (!currentKbId.value) return
  sessions.value = await listSessionsApi(currentKbId.value as number)
}

function onKbChange() {
  // Reset session when KB changes
  currentSessionId.value = null
  messages.value = []
  loadSessions()
}

function formatTime(dateStr: string): string {
  if (!dateStr) return ''
  const d = new Date(dateStr)
  const now = new Date()
  const diff = now.getTime() - d.getTime()
  const mins = Math.floor(diff / 60000)
  const hours = Math.floor(diff / 3600000)
  const days = Math.floor(diff / 86400000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return mins + '分钟前'
  if (hours < 24) return hours + '小时前'
  if (days === 1) return '昨天'
  if (days < 7) return days + '天前'
  return d.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

async function createNewSession() {
  if (!currentKbId.value) {
    ElMessage.warning('请先选择知识库')
    return
  }
  try {
    const session = await createSessionApi({ kbId: currentKbId.value as number })
    sessions.value.unshift(session)
    currentSessionId.value = session.id
    messages.value = []
  } catch {
    // ignore
  }
}

async function deleteSession(id: number) {
  try {
    await ElMessageBox.confirm('确定删除该对话？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteSessionApi(id)
    sessions.value = sessions.value.filter(s => s.id !== id)
    if (currentSessionId.value === id) {
      currentSessionId.value = null
      messages.value = []
    }
  } catch {
    // cancelled
  }
}

async function switchSession(id: number) {
  if (currentSessionId.value === id) return
  currentSessionId.value = id
  await loadMessages()
  await nextTick()
  if (textareaRef.value) textareaRef.value.focus()
}

async function loadMessages() {
  if (!currentSessionId.value) return
  isLoading.value = true
  try {
    messages.value = await getMessagesApi(currentSessionId.value)
    await nextTick()
    scrollToBottom()
  } catch (e) {
    console.error('加载消息失败:', e)
    ElMessage.error('加载对话记录失败')
  } finally {
    isLoading.value = false
  }
}

function scrollToBottom() {
  nextTick(() => {
    scrollAnchor.value?.scrollIntoView({ behavior: 'smooth' })
  })
}

function autoResize() {
  const el = textareaRef.value
  if (!el) return
  el.style.height = 'auto'
  el.style.height = Math.min(el.scrollHeight, 160) + 'px'
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function onScroll() {
  // could implement infinite scroll here
}

let abortController: AbortController | null = null

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || !currentSessionId.value || isStreaming.value) return

  inputText.value = ''
  if (textareaRef.value) {
    textareaRef.value.style.height = 'auto'
  }

  const userMsg: MessageVO = {
    id: Date.now(),
    sessionId: currentSessionId.value,
    role: 'USER',
    content: text,
    sources: null,
    tokensUsed: 0,
    createdAt: new Date().toISOString()
  }
  messages.value.push(userMsg)

  isStreaming.value = true
  streamingDone.value = false
  streamingContent.value = ''
  isThinking.value = true

  scrollToBottom()

  abortController = askChatStream(currentSessionId.value, text, {
    onToken(token: string) {
      isThinking.value = false
      streamingContent.value += token
      scrollToBottom()
    },
    onDone(msg: MessageVO) {
      streamingDone.value = true
      const aiMsg: MessageVO = {
        ...msg,
        content: streamingContent.value,
        id: Date.now(),
        createdAt: new Date().toISOString()
      }
      messages.value.push(aiMsg)
      isThinking.value = false
      isStreaming.value = false
      streamingContent.value = ''
      scrollToBottom()
      loadSessions()
    },
    onError(err: string) {
      ElMessage.error(err || '请求失败')
      isThinking.value = false
      isStreaming.value = false
      streamingContent.value = ''
    }
  })
}

function stopStreaming() {
  if (abortController) {
    abortController.abort()
    abortController = null
  }
  isStreaming.value = false
  isThinking.value = false
}

function parseSources(sourcesJson: string): any[] {
  try {
    return JSON.parse(sourcesJson) || []
  } catch {
    return []
  }
}

function toggleSource(msgIdx: number, srcIdx: number) {
  const cur = expandedSource.value
  if (cur && cur.msgIdx === msgIdx && cur.srcIdx === srcIdx) {
    expandedSource.value = null
  } else {
    expandedSource.value = { msgIdx, srcIdx }
  }
}

function renderMarkdown(text: string): string {
  if (!text) return ''
  try {
    return marked.parse(text) as string
  } catch {
    return text.replace(/</g, '&lt;').replace(/>/g, '&gt;')
  }
}
</script>

<style scoped>
.chat-page {
  display: flex;
  height: 100vh;
  background: var(--color-bg-primary);
  overflow: hidden;
}

/* ---------- Sidebar ---------- */
.chat-sidebar {
  width: 260px;
  display: flex;
  flex-direction: column;
  background: var(--color-bg-secondary);
  border-right: 1px solid var(--color-border);
  flex-shrink: 0;
}

.sidebar-header {
  padding: 16px 16px 0;
}

.logo-link {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
  color: var(--color-text-primary);
  font-size: 15px;
  font-weight: 700;
}

.logo-link .logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  border-radius: 6px;
  color: #fff;
}

.sidebar-kb {
  padding: 12px 16px;
}

.kb-option-count {
  float: right;
  font-size: 11px;
  color: var(--color-text-tertiary);
}
.kb-selector {
  width: 100%;
}

.sidebar-kb :deep(.el-input__wrapper) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  box-shadow: none !important;
  border-radius: var(--radius-md, 8px);
}

.sidebar-kb :deep(.el-input__inner) {
  color: var(--color-text-primary);
  font-size: 12px;
  height: 32px;
}

.sidebar-new {
  padding: 0 16px 12px;
}

.new-chat-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  height: 34px;
  background: var(--color-bg-tertiary);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md, 8px);
  color: var(--color-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: all 0.2s;
}

.new-chat-btn:hover {
  border-color: var(--color-accent);
  color: var(--color-accent);
  background: rgba(99, 102, 241, 0.08);
}

.sidebar-sessions {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
  display: flex;
  flex-direction: column;
}

.sessions-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px 10px;
  flex-shrink: 0;
}

.sessions-header-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.sessions-header-count {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  background: var(--color-bg-tertiary);
  padding: 1px 7px;
  border-radius: 10px;
  min-width: 20px;
  text-align: center;
}

.session-list-inner {
  flex: 1;
}

.session-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  transition: background 0.15s, padding-left 0.2s;
  margin-bottom: 1px;
  position: relative;
  overflow: hidden;
}

.session-item:hover {
  background: var(--color-bg-tertiary);
}

.session-item.active {
  background: rgba(99, 102, 241, 0.12);
}

.session-item.active .session-icon {
  color: var(--color-accent);
}

.session-indicator {
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 0;
  border-radius: 0 3px 3px 0;
  background: var(--color-accent);
  transition: height 0.2s ease;
}

.session-item.active .session-indicator {
  height: 20px;
}

.session-icon {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
  transition: color 0.2s;
}

.session-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-title {
  font-size: 13px;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  line-height: 1.3;
}

.session-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
}

.session-delete {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  border-radius: 5px;
  cursor: pointer;
  flex-shrink: 0;
  opacity: 0;
  transition: opacity 0.15s, background 0.15s, color 0.15s;
}

.session-item:hover .session-delete {
  opacity: 1;
}

.session-delete:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error);
}

.session-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 40px 12px;
}

.session-empty p {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin: 0;
}

.session-empty-hint {
  font-size: 11px !important;
  color: var(--color-text-tertiary) !important;
}

/* Session list transitions */
.session-list-enter-active,
.session-list-leave-active {
  transition: all 0.25s ease;
}

.session-list-enter-from {
  opacity: 0;
  transform: translateX(-12px);
}

.session-list-leave-to {
  opacity: 0;
  transform: translateX(-16px);
}

.session-list-move {
  transition: transform 0.25s ease;
}
/* ---------- Main ---------- */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* Messages */
.messages-area {
  flex: 1;
  overflow-y: auto;
  padding: 0;
}

.messages-inner {
  max-width: 760px;
  margin: 0 auto;
  padding: 32px 24px 24px;
}

/* Welcome */
.welcome-screen {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  text-align: center;
}

.welcome-icon {
  color: var(--color-accent);
  opacity: 0.4;
  margin-bottom: 16px;
}

.welcome-title {
  font-size: 22px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 6px;
}

.welcome-desc {
  font-size: 14px;
  color: var(--color-text-secondary);
}

/* Message Row */
.message-row {
  display: flex;
  gap: 12px;
  margin-bottom: 24px;
  animation: fadeInUp 0.25s ease-out;
}

.message-avatar {
  width: 30px;
  height: 30px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  font-size: 12px;
  font-weight: 600;
  margin-top: 4px;
}

.chat-avatar-img {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.is-user {
  flex-direction: row-reverse;
}

.is-user .message-avatar {
  background: var(--color-accent);
  color: #fff;
}

.is-ai .message-avatar {
  background: var(--color-bg-tertiary);
  color: var(--color-accent);
}

.message-bubble {
  flex: 1;
  min-width: 0;
}

.is-user .message-bubble {
  text-align: right;
}

.is-user .message-content {
  display: inline-block;
  background: var(--color-accent);
  color: #fff;
  padding: 10px 16px;
  border-radius: 12px 4px 12px 12px;
  font-size: 14px;
  line-height: 1.6;
  text-align: left;
  max-width: 85%;
}

.is-ai .message-content {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  padding: 12px 16px;
  border-radius: 4px 12px 12px 12px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-primary);
  overflow-x: auto;
}

.is-ai .message-content :deep(h1),
.is-ai .message-content :deep(h2),
.is-ai .message-content :deep(h3) {
  margin: 12px 0 6px;
  font-weight: 600;
}

.is-ai .message-content :deep(h1) { font-size: 16px; }
.is-ai .message-content :deep(h2) { font-size: 15px; }
.is-ai .message-content :deep(h3) { font-size: 14px; }

.is-ai .message-content :deep(p) {
  margin: 6px 0;
}

.is-ai .message-content :deep(code) {
  background: var(--color-bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: var(--font-mono);
}

.is-ai .message-content :deep(pre) {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 14px;
  overflow-x: auto;
  margin: 10px 0;
}

.is-ai .message-content :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 13px;
  line-height: 1.5;
}

.is-ai .message-content :deep(li) {
  margin: 3px 0 3px 18px;
}

.is-ai .message-content :deep(strong) {
  font-weight: 600;
}

/* Sources */
.message-sources {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

.source-chip {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: 12px;
  font-size: 11px;
  cursor: pointer;
  transition: all 0.15s;
}

.source-chip:hover {
  border-color: var(--color-accent);
  background: rgba(99, 102, 241, 0.08);
}

.source-label {
  color: var(--color-text-secondary);
}

.source-score {
  color: var(--color-accent);
  font-weight: 500;
}

/* Thinking animation */
.thinking-indicator {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 8px 4px;
}

.thinking-dot {
  display: inline-block;
  width: 6px;
  height: 6px;
  background: var(--color-accent);
  border-radius: 50%;
  opacity: 0.4;
}

.thinking-dot.dot-1 {
  animation: thinkPulse 1.4s ease-in-out infinite;
}

.thinking-dot.dot-2 {
  animation: thinkPulse 1.4s ease-in-out 0.2s infinite;
}

.thinking-dot.dot-3 {
  animation: thinkPulse 1.4s ease-in-out 0.4s infinite;
}

@keyframes thinkPulse {
  0%, 80% { opacity: 0.4; transform: scale(0.8); }
  40% { opacity: 1; transform: scale(1.1); }
}

.thinking-label {
  font-size: 13px;
  color: var(--color-text-tertiary);
  margin-left: 6px;
}

.streaming-wrapper {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  padding: 12px 16px;
  border-radius: 4px 12px 12px 12px;
  font-size: 14px;
  line-height: 1.7;
  color: var(--color-text-primary);
  overflow-x: auto;
}

.streaming-wrapper .message-content {
  background: none;
  border: none;
  padding: 0;
  border-radius: 0;
}

.streaming-wrapper .message-content :deep(h1),
.streaming-wrapper .message-content :deep(h2),
.streaming-wrapper .message-content :deep(h3) {
  margin: 12px 0 6px;
  font-weight: 600;
}

.streaming-wrapper .message-content :deep(h1) { font-size: 16px; }
.streaming-wrapper .message-content :deep(h2) { font-size: 15px; }
.streaming-wrapper .message-content :deep(h3) { font-size: 14px; }

.streaming-wrapper .message-content :deep(p) {
  margin: 6px 0;
}

.streaming-wrapper .message-content :deep(code) {
  background: var(--color-bg-tertiary);
  padding: 2px 6px;
  border-radius: 4px;
  font-size: 13px;
  font-family: var(--font-mono);
}

.streaming-wrapper .message-content :deep(pre) {
  background: var(--color-bg-primary);
  border: 1px solid var(--color-border);
  border-radius: 8px;
  padding: 14px;
  overflow-x: auto;
  margin: 10px 0;
}

.streaming-wrapper .message-content :deep(pre code) {
  background: none;
  padding: 0;
  font-size: 13px;
  line-height: 1.5;
}

.streaming-wrapper .message-content :deep(li) {
  margin: 3px 0 3px 18px;
}

.streaming-wrapper .message-content :deep(strong) {
  font-weight: 600;
}

/* Typing cursor */
.typing-cursor {
  display: inline-block;
  animation: blink 0.8s step-end infinite;
  color: var(--color-accent);
  font-weight: 700;
  margin-left: 2px;
}

@keyframes blink {
  50% { opacity: 0; }
}

/* Input */
.input-area {
  padding: 16px 24px 24px;
  border-top: 1px solid var(--color-border);
}

.input-wrapper {
  display: flex;
  align-items: flex-end;
  gap: 8px;
  max-width: 760px;
  margin: 0 auto;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  padding: 8px 8px 8px 16px;
  transition: border-color 0.2s;
}

.input-wrapper:focus-within {
  border-color: var(--color-accent);
}

.chat-input {
  flex: 1;
  border: none;
  background: transparent;
  color: var(--color-text-primary);
  font-size: 14px;
  font-family: var(--font-sans);
  line-height: 1.5;
  resize: none;
  outline: none;
  max-height: 160px;
  min-height: 24px;
}

.chat-input::placeholder {
  color: var(--color-text-tertiary);
}

.send-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border: none;
  background: var(--color-accent);
  color: #fff;
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  transition: opacity 0.2s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  opacity: 0.85;
}

.send-btn:disabled {
  opacity: 0.3;
  cursor: not-allowed;
}

.is-stopping {
  background: var(--color-error) !important;
}

.is-stopping:hover {
  opacity: 0.85;
}

.send-stop {
  font-size: 12px;
  font-weight: 500;
}

.input-hint {
  max-width: 760px;
  margin: 6px auto 0;
  font-size: 11px;
  color: var(--color-text-tertiary);
  text-align: center;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(8px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* Scrollbar */
.messages-area::-webkit-scrollbar {
  width: 6px;
}

.messages-area::-webkit-scrollbar-track {
  background: transparent;
}

.messages-area::-webkit-scrollbar-thumb {
  background: #3f3f46;
  border-radius: 3px;
}

.sidebar-sessions::-webkit-scrollbar {
  width: 4px;
}

.sidebar-sessions::-webkit-scrollbar-thumb {
  background: var(--color-border-hover);
  border-radius: 2px;
}
</style>
