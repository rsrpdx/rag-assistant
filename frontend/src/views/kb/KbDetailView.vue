<template>
  <div class="detail-page">
    <!-- Header -->
    <div class="detail-header">
      <div class="detail-info">
        <el-select
          v-model="selectedKbId"
          class="kb-switcher"
          placeholder="选择知识库"
          :teleported="false"
          filterable
          @change="switchKb"
        >
          <el-option
            v-for="k in allKbList"
            :key="k.id"
            :label="k.name"
            :value="k.id"
          />
        </el-select>
        <p class="detail-desc">{{ kb?.description || '暂无描述' }}</p>
      </div>
      <div class="detail-actions">
        <div class="kb-badge" :class="statusClass" :title="statusTooltip">
          {{ statusText }}
        </div>
        <button class="action-btn" @click="triggerFileInput">
          <el-icon :size="14"><Upload /></el-icon>
          <span>上传文档</span>
        </button>
      </div>
    </div>

    <!-- Stats -->
    <div class="detail-stats">
      <div class="detail-stat">
        <span class="ds-value">{{ kb?.documentCount || 0 }}</span>
        <span class="ds-label">文档</span>
      </div>
      <div class="detail-stat">
        <span class="ds-value">{{ kb?.chunkCount || 0 }}</span>
        <span class="ds-label">切片</span>
      </div>
      <div class="detail-stat">
        <span class="ds-value">{{ docTotal }}</span>
        <span class="ds-label">已就绪</span>
      </div>
    </div>

    <!-- Upload Queue -->
    <div v-if="uploadQueue.length > 0" class="upload-queue">
      <div class="uq-header">
        <h4 class="uq-title">上传中 ({{ uploadDoneCount }}/{{ uploadQueue.length }})</h4>
        <button class="uq-clear" @click="clearDone">清除已完成</button>
      </div>
      <div
        v-for="item in uploadQueue"
        :key="item.id"
        class="uq-item"
        :class="'uq-' + item.status"
      >
        <div class="uq-icon">
          <el-icon v-if="item.status === 'uploading'" :size="16" class="spin-icon"><Loading /></el-icon>
          <el-icon v-else-if="item.status === 'done'" :size="16" color="var(--color-success)"><CircleCheckFilled /></el-icon>
          <el-icon v-else-if="item.status === 'error'" :size="16" color="var(--color-error)"><CircleCloseFilled /></el-icon>
          <el-icon v-else :size="16" color="var(--color-text-tertiary)"><Clock /></el-icon>
        </div>
        <div class="uq-body">
          <div class="uq-name">{{ item.file.name }}</div>
          <div class="uq-size">{{ formatSize(item.file.size) }}</div>
          <div v-if="item.status === 'uploading'" class="uq-progress-bar">
            <div class="uq-progress-fill" :style="{ width: item.progress + '%' }" />
          </div>
          <div v-else-if="item.status === 'error'" class="uq-error">{{ item.error }}</div>
          <div v-else-if="item.status === 'done'" class="uq-done-text">上传完成，解析中</div>
        </div>
        <button
          v-if="item.status === 'pending' || item.status === 'uploading'"
          class="uq-cancel"
          @click="cancelUpload(item.id)"
        >
          <el-icon :size="14"><Close /></el-icon>
        </button>
      </div>
    </div>

    <!-- Document Section -->
    <div class="doc-section">
      <div class="doc-section-header">
        <h3 class="doc-section-title">文档列表</h3>
      </div>

      <!-- Upload Zone -->
      <div
        class="upload-zone"
        @drop.prevent="handleDrop"
        @dragover.prevent
        @click="triggerFileInput"
      >
        <input
          ref="fileInputRef"
          type="file"
          accept=".pdf,.md,.doc"
          multiple
          hidden
          @change="handleFileSelect"
        />
        <el-icon :size="24" color="#52525B"><UploadFilled /></el-icon>
        <p class="upload-text">拖拽文件到此处，或点击上传</p>
        <p class="upload-hint">支持 PDF、MD、DOC、DOCX 格式，可批量上传，单文件最大 50MB</p>
      </div>

      <!-- Loading -->
      <div v-if="docLoading" class="loading-list">
        <div v-for="i in 3" :key="i" class="skeleton-row" />
      </div>

      <!-- Empty -->
      <div v-else-if="docs.length === 0" class="doc-empty">
        <p>暂无文档，上传文件开始使用</p>
      </div>

      <!-- Table -->
      <div v-else class="doc-table">
        <div class="doc-table-header">
          <span class="col-name">文件名</span>
          <span class="col-size">大小</span>
          <span class="col-status">状态</span>
          <span class="col-chunks">切片</span>
          <span class="col-time">上传时间</span>
          <span class="col-action">操作</span>
        </div>
        <div
          v-for="doc in docs"
          :key="doc.id"
          class="doc-table-row"
        >
          <span class="col-name doc-link" @click="router.push('/doc/' + doc.id)">
            <el-icon :size="14" color="#EF4444"><Document /></el-icon>
            {{ doc.originalName }}
          </span>
          <span class="col-size">{{ formatSize(doc.fileSize) }}</span>
          <span class="col-status">
            <span class="doc-status" :class="docStatusClass(doc.status)" :title="docStatusTooltip(doc.status)">
              {{ docStatusText(doc.status) }}
            </span>
          </span>
          <span class="col-chunks">{{ doc.chunkCount }}</span>
          <span class="col-time">{{ formatTime(doc.createdAt) }}</span>
          <span class="col-action">
            <el-dropdown trigger="click" @command="(cmd: string) => handleDocAction(cmd, doc.id)">
              <button class="row-menu">
                <el-icon :size="14"><MoreFilled /></el-icon>
              </button>
              <template #dropdown>
                <el-dropdown-menu class="doc-dropdown">
                  <el-dropdown-item command="retry" :disabled="doc.status === 1">重新解析</el-dropdown-item>
                  <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { Upload, UploadFilled, Document, MoreFilled, Loading, CircleCheckFilled, CircleCloseFilled, Clock, Close } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
