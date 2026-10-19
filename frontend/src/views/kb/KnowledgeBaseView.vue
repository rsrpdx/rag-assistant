<template>
  <div class="kb-page">
    <div class="kb-header">
      <div class="kb-header-left">
        <h1 class="kb-title">知识库</h1>
        <span class="kb-count">共 {{ total }} 个</span>
      </div>
      <button class="create-btn" @click="openCreateDialog">
        <el-icon :size="14"><Plus /></el-icon>
        <span>新建知识库</span>
      </button>
    </div>

    <!-- Manage Categories Button -->
    <div v-if="categories.length > 0 || customCategories.length > 0" class="kb-categories-bar">
      <div v-if="activeCategoryFilter" class="active-filter-tag">
        <span>{{ activeCategoryFilter }}</span>
        <button class="filter-clear-btn" @click="clearCategoryFilter">
          <el-icon :size="10"><Close /></el-icon>
        </button>
      </div>
      <button class="manage-cat-btn" @click="openCatDialog">
        <el-icon :size="12"><Setting /></el-icon>
        <span>管理分类</span>
      </button>
    </div>

    <div v-if="loading" class="loading-grid">
      <div v-for="i in 6" :key="i" class="skeleton-card" />
    </div>

    <div v-else-if="kbList.length === 0" class="empty-state">
      <div class="empty-icon">
        <el-icon :size="48" color="#52525B"><FolderOpened /></el-icon>
      </div>
      <h3 class="empty-title">还没有知识库</h3>
      <p class="empty-desc">创建知识库，上传文档，开始AI问答</p>
      <button class="create-btn" @click="openCreateDialog">
        <el-icon :size="14"><Plus /></el-icon>
        <span>创建第一个知识库</span>
      </button>
    </div>

    <div v-else class="kb-grid">
      <div
        v-for="kb in kbList"
        :key="kb.id"
        class="kb-card"
        @click="goToDetail(kb.id)"
      >
        <div class="card-top">
          <div class="card-icon">
            <el-icon :size="22"><FolderOpened /></el-icon>
          </div>
        <button class="card-menu" @click.stop="showMenu(kb, $event)">
            <el-icon :size="16"><MoreFilled /></el-icon>
          </button>
        </div>

        <div class="card-title-row">
          <h3 class="card-title">{{ kb.name }}</h3>
          <span v-if="kb.category" class="card-category">{{ kb.category }}</span>
        </div>
        <p class="card-desc">{{ kb.description || '暂无描述' }}</p>

        <div class="card-stats">
          <div class="stat-item">
            <span class="stat-value">{{ kb.documentCount }}</span>
            <span class="stat-label">文档</span>
          </div>
          <div class="stat-divider" />
          <div class="stat-item">
            <span class="stat-value">{{ kb.chunkCount }}</span>
            <span class="stat-label">切片</span>
          </div>
        </div>

        <div class="card-footer">
          <span class="card-time">{{ formatTime(kb.createdAt) }}</span>
          <div class="card-status" :class="statusClass(kb.status)" :title="statusTooltip(kb.status)">
            {{ statusText(kb.status) }}
          </div>
        </div>

        <!-- 展开文档 -->
        <div class="card-expand" @click.stop="toggleExpand(kb.id, $event)">
          <el-icon :size="14" class="expand-icon" :class="{ rotated: expandedKbId === kb.id }"><ArrowDown /></el-icon>
          <span>{{ expandedKbId === kb.id ? '收起文档' : '展开文档 (' + kb.documentCount + ')' }}</span>
        </div>
        <div v-if="expandedKbId === kb.id" class="expand-docs" @click.stop>
          <div v-if="expandedLoading" class="expand-loading">加载中...</div>
          <div v-else-if="expandedDocs.length === 0" class="expand-empty">暂无文档</div>
          <div v-else class="expand-doc-list">
            <div v-for="doc in expandedDocs" :key="doc.id" class="expand-doc-item" @click="goToDoc(doc.id, $event)">
              <el-icon :size="14"><Document /></el-icon>
              <span class="expand-doc-name">{{ doc.originalName }}</span>
              <span class="expand-doc-status" :class="doc.status === 2 ? 'ready' : doc.status === 1 ? 'parsing' : 'pending'">
                {{ doc.status === 2 ? '已就绪' : doc.status === 1 ? '解析中' : '待解析' }}
              </span>
              <span class="expand-doc-size">{{ doc.fileSize ? (doc.fileSize / 1024).toFixed(1) + ' KB' : '' }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- Pagination -->
    <div v-if="kbList.length > 0" class="kb-pagination">
      <span class="kb-total-label">总计 {{ total }} 个文件</span>
      <div class="kb-page-size-select">
        <el-select
          :model-value="pageSize"
          :teleported="false"
          class="page-size-dropdown"
          @update:model-value="handleSizeChange"
        >
          <el-option
            v-for="s in [9, 18, 27]"
            :key="s"
            :label="'每页显示/' + s + '条'"
            :value="s"
          />
        </el-select>
      </div>
      <el-pagination
        v-model:current-page="currentPage"
        :page-size="pageSize"
        :total="total"
        :pager-count="5"
        layout="prev, pager, next"
        background
        small
        @current-change="handlePageChange"
      />
    </div>

    <!-- Create Dialog -->
    <el-dialog
      v-model="showCreateDialog"
      title="新建知识库"
      width="420px"
      top="25vh"
      custom-class="kb-dialog"
    >
      <el-form
        ref="createFormRef"
        :model="createForm"
        :rules="createRules"
        label-position="top"
        size="large"
      >
        <el-form-item label="名称" prop="name">
          <el-input v-model="createForm.name" placeholder="例如：Java面试指南" maxlength="100" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-select
            v-model="createForm.category"
            placeholder="选择分类"
            clearable
          >
            <el-option
              v-for="(cat, idx) in recentCategories"
              :key="'r'+idx"
              :label="cat"
              :value="cat"
            />
            <el-option label="其他" value="其他" />
            <el-option label="自定义..." value="__custom__" />
          </el-select>
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input
            v-model="createForm.description"
            type="textarea"
            :rows="3"
            placeholder="简要描述知识库的用途"
            maxlength="500"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false; createFormRef?.resetFields()">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">
          创建
        </el-button>
      </template>
    </el-dialog>

    <!-- Category Management Dialog -->
    <el-dialog
      v-model="showCatDialog"
      title="管理分类"
      width="420px"
      top="25vh"
      custom-class="kb-dialog"
    >
      <div class="cat-search-row">
        <input
          v-model="catSearchQuery"
          type="text"
          class="cat-search-input"
          placeholder="搜索分类..."
        />
      </div>
      <div class="cat-manage-list">
        <div v-if="filteredCategories.length === 0" class="cat-empty">
          {{ catSearchQuery ? '无匹配分类' : '暂无分类' }}
        </div>
        <div
          v-for="cat in filteredCategories"
          :key="cat"
          class="cat-manage-item"
          @click="filterByCategory(cat)"
        >
          <span class="cat-manage-name">{{ cat }}</span>
          <button
            v-if="isCustomCategory(cat)"
            class="cat-delete-btn"
            @click.stop="deleteCustomCategoryByName(cat)"
          >
            <el-icon :size="12"><Close /></el-icon>
          </button>
          <span v-else class="cat-used-tag">使用中</span>
        </div>
      </div>
      <div class="cat-add-row">
        <el-input
          v-model="newCatName"
          placeholder="输入新分类名称"
          maxlength="20"
          @keyup.enter="addCustomCategory"
        />
        <el-button type="primary" size="small" @click="addCustomCategory">添加</el-button>
      </div>
      <template #footer>
        <el-button @click="showCatDialog = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- Context Menu -->
    <div
      v-if="menuVisible"
      class="context-menu"
      :style="{ top: menuY + 'px', left: menuX + 'px' }"
      @click.stop
    >
      <div class="context-item" @click="handleEditCategory">
        <el-icon :size="14"><FolderOpened /></el-icon>
        编辑分类
      </div>
      <div class="context-item" @click="handleRename">
        <el-icon :size="14"><Edit /></el-icon>
        重命名
      </div>
      <div class="context-item danger" @click="handleDelete">
        <el-icon :size="14"><Delete /></el-icon>
        删除
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { Plus, FolderOpened, MoreFilled, Edit, Delete, Setting, Close, Document, ArrowDown } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { listKbApi, createKbApi, deleteKbApi, updateKbApi, fetchCategoriesApi, listDocsApi } from '@/api/kb'
import type { KnowledgeBaseVO, CreateKbRequest } from '@/types/kb'
import router from '@/router'

const kbList = ref<KnowledgeBaseVO[]>([])
const total = ref(0)
const loading = ref(true)
const currentPage = ref(1)
const pageSize = ref(9)

const showCreateDialog = ref(false)
const creating = ref(false)
const createFormRef = ref<FormInstance>()
const createForm = ref<CreateKbRequest>({ name: '', description: '', category: '' })
const createRules: FormRules = {
  name: [
    { required: true, message: '请输入知识库名称', trigger: 'blur' },
    { max: 100, message: '名称不能超过100个字符', trigger: 'blur' }
  ],
  description: [
    { required: true, message: '请输入知识库描述', trigger: 'blur' },
    { max: 500, message: '描述不能超过500个字符', trigger: 'blur' }
  ],
  category: [
    { required: true, message: '请选择知识库分类', trigger: 'change' }
  ]
}

const expandedKbId = ref<number | null>(null)
const expandedDocs = ref<any[]>([])
const expandedLoading = ref(false)
const showCatDialog = ref(false)
const activeCategoryFilter = ref('')
const newCatName = ref('')
const catSearchQuery = ref('')
const recentCategories = ref<string[]>(JSON.parse(localStorage.getItem('kb_recent_cats') || '[]'))
const categories = ref<string[]>([])
const customCategories = ref<string[]>(JSON.parse(localStorage.getItem('kb_custom_cats') || '[]'))
const filteredCategories = computed(() => {
  const merged = [...new Set([...categories.value, ...customCategories.value])]
  const q = (catSearchQuery.value || '').toLowerCase().trim()
  if (!q) return merged
  return merged.filter(function(c) { return c.toLowerCase().includes(q) })
})

const menuVisible = ref(false)
const menuX = ref(0)
const menuY = ref(0)
const selectedKb = ref<KnowledgeBaseVO | null>(null)

onMounted(async () => {
  await Promise.all([loadKbList(), loadCategories()])
})

onUnmounted(() => {
  document.removeEventListener('click', closeMenu)
})

// Watch for custom category selection
watch(() => createForm.value.category, async (val) => {
  if (val === '__custom__') {
    createForm.value.category = ''
    try {
      const { value: newCat } = await ElMessageBox.prompt('请输入新分类名称', '自定义分类', {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputPattern: /^.+$/,
        inputErrorMessage: '名称不能为空'
      })
      if (newCat && newCat.trim()) {
        const trimmed = newCat.trim()
        createForm.value.category = trimmed
        addRecentCategory(trimmed)
      }
    } catch {
      // cancelled
    }
  }
})

function addRecentCategory(cat: string) {
  if (!cat || cat === '__custom__') return
  let recents = [...recentCategories.value]
  recents = recents.filter(c => c !== cat)
  recents.unshift(cat)
  recents = recents.slice(0, 4)
  recentCategories.value = recents
  localStorage.setItem('kb_recent_cats', JSON.stringify(recents))
}

function saveCustomCats() {
  localStorage.setItem('kb_custom_cats', JSON.stringify(customCategories.value))
}

function isCustomCategory(name: string): boolean {
  return customCategories.value.includes(name)
}

function addCustomCategory() {
  const name = newCatName.value.trim()
  if (!name) return
  if (customCategories.value.includes(name) || categories.value.includes(name)) {
    ElMessage.warning('分类已存在')
    return
  }
  customCategories.value.push(name)
  saveCustomCats()
  newCatName.value = ''
  ElMessage.success('分类已添加')
}

function deleteCustomCategoryByName(name: string) {
  if (categories.value.includes(name)) {
    ElMessage.warning('该分类正在被知识库使用，无法删除')
    return
  }
  customCategories.value = customCategories.value.filter(c => c !== name)
  saveCustomCats()
  ElMessage.success('分类已删除')
}

async function loadCategories() {
  try {
    const res = await fetchCategoriesApi()
    categories.value = (res || []).map(c => c.name)
  } catch {
    categories.value = []
  }
}

async function loadKbList() {
  loading.value = true
  try {
    const res = await listKbApi(currentPage.value, pageSize.value, activeCategoryFilter.value)
    kbList.value = res.records || []
    total.value = res.total || 0
  } catch {
    kbList.value = []
  } finally {
    loading.value = false
  }
}

function handlePageChange(page: number) {
  currentPage.value = page
  loadKbList()
}

function handleSizeChange(size: number) {
  pageSize.value = size
  currentPage.value = 1
  loadKbList()
}

function filterByCategory(cat: string) {
  activeCategoryFilter.value = cat
  showCatDialog.value = false
  currentPage.value = 1
  loadKbList()
}

function clearCategoryFilter() {
  activeCategoryFilter.value = ''
  currentPage.value = 1
  loadKbList()
}

async function openCatDialog() {
  showCatDialog.value = true
  catSearchQuery.value = ''
  await loadCategories()
}

function openCreateDialog() {
  createForm.value = { name: '', description: '', category: '' }
  createFormRef.value?.resetFields()
  showCreateDialog.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return

  creating.value = true
  try {
    await createKbApi(createForm.value)
    showCreateDialog.value = false
    addRecentCategory(createForm.value.category || '')
    await loadKbList()
    createForm.value = { name: '', description: '', category: '' }
    await loadCategories()
    ElMessage.success('创建成功')
  } catch (e: any) {
    ElMessage.error(e.message || '创建失败')
  } finally {
    creating.value = false
  }
}

function showMenu(kb: KnowledgeBaseVO, event: MouseEvent) {
  selectedKb.value = kb
  menuX.value = event.clientX || 0
  menuY.value = event.clientY || 0
  menuVisible.value = true
  document.addEventListener('click', closeMenu, { once: true })
}

function closeMenu() {
  menuVisible.value = false
}

async function handleEditCategory() {
  menuVisible.value = false
  if (!selectedKb.value) return
  try {
    const { value: newCat } = await ElMessageBox.prompt(
      '请输入新分类',
      '编辑分类 - ' + selectedKb.value.name,
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        inputValue: selectedKb.value.category || '',
        inputErrorMessage: '分类名不能为空'
      }
    )
    if (newCat !== null && newCat.trim() !== (selectedKb.value.category || '')) {
      const trimmed = newCat.trim() || undefined
      await updateKbApi(selectedKb.value.id, { category: trimmed })
      selectedKb.value.category = trimmed || ''
      addRecentCategory(trimmed || '')
      ElMessage.success('分类已更新')
      await loadCategories()
    }
  } catch {
    // cancelled
  }
}

