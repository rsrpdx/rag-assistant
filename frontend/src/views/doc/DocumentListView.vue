<template>
  <div class="doc-list-page">
    <div class="page-header">
      <h2 class="page-title">文档管理</h2>
      <span class="page-subtitle">共 {{ total }} 个文档</span>
    </div>

    <div class="filter-bar">
      <el-input
        v-model="searchKeyword"
        placeholder="搜索文档名称..."
        :prefix-icon="Search"
        clearable
        class="search-input"
        @input="onSearchChange"
      />
      <el-select v-model="filterKbId" placeholder="全部知识库" clearable @change="onFilterChange" class="filter-select">
        <el-option v-for="kb in kbList" :key="kb.id" :label="kb.name" :value="kb.id" />
      </el-select>
      <el-select v-model="filterType" placeholder="全部类型" clearable @change="onFilterChange" class="filter-select">
        <el-option label="PDF" value="pdf" />
        <el-option label="Markdown" value="md" />
        <el-option label="Word" value="doc" />
        <el-option label="Word (docx)" value="docx" />
        <el-option label="TXT" value="txt" />
      </el-select>
      <el-select v-model="filterStatus" placeholder="全部状态" clearable @change="onFilterChange" class="filter-select">
        <el-option label="已就绪" :value="2" />
        <el-option label="解析中" :value="1" />
        <el-option label="待解析" :value="0" />
        <el-option label="解析失败" :value="3" />
      </el-select>
    </div>

    <div class="doc-table-wrap">
      <el-table
        :data="displayDocs"
        style="width: 100%"
        v-loading="loading"
        empty-text="暂无文档"
        row-class-name="doc-row"
      >
        <el-table-column prop="originalName" label="文档名称" min-width="220">
          <template #default="{ row }">
            <div class="doc-name-cell">
              <span class="doc-type-icon" :class="'type-' + (row.fileType || 'file')">
                {{ (row.fileType || '?').toUpperCase() }}
              </span>
              <span class="doc-name-text" @click="goToDoc(row.id)">{{ row.originalName }}</span>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="知识库" width="140">
          <template #default="{ row }">
            <span class="kb-tag">{{ getKbName(row.kbId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="大小" width="100" align="center">
          <template #default="{ row }">
            {{ formatSize(row.fileSize) }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="statusType(row.status)" size="small" :title="statusTooltip(row.status)">
              {{ statusText(row.status) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="切片数" width="80" align="center">
          <template #default="{ row }">
            {{ row.chunkCount || 0 }}
          </template>
        </el-table-column>
        <el-table-column label="上传时间" width="170" align="center">
          <template #default="{ row }">
            {{ formatTime(row.createdAt) }}
          </template>
        </el-table-column>
        <el-table-column label="操作" width="140" align="center" fixed="right">
          <template #default="{ row }">
            <el-button text type="primary" size="small" @click="goToDoc(row.id)">查看</el-button>
            <el-button text type="primary" size="small" @click="downloadDoc(row.id)">下载</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next"
        :pager-count="7"
        @size-change="onSizeChange"
        @current-change="loadData"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { listUserDocsApi, listKbApi } from '@/api/kb'

const router = useRouter()

const loading = ref(false)
const docs = ref<any[]>([])
const kbList = ref<any[]>([])
const searchKeyword = ref('')
const filterKbId = ref<number | ''>('')
const filterType = ref('')
const filterStatus = ref<number | ''>('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)

let searchTimer: ReturnType<typeof setTimeout> | null = null

const displayDocs = computed(() => {
  let result = docs.value
  if (searchKeyword.value) {
    const kw = searchKeyword.value.toLowerCase()
    result = result.filter(d => d.originalName?.toLowerCase().includes(kw))
  }
  if (filterKbId.value) {
    result = result.filter(d => d.kbId === filterKbId.value)
  }
  if (filterType.value) {
    result = result.filter(d => d.fileType === filterType.value)
  }
  if (filterStatus.value !== '') {
    result = result.filter(d => d.status === filterStatus.value)
  }
  return result
})

onMounted(async () => {
  await Promise.all([loadData(), loadKbList()])
})

async function loadData() {
  loading.value = true
  try {
    const res = await listUserDocsApi(currentPage.value, pageSize.value)
    docs.value = res.records || []
    total.value = res.total || 0
  } finally {
    loading.value = false
  }
}

async function loadKbList() {
  try {
    const res = await listKbApi(1, 100)
    kbList.value = res.records || []
  } catch { /* ignore */ }
}

function onSearchChange() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {}, 300)
}

function onFilterChange() {
  // Client-side filters apply automatically via computed
}

function onSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadData()
}

function getKbName(kbId: number): string {
  return kbList.value.find(k => k.id === kbId)?.name || '未知'
}

function goToDoc(id: number) {
  router.push('/doc/' + id)
}

function downloadDoc(id: number) {
  const token = localStorage.getItem('token')
  const url = '/api/v1/doc/' + id + '/download'
  const a = document.createElement('a')
  a.href = url
  if (token) {
    // Use fetch to download with auth
    fetch(url, { headers: { Authorization: 'Bearer ' + token } })
      .then(res => {
        if (!res.ok) throw new Error('下载失败')
        return res.blob()
      })
      .then(blob => {
        const blobUrl = URL.createObjectURL(blob)
        const a = document.createElement('a')
        a.href = blobUrl
        a.download = ''
        a.click()
        URL.revokeObjectURL(blobUrl)
      })
      .catch(() => {})
  }
}

function formatSize(bytes: number): string {
  if (!bytes) return '0 B'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

function formatTime(time: string): string {
  if (!time) return ''
  return time.replace('T', ' ').substring(0, 19)
}

function statusTooltip(status: number): string {
  if (status === 2) return "文档已解析完毕，可进行问答"
  if (status === 3) return "文档解析失败，请检查文件格式"
  if (status === 1) return "文档正在解析中，请稍候"
  return "文档等待解析中"
}

function statusType(status: number): string {
  if (status === 2) return 'success'
  if (status === 3) return 'danger'
  if (status === 1) return ''
  return 'warning'
}

function statusText(status: number): string {
  if (status === 2) return '已就绪'
  if (status === 3) return '解析失败'
  if (status === 1) return '解析中'
  return '待解析'
}
</script>

<style scoped>
.doc-list-page {
  padding: 32px 40px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  margin-bottom: 24px;
}

.page-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--color-text-primary);
  margin: 0 0 4px 0;
}

.page-subtitle {
  font-size: 13px;
  color: var(--color-text-secondary);
}

.filter-bar {
  display: flex;
  gap: 12px;
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.search-input {
  width: 260px;
}

.filter-select {
  width: 160px;
}

:deep(.search-input .el-input__wrapper) {
  background: var(--color-bg-secondary);
  border-color: var(--color-border);
}

:deep(.filter-select .el-input__wrapper) {
  background: var(--color-bg-secondary);
  border-color: var(--color-border);
}

.doc-table-wrap {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  overflow: hidden;
}

:deep(.doc-row) {
  cursor: default;
}

:deep(.el-table) {
  --el-table-bg-color: transparent;
  --el-table-tr-bg-color: transparent;
  --el-table-header-bg-color: var(--color-bg-tertiary);
  --el-table-row-hover-bg-color: var(--color-bg-tertiary);
  --el-table-border-color: var(--color-border);
  --el-table-text-color: var(--color-text-primary);
  --el-table-header-text-color: var(--color-text-secondary);
}

:deep(.el-table th) {
  font-size: 12px;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.3px;
}

.doc-name-cell {
  display: flex;
  align-items: center;
  gap: 10px;
}

.doc-type-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  font-size: 10px;
  font-weight: 700;
  color: #fff;
  flex-shrink: 0;
  letter-spacing: 0.5px;
}

.type-pdf { background: #ef4444; }
.type-md { background: #6366f1; }
.type-doc, .type-docx { background: #3b82f6; }
.type-txt { background: #6b7280; }
.type-file { background: #9ca3af; }

.doc-name-text {
  color: var(--color-text-primary);
  cursor: pointer;
  font-weight: 500;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-name-text:hover {
  color: var(--color-accent);
}

.kb-tag {
  font-size: 12px;
  color: var(--color-text-secondary);
}

.pagination-wrap {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}
</style>