export interface DashboardStats {
  kbCount: number
  docCount: number
  chatCount: number
  chunkCount: number
}

export interface RecentActivity {
  id: number
  type: 'kb' | 'doc' | 'chat'
  title: string
  description: string
  time: string
}