async function handleRename() {
  menuVisible.value = false
  if (!selectedKb.value) return
  try {
    const { value: newName } = await ElMessageBox.prompt('请输入新名称', '重命名知识库', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputValue: selectedKb.value.name,
      inputPattern: /^.+$/,
      inputErrorMessage: '名称不能为空'
    })
    if (newName && newName !== selectedKb.value.name) {
      await updateKbApi(selectedKb.value.id, { name: newName })
      selectedKb.value.name = newName
      ElMessage.success('重命名成功')
    }
  } catch {
    // cancelled or closed
  }
}

async function handleDelete() {
  menuVisible.value = false
  if (!selectedKb.value) return
  try {
    await ElMessageBox.confirm(
      `确定删除「${selectedKb.value.name}」？文档和切片数据将被清除。`,
      '删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
    const deletedCat = selectedKb.value.category
    await deleteKbApi(selectedKb.value.id)
    // Preserve category in localStorage
    if (deletedCat && !customCategories.value.includes(deletedCat)) {
      customCategories.value.push(deletedCat)
      saveCustomCats()
    }
    ElMessage.success('已删除')
    if (kbList.value.length === 1 && currentPage.value > 1) {
      currentPage.value--
    }
    await loadKbList()
  } catch {
    // cancelled
  }
}


async function toggleExpand(kbId: number, event: Event) {
  event.stopPropagation()
  if (expandedKbId.value === kbId) {
    expandedKbId.value = null
    expandedDocs.value = []
    return
  }
  expandedKbId.value = kbId
  expandedLoading.value = true
  try {
    const res = await listDocsApi(kbId, 1, 20)
    expandedDocs.value = res.records || []
  } catch {
    expandedDocs.value = []
  } finally {
    expandedLoading.value = false
  }
}

function goToDoc(docId: number, event: Event) {
  event.stopPropagation()
  router.push('/doc/' + docId)
}
function goToDetail(id: number) {
  router.push(`/kb/${id}`)
}

function formatTime(iso: string): string {
  if (!iso) return ''
  return iso.slice(0, 10)
}

function statusTooltip(status: number): string {
  if (status === 0) return "知识库为空，请上传文档"
  if (status === 1) return "正在解析文档内容"
  return "知识库已就绪，可进行问答"
}

function statusClass(status: number): string {
  if (status === 0) return 'empty'
  if (status === 1) return 'processing'
  return 'ready'
}

function statusText(status: number): string {
  if (status === 0) return '空'
  if (status === 1) return '处理中'
  return '就绪'
}
</script>

<style scoped>
.kb-page {
  max-width: 1100px;
  margin: 0 auto;
  padding: 32px 24px 80px;
}

.kb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.kb-header-left {
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.kb-title {
  font-size: 22px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.kb-categories-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 24px;
}

.active-filter-tag {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  background: rgba(99, 102, 241, 0.12);
  color: var(--color-accent);
  border-radius: 6px;
  font-size: 12px;
  font-weight: 500;
}

.filter-clear-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border: none;
  background: transparent;
  color: var(--color-accent);
  cursor: pointer;
  border-radius: 50%;
  transition: background 0.15s;
}

.filter-clear-btn:hover {
  background: rgba(99, 102, 241, 0.2);
}

.manage-cat-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  font-size: 12px;
  color: var(--color-text-tertiary);
  background: transparent;
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  transition: all 0.15s;
}

