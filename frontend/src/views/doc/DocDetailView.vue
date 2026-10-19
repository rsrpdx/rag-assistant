<template>
  <div class="doc-detail-page">
    <div class="doc-header">
      <div class="doc-header-info">
        <h1 class="doc-title">{{ doc?.originalName || '加载中...' }}</h1>
      </div>
    </div>

    <div v-if="loading" class="loading-state">
      <div class="skeleton-line w-60" />
      <div class="skeleton-line w-80" />
      <div class="skeleton-line w-40" />
    </div>

    <div v-else-if="!doc" class="empty-state">
      <el-icon :size="40" color="var(--color-text-tertiary)"><WarningFilled /></el-icon>
      <p>文档不存在或已被删除</p>
    </div>

    <div v-else class="doc-body">
      <!-- Preview Card -->
      <div class="preview-card">
        <div class="preview-header">
          <h3 class="preview-title">
            <el-icon :size="16"><View /></el-icon>
            文件预览
          </h3>
          <div class="preview-header-actions">
            <button class="preview-action" @click="toggleWrap" v-if="previewText">
              <el-icon :size="14"><Operation /></el-icon>
              <span>{{ wrapLines ? '不换行' : '自动换行' }}</span>
            </button>
            <button class="preview-action" @click="handleDownload">
              <el-icon :size="14"><Download /></el-icon>
              <span>下载</span>
            </button>
          </div>
        </div>
        <div class="preview-body">
          <!-- Loading -->
          <div v-if="previewLoading" class="preview-loading">
            <div class="spin-icon-lg" />
            <p>加载预览中...</p>
          </div>
          <!-- Error -->
          <div v-else-if="previewError" class="preview-error">
            <el-icon :size="24"><WarningFilled /></el-icon>
            <p>{{ previewError }}</p>
          </div>
          <!-- PDF -->
          <div v-else-if="doc.fileType === 'pdf'" class="preview-scroll">
            <iframe
              v-if="previewUrl"
              :src="previewUrl"
              class="preview-iframe"
              frameborder="0"
            />
            <div v-else class="preview-placeholder">
              <el-icon :size="32" color="var(--color-text-tertiary)"><Document /></el-icon>
              <p>无法加载PDF预览</p>
            </div>
          </div>
          <!-- Text preview (MD/DOC) -->
          <div v-else-if="previewText" class="preview-scroll">
            <pre class="preview-text" :class="{ 'no-wrap': !wrapLines }">{{ previewText }}</pre>
          </div>
          <!-- No content -->
          <div v-else class="preview-placeholder">
            <el-icon :size="32" color="var(--color-text-tertiary)"><Document /></el-icon>
            <p>无法预览此文件类型</p>
            <button class="preview-action" style="margin-top:12px" @click="handleDownload">
              <el-icon :size="14"><Download /></el-icon>
              <span>下载文件</span>
            </button>
          </div>
        </div>
      </div>

      <!-- Metadata Card -->
      <div class="meta-card">
        <div class="meta-grid">
          <div class="meta-item">
            <span class="meta-label">所属知识库</span>
            <span class="meta-value">
              <router-link :to="'/kb/' + doc.kbId" class="meta-link">
                {{ kbName || 'ID: ' + doc.kbId }}
              </router-link>
            </span>
          </div>
          <div class="meta-item">
            <span class="meta-label">文件大小</span>
            <span class="meta-value">{{ formatSize(doc.fileSize) }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">文件类型</span>
            <span class="meta-value">{{ doc.fileType?.toUpperCase() }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">切片数</span>
            <span class="meta-value">{{ doc.chunkCount }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">状态</span>
            <span class="doc-status" :class="statusClass" :title="statusTooltip">{{ statusText }}</span>
          </div>
          <div class="meta-item" v-if="doc.errorMsg">
            <span class="meta-label">错误信息</span>
            <span class="meta-value error-msg">{{ doc.errorMsg }}</span>
          </div>
          <div class="meta-item">
            <span class="meta-label">上传时间</span>
            <span class="meta-value">{{ formatTime(doc.createdAt) }}</span>
          </div>
        </div>
        <div class="meta-actions">
          <button class="action-btn secondary" @click="handleRetry" :disabled="doc.status === 1">
            <el-icon :size="14"><Refresh /></el-icon>
            <span>重新解析</span>
          </button>
          <button class="action-btn secondary" @click="handleDownload">
            <el-icon :size="14"><Download /></el-icon>
            <span>下载</span>
          </button>
          <button class="action-btn danger" @click="handleDelete">
            <el-icon :size="14"><Delete /></el-icon>
            <span>删除文档</span>
          </button>
        </div>
      </div>

      <!-- Chunks -->
      <div class="chunks-section">
        <div class="section-header">
          <h3 class="section-title">
            <el-icon :size="16"><Grid /></el-icon>
            切片内容
          </h3>
          <span class="chunk-count">{{ chunks.length }} 个切片</span>
        </div>
        <div v-if="chunks.length === 0" class="chunks-empty">
          <p v-if="doc.status === 0 || doc.status === 1">文档正在解析中，请稍后...</p>
          <p v-else-if="doc.status === 3">文档解析失败</p>
          <p v-else>暂无切片内容</p>
        </div>
        <div v-else class="chunks-list">
          <div v-for="(chunk, idx) in chunks" :key="idx" class="chunk-item">
            <div class="chunk-index">{{ idx + 1 }}</div>
            <div class="chunk-content">{{ chunk.content }}</div>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { WarningFilled, Refresh, Delete, Grid, View, Download, Document, Operation } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getDocDetailApi, deleteDocApi, retryDocApi } from '@/api/kb'
import type { DocumentDetailVO } from '@/types/kb'
import request from '@/api/request'

const route = useRoute()
const router = useRouter()
const docId = computed(() => Number(route.params.id))

const doc = ref<DocumentDetailVO | null>(null)
const chunks = ref<{ content: string }[]>([])
const kbName = ref('')
const loading = ref(true)
const previewUrl = ref('')
const previewText = ref('')
const previewLoading = ref(false)
const previewError = ref('')
const wrapLines = ref(true)

const statusText = computed(() => {
  switch (doc.value?.status) {
    case 0: return '等待中'
    case 1: return '解析中'
    case 2: return '已就绪'
    case 3: return '解析失败'
    default: return '未知'
  }
})

const statusTooltip = computed(() => {
  switch (doc.value?.status) {
    case 0: return "文档等待解析中，请稍候"
    case 1: return "文档正在解析中，请稍后查看"
    case 2: return "文档已解析完毕，可进行问答"
    case 3: return "文档解析失败，请检查文件格式"
    default: return ""
  }
})
const statusClass = computed(() => {
  switch (doc.value?.status) {
    case 0: return 'pending'
    case 1: return 'parsing'
    case 2: return 'success'
    case 3: return 'error'
    default: return 'pending'
  }
})

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(time: string): string {
  if (!time) return ''
  const date = new Date(time)
  const now = new Date()
  const diff = now.getTime() - date.getTime()
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' })
}

function toggleWrap() {
  wrapLines.value = !wrapLines.value
}

async function loadPreview() {
  if (!doc.value || !docId.value) return
  previewLoading.value = true
  previewError.value = ''

  const fileType = doc.value.fileType

  if (fileType === 'pdf') {
    try {
      const res = await request.get('/doc/' + docId.value + '/preview-url')
      previewUrl.value = res as unknown as string
    } catch {
      previewUrl.value = ''
      previewError.value = '无法加载PDF预览链接'
    }
  } else if (fileType === 'md' || fileType === 'doc') {
    // Fetch raw text content via download endpoint
    try {
      const token = localStorage.getItem('token')
      const resp = await fetch('/api/v1/doc/' + docId.value + '/download', {
        headers: token ? { Authorization: 'Bearer ' + token } : {}
      })
      if (!resp.ok) throw new Error('HTTP ' + resp.status)
      previewText.value = await resp.text()
    } catch {
      previewError.value = '无法加载文件内容'
      previewText.value = ''
    }
  }

  previewLoading.value = false
}

function handleDownload() {
  if (!docId.value) return
  const token = localStorage.getItem('token')
  fetch('/api/v1/doc/' + docId.value + '/download', {
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  })
    .then(res => {
      if (!res.ok) throw new Error('Download failed')
      const disp = res.headers.get('Content-Disposition') || ''
      const match = disp.match(/filename\*?=(?:UTF-8'')?([^;]+)/)
      const filename = match ? decodeURIComponent(match[1]) : (doc.value?.originalName || 'download')
      return res.blob().then(blob => ({ blob, filename }))
    })
    .then(({ blob, filename }) => {
      const url = URL.createObjectURL(blob)
      const a2 = document.createElement('a')
      a2.href = url
      a2.download = filename
      a2.click()
      URL.revokeObjectURL(url)
    })
    .catch(() => {
      ElMessage.error('下载失败')
    })
}

async function handleRetry() {
  try {
    await retryDocApi(docId.value)
    ElMessage.success('已触发重新解析')
    doc.value!.status = 1
    doc.value!.errorMsg = ''
  } catch {
    ElMessage.error('操作失败')
  }
}

async function handleDelete() {
  try {
    await ElMessageBox.confirm('确定删除该文档？删除后切片和向量数据将同步清除。', '提示', {
      confirmButtonText: '确定删除',
      cancelButtonText: '取消',
      type: 'warning'
    })
    await deleteDocApi(docId.value)
    ElMessage.success('文档已删除')
    router.push('/kb/' + doc.value!.kbId)
  } catch {
    // cancelled
  }
}

onMounted(async () => {
  try {
    const detail = await getDocDetailApi(docId.value)
    doc.value = detail
    chunks.value = detail.chunks || []
    loading.value = false
    await loadPreview()
  } catch {
    loading.value = false
  }
})
</script>

<style scoped>
.doc-detail-page {
  max-width: 960px;
  margin: 0 auto;
  padding: 32px 24px;
}

.doc-header {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
}

.doc-header-info { min-width: 0; }

.doc-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

/* Preview */
.preview-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
  margin-bottom: 20px;
}

.preview-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--color-border);
}

