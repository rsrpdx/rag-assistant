import request from './request'
import type { DashboardStats, RecentActivity } from '@/types/dashboard'

export function getDashboardStats(): Promise<DashboardStats> {
  return request.get('/dashboard/stats')
}

export function getRecentActivity(): Promise<RecentActivity[]> {
  return request.get('/dashboard/recent-activity')
}

export function searchApi(keyword: string): Promise<any[]> {
  return request.get('/dashboard/search', { params: { q: keyword } })
}