.manage-cat-btn:hover {
  color: var(--color-accent);
  border-color: var(--color-accent);
}

.cat-search-row {
  margin-bottom: 12px;
}

.cat-search-input {
  width: 100%;
  padding: 8px 12px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  color: var(--color-text-primary);
  font-size: 13px;
  outline: none;
  transition: border-color 0.15s;
}

.cat-search-input::placeholder {
  color: var(--color-text-tertiary);
}

.cat-search-input:focus {
  border-color: var(--color-accent);
}

.cat-manage-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 260px;
  overflow-y: auto;
  margin-bottom: 16px;
}

.cat-empty {
  text-align: center;
  padding: 24px;
  color: var(--color-text-tertiary);
  font-size: 13px;
}

.cat-manage-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: var(--color-bg-tertiary);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}

.cat-manage-item:hover {
  background: var(--color-bg-secondary);
}

.cat-manage-name {
  font-size: 13px;
  color: var(--color-text-primary);
}

.cat-used-tag {
  font-size: 11px;
  color: var(--color-text-tertiary);
  padding: 2px 6px;
  background: var(--color-bg-secondary);
  border-radius: 4px;
}

.cat-delete-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: none;
  background: transparent;
  color: var(--color-text-tertiary);
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.15s;
}

.cat-delete-btn:hover {
  background: rgba(239, 68, 68, 0.1);
  color: var(--color-error);
}

