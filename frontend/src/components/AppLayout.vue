<template>
  <div class="app-layout">
    <header class="top-nav">
      <div class="nav-left">
        <router-link to="/dashboard" class="nav-logo">
          <div class="logo-icon">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"/>
              <path d="M12 16v-4"/>
              <path d="M12 8h.01"/>
            </svg>
          </div>
          <span class="logo-text">RAG</span>
        <div class="theme-toggle" @click.prevent="toggleTheme" :title="isDark ? '切换为日间模式' : '切换为夜间模式'">
          <div class="toggle-track">
            <div class="toggle-thumb" :class="{ light: !isDark }">
              <el-icon :size="14">
                <Sunny v-if="isDark" />
                <Moon v-else />
              </el-icon>
            </div>
          </div>
        </div>
        </router-link>
      </div>

      <div class="nav-center">
        <div class="global-search" @click="openSearch">
          <el-icon :size="16"><Search /></el-icon>
          <span class="search-placeholder">搜索知识库、文档、对话...</span>
          <span class="search-shortcut">Ctrl+K</span>
        </div>
      </div>

      <div class="nav-right">
        <el-dropdown trigger="click" @command="handleCommand">
          <div class="user-avatar">
            <img v-if="avatarUrl" :src="avatarUrl" class="avatar-img-small" />
            <span v-else class="avatar-text">{{ avatarText }}</span>
          </div>
          <template #dropdown>
            <el-dropdown-menu class="nav-dropdown">
              <el-dropdown-item command="profile">
                <el-icon><User /></el-icon>
                个人中心
              </el-dropdown-item>
              <el-dropdown-item command="logout" divided>
                <el-icon><SwitchButton /></el-icon>
                退出登录
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="main-content">
      <router-view />
    </main>

    <el-dialog
      v-model="searchVisible"
      title="全局搜索"
      width="640px"
      top="15vh"
      custom-class="search-dialog"
      :close-on-click-modal="false"
      @opened="focusSearchInput"
    >
      <el-input
        ref="searchInputRef"
        v-model="searchQuery"
        placeholder="搜索知识库、文档、对话..."
        :prefix-icon="Search"
        size="large"
        clearable
        @input="onSearchInput"
        @keyup.enter="doSearch"
      />
      <div class="search-hints">
        <span>在知识库中搜索文档内容</span>
        <span class="hint-key">Enter</span>
      </div>

      <div v-if="searchLoading" class="search-loading">
        <el-icon :size="16" class="loading-spin"><Loading /></el-icon>
        <span>搜索中...</span>
      </div>
      <div v-else-if="searchResults.length > 0" class="search-results">
        <div v-if="searchResults.filter(r => r.type === 'kb').length > 0" class="search-section">
          <div class="search-section-title">知识库</div>
          <div v-for="r in searchResults.filter(r => r.type === 'kb')" :key="'kb' + r.id" class="search-result-item" @click="navigateToResult(r)">
            <el-icon :size="16"><FolderOpened /></el-icon>
            <div class="result-info">
              <span class="result-title">{{ r.title }}</span>
              <span v-if="r.description" class="result-desc">{{ r.description }}</span>
            </div>
          </div>
        </div>
        <div v-if="searchResults.filter(r => r.type === 'doc').length > 0" class="search-section">
          <div class="search-section-title">文档</div>
          <div v-for="r in searchResults.filter(r => r.type === 'doc')" :key="'doc' + r.id" class="search-result-item" @click="navigateToResult(r)">
            <el-icon :size="16"><Document /></el-icon>
            <div class="result-info">
              <span class="result-title">{{ r.title }}</span>
              <span v-if="r.description" class="result-desc">{{ r.description }}</span>
            </div>
          </div>
        </div>
        <div v-if="searchResults.filter(r => r.type === 'chunk').length > 0" class="search-section">
          <div class="search-section-title">文档内容</div>
          <div v-for="r in searchResults.filter(r => r.type === 'chunk')" :key="'chunk' + r.id" class="search-result-item" @click="navigateToResult(r)">
            <el-icon :size="16"><Document /></el-icon>
            <div class="result-info">
              <span class="result-title">{{ r.docName }}</span>
              <span class="result-desc">{{ r.content }}</span>
            </div>
          </div>
        </div>
        <div v-if="searchResults.filter(r => r.type === 'chat').length > 0" class="search-section">
          <div class="search-section-title">对话</div>
          <div v-for="r in searchResults.filter(r => r.type === 'chat')" :key="'chat' + r.id" class="search-result-item" @click="navigateToResult(r)">
            <el-icon :size="16"><ChatDotSquare /></el-icon>
            <div class="result-info">
              <span class="result-title">{{ r.title }}</span>
              <span v-if="r.description" class="result-desc">{{ r.description }}</span>
            </div>
          </div>
        </div>
      </div>
      <div v-else-if="searchQuery && !searchLoading" class="search-empty">
        <span>没有找到相关内容</span>
      </div>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch, onMounted } from "vue";