.preview-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.preview-header-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.preview-action {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: var(--radius-md, 6px);
  font-size: 12px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.preview-action:hover { opacity: 0.85; }

.preview-body {
  position: relative;
}

.preview-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 64px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.spin-icon-lg {
  width: 32px;
  height: 32px;
  border: 3px solid var(--color-border);
  border-top-color: var(--color-accent);
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

/* Scrollable content area */
.preview-scroll {
  max-height: 520px;
  overflow: auto;
  background: #fafbfc;
  scrollbar-width: thin;
  scrollbar-color: #cbd5e1 transparent;
}

.preview-scroll::-webkit-scrollbar {
  width: 6px;
  height: 6px;
}

.preview-scroll::-webkit-scrollbar-track {
  background: transparent;
}

.preview-scroll::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.preview-scroll::-webkit-scrollbar-thumb:hover {
  background: #94a3b8;
}

/* PDF iframe */
.preview-iframe {
  width: 100%;
  height: 520px;
  border: none;
  display: block;
}

/* Plain text preview */
.preview-text {
  margin: 0;
  padding: 20px;
  font-family: 'Consolas', 'Monaco', 'Courier New', monospace;
  font-size: 13px;
  line-height: 1.7;
  color: #1e293b;
  background: #fafbfc;
  white-space: pre-wrap;
  word-break: break-all;
  overflow-wrap: break-word;
  tab-size: 4;
  min-height: 200px;
}

.preview-text.no-wrap {
  white-space: pre;
  word-break: normal;
  overflow-wrap: normal;
}

.preview-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 6px;
  padding: 56px 24px;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.preview-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  padding: 48px 0;
  color: var(--color-text-secondary);
  font-size: 13px;
}

/* Loading */
.loading-state {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.skeleton-line {
  height: 18px;
  background: var(--color-bg-tertiary);
  border-radius: 6px;
  animation: pulse 1.5s infinite;
}
.skeleton-line.w-60 { width: 60%; }
.skeleton-line.w-80 { width: 80%; }
.skeleton-line.w-40 { width: 40%; }
@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.7; }
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 64px 0;
  color: var(--color-text-secondary);
  font-size: 14px;
}