.cat-add-row {
  display: flex;
  gap: 8px;
}

.cat-add-row :deep(.el-input__wrapper) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  box-shadow: none !important;
}

.cat-add-row :deep(.el-input__inner) {
  color: var(--color-text-primary);
}

.kb-count {
  font-size: 13px;
  color: var(--color-text-tertiary);
}

.create-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 9px 18px;
  background: var(--color-accent);
  color: #fff;
  border: none;
  border-radius: var(--radius-md, 8px);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.2s;
}

.create-btn:hover {
  opacity: 0.85;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80px 0;
  text-align: center;
}

.empty-icon {
  margin-bottom: 16px;
}

.empty-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--color-text-primary);
  margin-bottom: 6px;
}

.empty-desc {
  font-size: 13px;
  color: var(--color-text-secondary);
  margin-bottom: 20px;
}

.kb-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.loading-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.skeleton-card {
  height: 200px;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  animation: pulse 1.5s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 0.4; }
  50% { opacity: 0.7; }
}

.kb-card {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg, 12px);
  padding: 20px;
  cursor: pointer;
  transition: all 0.2s;
  display: flex;
  flex-direction: column;
}

.kb-card:hover {
  border-color: var(--color-accent);
  transform: translateY(-2px);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.3);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.card-icon {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(99, 102, 241, 0.12);
  border-radius: var(--radius-md, 8px);
  color: var(--color-accent);
}