import { getKbDetailApi, listDocsApi, uploadDocWithProgress, deleteDocApi, retryDocApi, getDocDetailApi, listKbApi } from '@/api/kb'
import type { KnowledgeBaseVO, DocumentVO } from '@/types/kb'

const route = useRoute()
const router = useRouter()
const kbId = computed(() => Number(route.params.id))
// activeKbId tracks the actual KB being displayed (may differ from URL after switchKb)
const activeKbId = ref<number>(kbId.value)
const selectedKbId = ref<number>(kbId.value)
const allKbList = ref<{ id: number; name: string }[]>([])

const kb = ref<KnowledgeBaseVO | null>(null)
const docs = ref<DocumentVO[]>([])
const docTotal = ref(0)
const docLoading = ref(true)
const fileInputRef = ref<HTMLInputElement>()

// Upload queue state
interface UploadItem {
  id: number
  file: File
  progress: number
  status: 'pending' | 'uploading' | 'done' | 'error' | 'cancelled'
  error?: string
  controller?: AbortController
}
const uploadQueue = ref<UploadItem[]>([])
let uploadIdCounter = 0

const uploadDoneCount = computed(() => uploadQueue.value.filter(i => i.status === 'done').length)
const hasUploading = computed(() => uploadQueue.value.some(i => i.status === 'uploading'))
const expandUpload = ref(true)

const statusClass = computed(() => {
  if (!kb.value) return ''
  const s = kb.value.status
  if (s === 0) return 'empty'
  if (s === 1) return 'processing'
  return 'ready'
})

const statusTooltip = computed(() => {
  if (!kb.value) return ""
  const s = kb.value.status
  if (s === 0) return "知识库为空，请上传文档"
  if (s === 1) return "正在解析文档内容"
  return "知识库已就绪，可进行问答"
})

const statusText = computed(() => {
  if (!kb.value) return ''
  const s = kb.value.status
  if (s === 0) return '空'
  if (s === 1) return '处理中'
  return '就绪'
})

onMounted(async () => {
  activeKbId.value = kbId.value
  selectedKbId.value = kbId.value
  await Promise.all([loadKb(), loadDocs(), fetchAllKb()])
})


async function fetchAllKb() {
  try {
    const res = await listKbApi(1, 100)
    allKbList.value = (res.records || []).map(k => ({ id: k.id, name: k.name }))
  } catch {
    allKbList.value = []
  }
}

async function switchKb(id: number) {
  activeKbId.value = id
  try {
    kb.value = await getKbDetailApi(id)
    const res = await listDocsApi(id)
    docs.value = res.records || []
    docTotal.value = docs.value.filter(d => d.status === 2).length
  } catch {
    ElMessage.error('加载失败')
  }
}

async function loadKb() {
  try {
    kb.value = await getKbDetailApi(activeKbId.value)
  } catch {
    ElMessage.error('知识库不存在')
    router.push('/kb')
  }
}

