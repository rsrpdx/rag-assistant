export interface KnowledgeBaseVO {
  id: number
  name: string
  description: string
  category: string
  userId: number
  status: number
  documentCount: number
  chunkCount: number
  createdAt: string
  updatedAt: string
}

export interface CreateKbRequest {
  name: string
  description?: string
  category?: string
}

export interface DocumentVO {
  id: number
  kbId: number
  originalName: string
  fileSize: number
  fileType: string
  status: number
  chunkCount: number
  errorMsg: string
  createdAt: string
}

export interface ChunkVO {
  content: string
  chunkIndex: number
}

export interface DocumentDetailVO extends DocumentVO {
  filename: string
  fileUrl: string
  userId: number
  updatedAt: string
  chunks: ChunkVO[]
}

export interface KbStatsVO {
  documentCount: number
  chunkCount: number
  chatCount: number
}

export interface CategoryVO {
  name: string
}