.card-menu {
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
  transition: all 0.15s;
}

.kb-card:hover .card-menu {
  opacity: 1;
}

.card-menu:hover {
  background: var(--color-bg-tertiary);
  color: var(--color-text-primary);
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 4px;
}

.card-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  flex: 1;
  min-width: 0;
}

.card-category {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
  background: rgba(99, 102, 241, 0.1);
  color: var(--color-accent);
  white-space: nowrap;
  flex-shrink: 0;
}

.card-desc {
  font-size: 12px;
  color: var(--color-text-tertiary);
  margin-bottom: 16px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 32px;
}

.card-stats {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 14px;
}

.stat-item {
  display: flex;
  align-items: baseline;
  gap: 4px;
}

.stat-value {
  font-size: 18px;
  font-weight: 700;
  color: var(--color-text-primary);
}

.stat-label {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.stat-divider {
  width: 1px;
  height: 24px;
  background: var(--color-border);
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: auto;
  padding-top: 12px;
  border-top: 1px solid var(--color-border);
}

.card-time {
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.card-status {
  font-size: 11px;
  font-weight: 500;
  padding: 2px 8px;
  border-radius: 10px;
}

.card-status.empty {
  background: rgba(82, 82, 91, 0.2);
  color: var(--color-text-tertiary);
}

.card-status.processing {
  background: rgba(245, 158, 11, 0.15);
  color: var(--color-warning);
}

.card-status.ready {
  background: rgba(16, 185, 129, 0.15);
  color: var(--color-success);
}

:deep(.kb-dialog) {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-xl, 16px);
  border: 1px solid var(--color-border);
}

:deep(.kb-dialog .el-dialog__header) {
  padding: 24px 24px 0;
}

:deep(.kb-dialog .el-dialog__title) {
  color: var(--color-text-primary);
  font-size: 18px;
  font-weight: 600;
}

:deep(.kb-dialog .el-dialog__body) {
  padding: 20px 24px;
}

:deep(.kb-dialog .el-dialog__footer) {
  padding: 0 24px 24px;
}

:deep(.kb-dialog .el-input__wrapper),
:deep(.kb-dialog .el-textarea__inner) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  box-shadow: none !important;
}

