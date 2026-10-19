<template>
  <div class="dashboard">
    <div class="dashboard-inner">
      <!-- Hero Search -->
      <section class="hero-section">
        <h1 class="hero-title">AI 智能问答</h1>
        <p class="hero-subtitle">向 AI 提问，从你的知识库中获取精准答案</p>
        <div class="hero-search" >
          <el-icon :size="18" class="search-icon"><Search /></el-icon>
          <input v-model="heroQuestion" class="hero-search-input" placeholder="直接输入问题，AI 将基于知识库为你解答..." @keyup.enter="goAskQuestion" @click.stop />
          <button class="hero-search-btn" @click.stop="goAskQuestion" :disabled="!heroQuestion.trim()"><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><line x1="22" y1="2" x2="11" y2="13"/><polygon points="22 2 15 22 11 13 2 9 22 2"/></svg></button>
        </div>
      </section>

      <!-- Quick Stats -->
      <section class="stats-section">
        <div
          v-for="stat in statsCards"
          :key="stat.label"
          class="stat-card"
          @click="stat.action"
        >
          <div class="stat-icon" :style="{ background: stat.gradient }">
            <el-icon :size="20"><component :is="stat.icon" /></el-icon>
          </div>
          <div class="stat-info">
            <span class="stat-value">{{ stat.value }}</span>
            <span class="stat-label">{{ stat.label }}</span>
          </div>
          <el-icon class="stat-arrow" :size="14"><ArrowRight /></el-icon>
        </div>
      </section>

      <!-- Content Grid -->
      <section class="content-section">
        <div class="content-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon :size="16"><Message /></el-icon>
              最近对话
            </h3>
            <router-link to="/chat" class="card-more">查看全部</router-link>
          </div>
          <div class="card-body">
            <div v-if="recentChats.length === 0" class="empty-state">
              <el-icon :size="32" color="#52525B"><ChatDotSquare /></el-icon>
              <p>还没有对话记录</p>
              <router-link to="/chat" class="empty-action">开始对话</router-link>
            </div>
            <div
              v-for="chat in recentChats"
              :key="chat.id"
              class="activity-item"
              @click="goToSession(chat.id)"
            >
              <div class="activity-dot chat" />
              <div class="activity-content">
                <span class="activity-title">{{ chat.title }}</span>
                <span class="activity-time">{{ chat.time }}</span>
              </div>
            </div>
          </div>
        </div>

        <div class="content-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon :size="16"><Document /></el-icon>
              最近文档
            </h3>
            <router-link to="/documents" class="card-more">查看全部</router-link>
          </div>
          <div class="card-body">
            <div v-if="recentDocs.length === 0" class="empty-state">
              <el-icon :size="32"><FolderOpened /></el-icon>
              <p>还没有上传文档</p>
              <span class="empty-hint">上传文档到知识库，构建你的AI知识体系</span>
              <router-link to="/documents" class="empty-action">前往文档管理</router-link>
            </div>
            <div
              v-for="doc in recentDocs"
              :key="doc.id"
              class="doc-item"
              @click="goToDoc(doc.id)"
            >
              <div class="doc-icon" :class="doc.fileType">
                <el-icon :size="16"><component :is="doc.icon" /></el-icon>
              </div>
              <div class="doc-info">
                <span class="doc-title">{{ doc.title }}</span>
                <span class="doc-meta">{{ doc.description }} · {{ formatTime(doc.time) }}</span>
              </div>
              <el-icon :size="14" class="doc-arrow"><ArrowRight /></el-icon>
            </div>
          </div>
        </div>

        <div class="content-card">
          <div class="card-header">
            <h3 class="card-title">
              <el-icon :size="16"><Lightning /></el-icon>
              快速开始
            </h3>
          </div>
          <div class="card-body">
            <div class="quick-actions">
              <div class="quick-action-item" @click="goToCreateKb">
                <div class="quick-icon create">
                  <el-icon :size="18"><Plus /></el-icon>
                </div>
                <div class="quick-text">
                  <span class="quick-title">创建知识库</span>
                  <span class="quick-desc">整理你的知识体系</span>
                </div>
              </div>
              <div class="quick-action-item" @click="goToUpload">
                <div class="quick-icon upload">
                  <el-icon :size="18"><Upload /></el-icon>
                </div>
                <div class="quick-text">
                  <span class="quick-title">上传文档</span>
                  <span class="quick-desc">支持PDF格式</span>
                </div>
              </div>
              <div class="quick-action-item" @click="goToChat">
                <div class="quick-icon chat">
                  <el-icon :size="18"><ChatDotSquare /></el-icon>
                </div>
                <div class="quick-text">
                  <span class="quick-title">开始问答</span>
                  <span class="quick-desc">向AI提问</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import {
  Search, ArrowRight, Message, Document, ChatDotSquare,
  FolderOpened, Lightning, Plus, Upload, Grid, TrendCharts, Memo, Files
} from '@element-plus/icons-vue'
import { useDashboardStore } from '@/stores/dashboard'
import router from '@/router'
import { listKbApi } from '@/api/kb'
import type { KnowledgeBaseVO } from '@/types/kb'