async function loadDocs() {
  docLoading.value = true
  try {
    const res = await listDocsApi(activeKbId.value)
    docs.value = res.records || []
    docTotal.value = docs.value.filter(d => d.status === 2).length
  } catch {
    docs.value = []
  } finally {
    docLoading.value = false
  }
}

function triggerFileInput() {
  fileInputRef.value?.click()
}

function handleFileSelect(e: Event) {
  const files = (e.target as HTMLInputElement).files
  if (files && files.length > 0) {
    addFilesToQueue(Array.from(files))
    // Reset input so the same file can be re-selected
    ;(e.target as HTMLInputElement).value = ''
  }
}

function handleDrop(e: DragEvent) {
  const files = e.dataTransfer?.files
  if (files && files.length > 0) {
    addFilesToQueue(Array.from(files))
  }
}

function addFilesToQueue(files: File[]) {
  const validExtensions = ['.pdf', '.md', '.doc', '.docx']

  for (const file of files) {
    const extension = '.' + file.name.split('.').pop()?.toLowerCase()
    if (!validExtensions.includes(extension)) {
      ElMessage.warning(`"${file.name}" 格式不支持，仅支持 PDF、MD、DOC`)
      continue
    }
    if (file.size > 50 * 1024 * 1024) {
      ElMessage.warning(`"${file.name}" 文件大小超过50MB限制`)
      continue
    }
    // Avoid duplicates
    if (uploadQueue.value.some(i => i.file.name === file.name && i.file.size === file.size && i.status !== 'cancelled' && i.status !== 'error')) {
      ElMessage.warning(`"${file.name}" 已在上传队列中`)
      continue
    }

    const item: UploadItem = {
      id: ++uploadIdCounter,
      file,
      progress: 0,
      status: 'pending'
    }
    uploadQueue.value.push(item)

    // Start uploading after a micro-delay
    setTimeout(() => uploadSingle(item), 50)
  }
}

async function uploadSingle(item: UploadItem) {
  if (item.status === 'cancelled') return

  item.controller = new AbortController()
  item.status = 'uploading'
  item.progress = 0

  try {
    const doc = await uploadDocWithProgress(
      activeKbId.value,
      item.file,
      (percent) => { item.progress = percent },
      item.controller.signal
    )
    item.status = 'done'
    item.progress = 100
    docs.value.unshift(doc)
    if (kb.value) kb.value.documentCount++
    // Start auto-polling for document status until ready/error
    if (doc.status < 2) {
      pollDocStatus(doc.id)
    }
  } catch (e: any) {
    if (e.message === '已取消') {
      item.status = 'cancelled'
    } else {
      item.status = 'error'
      item.error = e.message || '上传失败'
    }
  }
}

function cancelUpload(id: number) {
  const item = uploadQueue.value.find(i => i.id === id)
  if (item && item.controller) {
    item.controller.abort()
    item.status = 'cancelled'
  }
}

// Auto-poll document status after upload until ready/error
const pollTimers = new Map<number, ReturnType<typeof setInterval>>()

function pollDocStatus(docId: number) {
  // Clear existing timer for this doc if any
  if (pollTimers.has(docId)) {
    clearInterval(pollTimers.get(docId))
  }

  let attempts = 0
  const maxAttempts = 60 // 2 min max

  const timer = setInterval(async () => {
    attempts++
    try {
      const latest = await getDocDetailApi(docId)
      const idx = docs.value.findIndex(d => d.id === docId)
      if (idx >= 0) {
        // Update doc in list with latest data
        docs.value[idx] = {
          ...docs.value[idx],
          status: latest.status,
          chunkCount: latest.chunkCount,
          errorMsg: latest.errorMsg
        }
        // Force reactivity
        docs.value = [...docs.value]
      }
      // Update KB chunk count
      if (kb.value) {
        kb.value.chunkCount = docs.value.reduce((sum, d) => sum + (d.chunkCount || 0), 0)
        if (latest.status === 2 || latest.status === 3) {
          kb.value.status = docs.value.some(d => d.status === 1) ? 1 : 2
        }
      }
      // Stop polling when done or errored
      if (latest.status >= 2 || attempts >= maxAttempts) {
        clearInterval(timer)
        pollTimers.delete(docId)
      }
    } catch {
      if (attempts >= maxAttempts) {
        clearInterval(timer)
        pollTimers.delete(docId)
      }
    }
  }, 2000)
  pollTimers.set(docId, timer)
}