import { Search, User, SwitchButton, Sunny, Moon, Loading, FolderOpened, Document, ChatDotSquare } from "@element-plus/icons-vue";
import { useAuthStore } from "@/stores/auth";
import { searchApi } from "@/api/dashboard";
import router from "@/router";

const authStore = useAuthStore();
const searchVisible = ref(false);
const searchQuery = ref("");
const searchInputRef = ref();
const searchResults = ref<any[]>([]);
const searchLoading = ref(false);

const avatarUrl = ref('')

// Load avatar from profile
watch(() => authStore.userInfo?.avatar, (val) => {
  avatarUrl.value = val || ''
}, { immediate: true })

const avatarText = computed(() => {
  const name = authStore.userInfo?.nickname || authStore.userInfo?.username || "U";
  return name.charAt(0).toUpperCase();
});

const isDark = ref(true);

function toggleTheme() {
  isDark.value = !isDark.value;
  document.documentElement.classList.toggle("light-theme", !isDark.value);
  localStorage.setItem("theme", isDark.value ? "dark" : "light");
}

onMounted(() => {
  const saved = localStorage.getItem("theme");
  if (saved === "light") {
    isDark.value = false;
    document.documentElement.classList.add("light-theme");
  }
});

function openSearch() {
  searchVisible.value = true;
}

function focusSearchInput() {
  setTimeout(() => searchInputRef.value?.focus(), 100);
}

function onSearchInput(val: string) {
  if (!val) searchResults.value = [];
}

async function doSearch() {
  const q = searchQuery.value.trim();
  if (!q) { searchResults.value = []; return; }
  searchLoading.value = true;
  try {
    searchResults.value = await searchApi(q);
  } catch {
    searchResults.value = [];
  } finally {
    searchLoading.value = false;
  }
}

function navigateToResult(result: any) {
  searchVisible.value = false;
  searchQuery.value = "";
  searchResults.value = [];
  router.push(result.url);
}

function handleCommand(command: string) {
  if (command === "profile") {
    router.push("/profile");
  } else if (command === "logout") {
    authStore.logout();
  }
}

document.addEventListener("keydown", (e: KeyboardEvent) => {
  if ((e.ctrlKey || e.metaKey) && e.key === "k") {
    e.preventDefault();
    searchVisible.value = true;
  }
  if (e.key === "Escape") {
    searchVisible.value = false;
  }
});
</script>

<style scoped>
.app-layout {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: var(--color-bg-primary);
}

.top-nav {
  display: flex;
  align-items: center;
  height: 56px;
  padding: 0 20px;
  border-bottom: 1px solid var(--color-border);
  background: var(--nav-bg);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  z-index: 100;
}

.nav-left { flex-shrink: 0; }

.nav-logo {
  display: flex;
  align-items: center;
  gap: 8px;
  text-decoration: none;
}

.nav-logo .logo-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 32px;
  height: 32px;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  border-radius: var(--radius-md, 8px);
  color: #fff;
}

.nav-logo .logo-text {
  font-size: 16px;
  font-weight: 700;
  color: var(--color-text-primary);
  letter-spacing: -0.3px;
}

.nav-center {
  flex: 1;
  display: flex;
  justify-content: center;
  padding: 0 32px;
}

.global-search {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 480px;
  height: 36px;
  padding: 0 14px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  transition: border-color 0.2s;
  color: var(--color-text-tertiary);
}

.global-search:hover { border-color: var(--color-border-hover); }
.search-placeholder { flex: 1; font-size: 13px; }

.search-shortcut {
  font-size: 11px;
  padding: 2px 6px;
  background: var(--color-bg-elevated);
  border-radius: 4px;
  color: var(--color-text-tertiary);
  font-family: var(--font-mono);
}