:deep(.kb-dialog .el-input__wrapper.is-focus),
:deep(.kb-dialog .el-textarea__inner:focus) {
  border-color: var(--color-accent);
}

:deep(.kb-dialog .el-input__inner) {
  color: var(--color-text-primary);
}

:deep(.kb-dialog .el-form-item__label) {
  color: var(--color-text-secondary);
  font-size: 13px;
}

.context-menu {
  position: fixed;
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  padding: 4px;
  z-index: 2000;
  min-width: 140px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.4);
}

.context-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  font-size: 13px;
  color: var(--color-text-primary);
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.1s;
}

.context-item:hover {
  background: var(--color-bg-tertiary);
}

.context-item.danger {
  color: var(--color-error);
}

.context-item.danger:hover {
  background: rgba(239, 68, 68, 0.1);
}

/* Pagination */
.kb-pagination {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 32px 0 16px;
  flex-wrap: wrap;
}

.kb-total-label {
  font-size: 13px;
  color: var(--color-text-secondary);
  white-space: nowrap;
}

.kb-page-size-select {
  display: inline-flex;
  align-items: center;
  position: relative;
}

.page-size-dropdown {
  width: 150px;
}

.page-size-dropdown :deep(.el-input__wrapper) {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  box-shadow: none !important;
}