function clearDone() {
  uploadQueue.value = uploadQueue.value.filter(
    i => i.status !== 'done' && i.status !== 'cancelled'
  )
}

function downloadFile(docId: number) {
  const token = localStorage.getItem('token')
  fetch('/api/v1/doc/' + docId + '/download', {
    headers: token ? { Authorization: 'Bearer ' + token } : {}
  })
    .then(res => {
      if (!res.ok) throw new Error('Download failed')
      const disp = res.headers.get('Content-Disposition') || ''
      const match = disp.match(/filename*?=(?:UTF-8'')?([^;]+)/)
      const filename = match ? decodeURIComponent(match[1]) : 'download'
      return res.blob().then(blob => ({ blob, filename }))
    })
    .then(({ blob, filename }) => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = filename
      a.click()
      URL.revokeObjectURL(url)
    })
    .catch(() => {
      ElMessage.error('????')
    })
}

async function handleDocAction(cmd: string, docId: number) {
  if (cmd === 'preview') {
    router.push('/doc/' + docId)
    return
  }
  if (cmd === 'download') {
    downloadFile(docId)
    return
  }
  if (cmd === 'retry') {
    try {
      await retryDocApi(docId)
      ElMessage.success('已重新加入解析队列')
      // Start polling for new status
      pollDocStatus(docId)
    } catch (e: any) {
      ElMessage.error(e.message || '操作失败')
    }
  } else if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm('确定删除该文档？', '提示', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await deleteDocApi(docId)
      docs.value = docs.value.filter(d => d.id !== docId)
      ElMessage.success('已删除')
      if (kb.value) {
        kb.value.documentCount = Math.max(0, kb.value.documentCount - 1)
      }
    } catch {
      // cancelled
    }
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(iso: string): string {
  if (!iso) return ''
  return iso.slice(0, 16).replace('T', ' ')
}

function docStatusClass(status: number): string {
  if (status === 0) return 'pending'
  if (status === 1) return 'parsing'
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  return 'error'
}

function docStatusTooltip(status: number): string {
  if (status === 0) return "文档已上传，等待解析"
  if (status === 1) return "AI 正在解析文档内容"
  if (status === 2) return "解析完成，可用于问答"
  if (status === 3) return "文档解析失败，请检查文件格式"
  return "解析失败，可点击重新解析"
}

function docStatusText(status: number): string {
  if (status === 0) return '待解析'
  if (status === 1) return '解析中'
  if (status === 2) return '已就绪'
  if (status === 3) return '解析失败'
  return '失败'
}
</script>

<style scoped>
.detail-page {
  max-width: 1000px;
  margin: 0 auto;
  padding: 32px 24px;
}

.detail-header {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  margin-bottom: 24px;
}

.detail-info {
  flex: 1;
  min-width: 0;
}

.detail-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin-bottom: 4px;
}

.detail-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.detail-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
  margin-top: 4px;
}

.kb-badge {
  font-size: 11px;
  font-weight: 500;
  padding: 4px 10px;
  border-radius: 10px;
}

.kb-badge.empty {
  background: rgba(82, 82, 91, 0.2);
  color: var(--color-text-tertiary);
}

.kb-badge.processing {
  background: rgba(245, 158, 11, 0.15);
  color: var(--color-warning);
}

.kb-badge.ready {
  background: rgba(16, 185, 129, 0.15);
  color: var(--color-success);
}

.action-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.action-btn:hover {
  opacity: 0.85;
}

.detail-stats {
  display: flex;
  gap: 24px;
  margin-bottom: 28px;
  padding: 16px 20px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
}

.detail-stat {
  display: flex;
  align-items: baseline;
  gap: 6px;
}

.ds-value {
  font-size: 20px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.ds-label {
  font-size: 13px;
  color: var(--color-text-secondary);
}

/* Upload Queue */
.upload-queue {
  margin-bottom: 20px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
}

.uq-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.uq-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin: 0;
}

.uq-clear {
  font-size: 11px;
  color: var(--color-text-tertiary);
  background: none;
  border: none;
  cursor: pointer;
  padding: 2px 6px;
  border-radius: 4px;
}

.uq-clear:hover {
  color: var(--color-text-primary);
  background: var(--color-bg-tertiary);
}

.uq-item {
  display: flex;
  align-items: flex-start;
  gap: 10px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--color-border);
}

.uq-item:last-child {
  border-bottom: none;
}

.uq-item.uq-cancelled {
  opacity: 0.4;
}