.nav-right { flex-shrink: 0; }

.user-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: linear-gradient(135deg, var(--color-accent), var(--color-accent-hover));
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  transition: opacity 0.2s;
}

.user-avatar:hover { opacity: 0.85; }
.avatar-img-small {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  object-fit: cover;
}

.avatar-text { font-size: 13px; font-weight: 600; color: #fff; }

.main-content {
  flex: 1;
  overflow-y: auto;
  overflow-x: hidden;
}

:deep(.search-dialog) {
  background: var(--color-bg-secondary);
  border-radius: var(--radius-xl, 16px);
  border: 1px solid var(--color-border);
}

:deep(.search-dialog .el-dialog__header) { display: none; }
:deep(.search-dialog .el-dialog__body) { padding: 20px; }

:deep(.search-dialog .el-input__wrapper) {
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  box-shadow: none !important;
  height: 48px;
}

:deep(.search-dialog .el-input__wrapper.is-focus) { border-color: var(--color-accent); }
:deep(.search-dialog .el-input__inner) { color: var(--color-text-primary); font-size: 15px; }

.search-hints {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 12px;
  font-size: 12px;
  color: var(--color-text-tertiary);
}

.hint-key {
  padding: 2px 6px;
  background: var(--color-bg-tertiary);
  border-radius: 4px;
  font-family: var(--font-mono);
  font-size: 11px;
}

/* Search results */
.search-section { margin-bottom: 4px; }
.search-section:last-child { margin-bottom: 0; }

.search-section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--color-text-tertiary);
  text-transform: uppercase;
  letter-spacing: 0.5px;
  padding: 8px 12px 4px;
}

.search-results { margin-top: 12px; max-height: 360px; overflow-y: auto; }

.search-result-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 12px;
  border-radius: var(--radius-md, 8px);
  cursor: pointer;
  transition: background 0.15s;
}

.search-result-item:hover { background: var(--color-bg-tertiary); }

.result-type-icon { color: var(--color-text-secondary); flex-shrink: 0; }

.result-info { display: flex; flex-direction: column; min-width: 0; flex: 1; }

.result-title {
  font-size: 13px;
  font-weight: 500;
  color: var(--color-text-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.result-desc { font-size: 11px; color: var(--color-text-tertiary); margin-top: 2px; }

.search-loading {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 16px 12px;
  font-size: 13px;
  color: var(--color-text-secondary);
}

.loading-spin { animation: searchSpin 1s linear infinite; }

@keyframes searchSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}

.search-empty {
  text-align: center;
  padding: 24px 12px;
  font-size: 13px;
  color: var(--color-text-tertiary);
}

:deep(.nav-dropdown) {
  background: var(--color-bg-secondary);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md, 8px);
  padding: 4px;
}

:deep(.nav-dropdown .el-dropdown-menu__item) {
  color: var(--color-text-primary);
  font-size: 13px;
  border-radius: 6px;
  padding: 8px 12px;
}

:deep(.nav-dropdown .el-dropdown-menu__item:hover) { background: var(--color-bg-tertiary); }

:deep(.nav-dropdown .el-dropdown-menu__item .el-icon) {
  margin-right: 6px;
  color: var(--color-text-secondary);
}

:deep(.el-dropdown-menu__item--divided) { border-top-color: var(--color-border); }

/* Theme toggle button */
.theme-toggle {
  display: flex;
  align-items: center;
  cursor: pointer;
  margin-left: 6px;
}

.toggle-track {
  width: 48px;
  height: 26px;
  border-radius: 13px;
  background: var(--color-bg-tertiary);
  border: 1px solid var(--color-border);
  position: relative;
  display: flex;
  align-items: center;
  padding: 0 3px;
}

.toggle-thumb {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: var(--color-accent);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  position: absolute;
  left: 3px;
  transition: transform 0.35s cubic-bezier(0.68, -0.55, 0.265, 1.55),
              background-color 0.25s ease;
}

.toggle-thumb.light { transform: translateX(22px); }
.toggle-track:hover { border-color: var(--color-accent); }
.toggle-thumb .el-icon { transition: transform 0.4s ease; }
.toggle-thumb:hover .el-icon { transform: rotate(360deg); }
</style>