.page-size-dropdown :deep(.el-input__inner) {
  color: var(--color-text-secondary);
  font-size: 13px;
  text-align: center;
}

.page-size-dropdown :deep(.el-select-dropdown__item) {
  text-align: center;
  font-size: 13px;
}

.page-size-dropdown :deep(.el-select__caret) {
  color: var(--color-text-tertiary);
}

.kb-pagination :deep(.el-pagination) {
  --el-pagination-bg-color: var(--color-bg-secondary);
  --el-pagination-text-color: var(--color-text-secondary);
  --el-pagination-button-color: var(--color-text-secondary);
  --el-pagination-button-disabled-color: var(--color-text-tertiary);
  --el-pagination-hover-color: var(--color-accent);
}

.kb-pagination :deep(.el-pagination .btn-prev),
.kb-pagination :deep(.el-pagination .btn-next) {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
}

.kb-pagination :deep(.el-pagination .el-pager li) {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  color: var(--color-text-secondary);
  font-size: 13px;
}

.kb-pagination :deep(.el-pagination .el-pager li.is-active) {
  background: var(--color-accent);
  border-color: var(--color-accent);
  color: #fff;
}


/* Expand documents */
.card-expand {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 0 0;
  margin-top: 8px;
  border-top: 1px solid var(--color-border);
  font-size: 12px;
  color: var(--color-text-secondary);
  cursor: pointer;
  transition: color 0.2s;
}
.card-expand:hover {
  color: var(--color-accent);
}
.expand-icon {
  transition: transform 0.2s;
}
.expand-icon.rotated {
  transform: rotate(180deg);
}
.expand-docs {
  margin-top: 4px;
  border-top: 1px solid var(--color-border);
  padding-top: 8px;
}
.expand-loading, .expand-empty {
  font-size: 12px;
  color: var(--color-text-tertiary);
  padding: 8px 0;
  text-align: center;
}
.expand-doc-list {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.expand-doc-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: pointer;
  transition: background 0.15s;
}
.expand-doc-item:hover {
  background: var(--color-bg-tertiary);
}
.expand-doc-name {
  flex: 1;
  font-size: 13px;
  color: var(--color-text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.expand-doc-status {
  font-size: 11px;
  padding: 1px 6px;
  border-radius: 8px;
  white-space: nowrap;
}
.expand-doc-status.ready {
  background: rgba(16, 185, 129, 0.15);
  color: var(--color-success);
}
.expand-doc-status.parsing {
  background: rgba(245, 158, 11, 0.15);
  color: var(--color-warning);
}
.expand-doc-status.pending {
  background: rgba(82, 82, 91, 0.2);
  color: var(--color-text-tertiary);
}
.expand-doc-size {
  font-size: 11px;
  color: var(--color-text-tertiary);
  white-space: nowrap;
}
</style>