.uq-icon {
  padding-top: 1px;
  flex-shrink: 0;
}

.spin-icon {
  animation: spin 1s linear infinite;
  color: var(--color-accent);
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.uq-body {
  flex: 1;
  min-width: 0;
}

.uq-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.uq-size {
  font-size: 11px;
  color: var(--color-text-tertiary);
  margin-top: 2px;
}

.uq-progress-bar {
  height: 4px;
  background: var(--color-bg-tertiary);
  border-radius: 2px;
  margin-top: 8px;
  overflow: hidden;
}

.uq-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, var(--color-accent), #818cf8);
  border-radius: 2px;
  transition: width 0.3s ease;
}

.uq-error {
  font-size: 12px;
  color: var(--color-error);
  margin-top: 4px;
}

.uq-done-text {
  font-size: 12px;
  color: var(--color-success);
  margin-top: 4px;
}

.uq-cancel {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border: none;
  background: none;
  color: var(--color-text-tertiary);
  border-radius: 4px;
  cursor: pointer;
  margin-top: 2px;
}

.uq-cancel:hover {
  background: var(--color-bg-tertiary);
  color: var(--color-error);
}

/* Document Section */
.doc-section {
  margin-top: 8px;
}

.doc-section-header {
  margin-bottom: 16px;
}

.upload-toggle-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  font-size: 11px;
  color: var(--color-text-tertiary);
  background: none;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm, 4px);
  padding: 3px 8px;
  cursor: pointer;
  transition: color 0.2s, border-color 0.2s;
}
`.upload-toggle-btn:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}
`.doc-section-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--color-text-primary);
}

.upload-zone {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 32px;
  border: 2px dashed var(--color-border);
  border-radius: var(--radius-lg, 12px);
  cursor: pointer;
  transition: all 0.2s;
  margin-bottom: 20px;
}

.upload-zone:hover {
  border-color: var(--color-accent);
  background: rgba(99, 102, 241, 0.04);
}

.upload-text {
  font-size: 14px;
  color: var(--color-text-secondary);
}

.upload-hint {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.loading-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.skeleton-row {
  height: 52px;
  background: var(--color-bg-secondary);
  border-radius: var(--radius-md, 8px);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.7; }
}

.doc-empty {
  text-align: center;
  padding: 32px 0;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.doc-table {
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
}

.doc-table-header {
  display: grid;
  grid-template-columns: 3fr 80px 80px 60px 140px 60px;
  gap: 8px;
  padding: 12px 16px;
  background: var(--color-bg-tertiary);
  font-size: 12px;
  font-weight: 600;
  color: var(--color-text-secondary);
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.doc-table-row {
  display: grid;
  grid-template-columns: 3fr 80px 80px 60px 140px 60px;
  gap: 8px;
  padding: 12px 16px;
  font-size: 13px;
  color: var(--color-text-primary);
  border-top: 1px solid var(--color-border);
  transition: background 0.1s;
}

.doc-table-row:hover {
  background: var(--color-bg-tertiary);
}

.col-name {
  display: flex;
  align-items: center;
  gap: 6px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-name.doc-link {
  cursor: pointer;
  color: var(--color-accent);
  transition: opacity 0.15s;
}

.col-name.doc-link:hover {
  opacity: 0.75;
}

.col-size,
.col-chunks {
  color: var(--color-text-secondary);
}

.doc-status {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
}

.doc-status.pending {
  background: rgba(82, 82, 91, 0.2);
  color: var(--color-text-tertiary);
}

.doc-status.parsing {
  background: rgba(245, 158, 11, 0.15);
  color: var(--color-warning);
}

.doc-status.success {
  background: rgba(16, 185, 129, 0.15);
  color: var(--color-success);
}

.doc-status.error {
  background: rgba(239, 68, 68, 0.15);
  color: var(--color-error);
}

.row-menu {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  border-radius: 6px;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
}

.doc-table-row:hover .row-menu {
  opacity: 1;
}

.row-menu:hover {
  background: var(--color-bg-elevated);
}

:deep(.doc-dropdown) {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  padding: 4px;
}

:deep(.doc-dropdown .el-dropdown-menu__item) {
  color: var(--color-text-primary);
  font-size: 13px;
  border-radius: 6px;
  padding: 6px 12px;
}

:deep(.doc-dropdown .el-dropdown-menu__item:hover) {
  background: var(--color-bg-tertiary);
}

:deep(.el-dropdown-menu__item--divided) {
  border-top-color: var(--color-border);
}
</style>
