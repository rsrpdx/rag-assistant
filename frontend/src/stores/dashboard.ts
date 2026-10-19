import { defineStore } from 'pinia'
import { ref } from 'vue'
import { getDashboardStats, getRecentActivity } from '@/api/dashboard'
import type { DashboardStats, RecentActivity } from '@/types/dashboard'

export const useDashboardStore = defineStore('dashboard', () => {
  const stats = ref<DashboardStats>({
    kbCount: 0,
    docCount: 0,
    chatCount: 0,
    chunkCount: 0
  })
  const recentActivity = ref<RecentActivity[]>([])
  const loading = ref(false)

  async function fetchStats() {
    try {
      stats.value = await getDashboardStats()
    } catch {
      // ignore
    }
  }

  async function fetchRecentActivity() {
    try {
      recentActivity.value = await getRecentActivity()
    } catch {
      // ignore
    }
  }

  async function loadAll() {
    loading.value = true
    await Promise.all([fetchStats(), fetchRecentActivity()])
    loading.value = false
  }

  return { stats, recentActivity, loading, loadAll }
})