const dashboardStore = useDashboardStore()

const heroQuestion = ref('')

const statsCards = ref([
  { label: '知识库', value: '0', icon: 'FolderOpened', gradient: 'linear-gradient(135deg, #6366f1, #818cf8)', action: () => router.push('/kb') },
  { label: '文档', value: '0', icon: 'Document', gradient: 'linear-gradient(135deg, #10b981, #34d399)', action: () => goToDocs() },
  { label: '问答次数', value: '0', icon: 'ChatDotSquare', gradient: 'linear-gradient(135deg, #f59e0b, #fbbf24)', action: () => router.push('/chat') },
  { label: '切片总数', value: '0', icon: 'Lightning', gradient: 'linear-gradient(135deg, #ef4444, #f87171)', action: () => goToDocs() }
])

const kbList = ref<KnowledgeBaseVO[]>([])
const recentChats = ref<any[]>([])
const recentDocs = ref<any[]>([])

onMounted(async () => {
  await dashboardStore.loadAll()
  const s = dashboardStore.stats
  statsCards.value = [
    { ...statsCards.value[0], value: String(s.kbCount) },
    { ...statsCards.value[1], value: String(s.docCount) },
    { ...statsCards.value[2], value: String(s.chatCount) },
    { ...statsCards.value[3], value: String(s.chunkCount) }
  ]

  recentChats.value = dashboardStore.recentActivity
    .filter(a => a.type === 'chat')
    .slice(0, 5)
    .map(a => ({ id: a.id, title: a.title, time: formatTime(a.time) }))

  // Load KB list for goToDocs navigation
  listKbApi(1, 100).then(list => { kbList.value = list.records || [] }).catch(() => {})

  recentDocs.value = dashboardStore.recentActivity
    .filter(a => a.type === 'doc')
    .slice(0, 5)
    .map(a => ({ id: a.id, title: a.title, description: a.description, time: a.time, fileType: getFileType(a.title), icon: getFileIcon(a.title) }))
})