/* Meta card */
.meta-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  padding: 20px;
  margin-bottom: 24px;
}

.meta-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 16px;
}

.meta-item { display: flex; flex-direction: column; gap: 4px; }

.meta-label {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.meta-value { font-size: 14px; color: var(--color-text-primary); font-weight: 500; }
.meta-link { color: var(--color-accent); text-decoration: none; }
.meta-link:hover { text-decoration: underline; }

.error-msg { color: var(--color-error); font-size: 13px; word-break: break-all; }

.doc-status {
  display: inline-block;
  font-size: 11px;
  font-weight: 500;
  padding: 2px 10px;
  border-radius: 10px;
  width: fit-content;
}
.doc-status.pending { background: rgba(82,82,91,0.2); color: var(--color-text-tertiary); }
.doc-status.parsing { background: rgba(245,158,11,0.15); color: var(--color-warning); }
.doc-status.success { background: rgba(16,185,129,0.15); color: var(--color-success); }
.doc-status.error { background: rgba(239,68,68,0.15); color: var(--color-error); }

.meta-actions {
  display: flex;
  gap: 8px;
  padding-top: 16px;
  border-top: 1px solid var(--color-border);
  flex-wrap: wrap;
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}
.action-btn:hover { opacity: 0.85; }
.action-btn.secondary {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
  border: 1px solid var(--color-border);
}
.action-btn.danger {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error);
  border: 1px solid rgba(239, 68, 68, 0.2);
}
.action-btn:disabled { opacity: 0.4; cursor: not-allowed; }

/* Chunks */
.chunks-section {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 20px;
  border-bottom: 1px solid var(--color-border);
}

.section-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.chunk-count { font-size: 12px; color: var(--color-text-tertiary); }

.chunks-empty {
  padding: 32px;
  text-align: center;
  color: var(--color-text-secondary);
  font-size: 13px;
}

.chunks-list {
  padding: 12px 20px 20px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.chunk-item {
  display: flex;
  gap: 12px;
  padding: 12px;
  background: var(--color-bg-tertiary);
  border-radius: var(--radius-md, 8px);
}

.chunk-index {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-accent);
  color: #fff;
  font-size: 12px;
  font-weight: 600;
  border-radius: 6px;
}

.chunk-content {
  font-size: 13px;
  color: var(--color-text-primary);
  line-height: 1.7;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