function formatTime(iso: string): string {
  if (!iso) return ''
  const date = new Date(iso)
  const now = new Date()
  const diff = Math.floor((now.getTime() - date.getTime()) / 1000)
  if (diff < 60) return '刚刚'
  if (diff < 3600) return Math.floor(diff / 60) + ' 分钟前'
  if (diff < 86400) return Math.floor(diff / 3600) + ' 小时前'
  if (diff < 2592000) return Math.floor(diff / 86400) + ' 天前'
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

function goToCreateKb() {
  router.push('/kb')
}

function goToUpload() {
  goToDocs()
}

function goToChat() {
  router.push('/chat')
}

function goAskQuestion() {
  if (!heroQuestion.value.trim()) return
  router.push('/chat?question=' + encodeURIComponent(heroQuestion.value.trim()))
}

function getFileType(filename: string): string {
  const ext = filename.split('.').pop()?.toLowerCase() || ''
  const map: Record<string, string> = { md: 'markdown', pdf: 'pdf', doc: 'word', docx: 'word', xls: 'excel', xlsx: 'excel', ppt: 'ppt', pptx: 'ppt', txt: 'text', csv: 'csv', json: 'code', xml: 'code', html: 'code', js: 'code', ts: 'code', py: 'code', java: 'code' }
  return map[ext] || 'file'
}

function getFileIcon(filename: string): string {
  const type = getFileType(filename)
  const iconMap: Record<string, any> = { markdown: Document, pdf: Document, word: Document, excel: Grid, ppt: TrendCharts, text: Memo, csv: Grid, code: Files, file: Document }
  return iconMap[type] || Document
}

function goToDocs() {
  router.push('/documents')
}

function goToDoc(id: number) {
  router.push('/doc/' + id)
}

function goToSession(id: number) {
  router.push(`/chat?sessionId=${id}`)
}
</script>

<style scoped>
.dashboard {
  min-height: 100%;
  display: flex;
  justify-content: center;
}

.dashboard-inner {
  width: 100%;
  max-width: 1000px;
  padding: 48px 24px 80px;
}

/* Hero */
.hero-section {
  text-align: center;
  margin-bottom: 48px;
  animation: fadeInUp 0.4s ease-out;
}

.hero-title {
  font-size: 32px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.5px;
  margin-bottom: 8px;
}

.hero-subtitle {
  font-size: 15px;
  color: var(--color-text-secondary);
  margin-bottom: 32px;
  opacity: 0.8;
}

.hero-search {
  display: flex;
  align-items: center;
  gap: 10px;
  max-width: 620px;
  height: 56px;
  margin: 0 auto;
  padding: 0 6px 0 18px;
  background: var(--color-bg-secondary);
  border: 2px solid var(--color-border);
  border-radius: 16px;
  transition: border-color 0.3s, box-shadow 0.3s, background 0.3s;
}

.hero-search:focus-within {
  border-color: var(--color-accent);
  box-shadow: 0 0 0 4px rgba(99, 102, 241, 0.1), 0 4px 24px rgba(99, 102, 241, 0.08);
  background: var(--color-bg-tertiary);
}

.search-icon {
  color: var(--color-text-tertiary);
  flex-shrink: 0;
  transition: color 0.3s;
}

.hero-search:focus-within .search-icon {
  color: var(--color-accent);
}

.hero-search-input {
  flex: 1;
  border: none;
  outline: none;
  background: transparent;
  font-size: 15px;
  color: var(--color-text-primary);
  font-family: inherit;
  line-height: 1.5;
}

.hero-search-input::placeholder {
  color: var(--color-text-tertiary);
}

.hero-search-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 12px;
  background: var(--color-accent);
  color: #fff;
  cursor: pointer;
  flex-shrink: 0;
  transition: opacity 0.2s, transform 0.2s;
}

.hero-search-btn:hover:not(:disabled) {
  opacity: 0.88;
  transform: scale(1.04);
}

.hero-search-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

/* Stats */
.stats-section {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 32px;
  animation: fadeInUp 0.4s ease-out 0.1s both;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
}

.stat-card:hover {
  border-color: var(--color-border-hover);
  transform: translateY(-2px);
}

.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md, 8px);
  color: #fff;
  flex-shrink: 0;
}

.stat-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.stat-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  line-height: 1.2;
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.stat-arrow {
  position: absolute;
  right: 12px;
  top: 50%;
  transform: translateY(-50%);
  color: var(--color-text-tertiary);
  opacity: 0;
  transition: opacity 0.2s, transform 0.2s;
}

.stat-card:hover .stat-arrow {
  opacity: 1;
  transform: translateY(-50%) translateX(2px);
}

/* Content Grid */
.content-section {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  animation: fadeInUp 0.4s ease-out 0.2s both;
}

.content-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 16px 12px;
  border-bottom: 1px solid var(--color-border);
}

.card-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.card-more {
  font-size: 12px;
  color: var(--color-accent);
  text-decoration: none;
}

.card-more:hover {
  color: var(--color-accent-hover);
}

.card-body {
  padding: 12px 16px 16px;
  flex: 1;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px 0;
}

.empty-state p {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.empty-action {
  font-size: 12px;
  color: var(--color-accent);
  text-decoration: none;
  font-weight: 500;
}

.empty-action:hover {
  color: var(--color-accent-hover);
}

.doc-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 0;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border);
  transition: background 0.15s, padding-left 0.2s;
  border-radius: 6px;
  padding: 10px 8px;
  margin: 0 -8px;
}
.doc-item:last-child {
  border-bottom: none;
}
.doc-item:hover {
  background: var(--color-bg-tertiary);
}
.doc-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  flex-shrink: 0;
  color: #fff;
}
.doc-icon.markdown { background: linear-gradient(135deg, #6366f1, #818cf8); }
.doc-icon.pdf { background: linear-gradient(135deg, #ef4444, #f87171); }
.doc-icon.word { background: linear-gradient(135deg, #3b82f6, #60a5fa); }
.doc-icon.excel { background: linear-gradient(135deg, #10b981, #34d399); }
.doc-icon.ppt { background: linear-gradient(135deg, #f59e0b, #fbbf24); }
.doc-icon.text { background: linear-gradient(135deg, #6b7280, #9ca3af); }
.doc-icon.csv { background: linear-gradient(135deg, #10b981, #6ee7b7); }
.doc-icon.code { background: linear-gradient(135deg, #8b5cf6, #a78bfa); }
.doc-icon.file { background: linear-gradient(135deg, #6b7280, #9ca3af); }
.doc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}
.doc-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.doc-meta {
  font-size: 11px;
  color: var(--color-text-tertiary);
  display: flex;
  align-items: center;
  gap: 4px;
}
.doc-arrow {
  color: var(--color-text-tertiary);
  opacity: 0;
  transition: opacity 0.15s, transform 0.15s;
  flex-shrink: 0;
}
.doc-item:hover .doc-arrow {
  opacity: 1;
  transform: translateX(2px);
}
.activity-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 10px 8px;
  cursor: pointer;
  border-bottom: 1px solid var(--color-border);
  transition: background 0.15s, padding-left 0.2s;
  border-radius: 6px;
  margin: 0 -8px;
}
.activity-item:last-child {
  border-bottom: none;
}
.activity-item:hover {
  background: var(--color-bg-tertiary);
  padding-left: 12px;
}
.activity-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-top: 5px;
  flex-shrink: 0;
}
.activity-dot.chat { background: var(--color-accent); }
.activity-dot.doc { background: var(--color-success); }
.activity-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}
.activity-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.activity-desc {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.activity-time {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}
.empty-hint {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

/* Quick actions */
.quick-actions {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.quick-action-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  transition: background 0.2s;
}

.quick-action-item:hover {
  background: var(--color-bg-tertiary);
}

.quick-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md, 8px);
  flex-shrink: 0;
  color: #fff;
}

.quick-icon.create {
  background: linear-gradient(135deg, #6366f1, #818cf8);
}

.quick-icon.upload {
  background: linear-gradient(135deg, #10b981, #34d399);
}

.quick-icon.chat {
  background: linear-gradient(135deg, #f59e0b, #fbbf24);
}

.quick-text {
  display: flex;
  flex-direction: column;
}

.quick-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
}

.quick-desc {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 1px;
}

@keyframes fadeInUp {
  from {
    opacity: 0;
    transform: translateY(12px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

@media (max-width: 800px) {
  .stats-section {
    grid-template-columns: repeat(2, 1fr);
  }

  .content-section {
    grid-template-columns: 1fr;
  }
}
</style>
