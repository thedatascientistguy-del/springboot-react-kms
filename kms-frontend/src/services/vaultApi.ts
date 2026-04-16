import api from './api'

export interface VaultFile {
  id: string
  filename: string
  contentType: string
  category: 'DOCUMENT' | 'IMAGE' | 'VIDEO' | 'AUDIO'
  originalSize: number
  createdAt: string
  updatedAt: string
}

export const vaultApi = {
  listFiles: (): Promise<VaultFile[]> =>
    api.get<VaultFile[]>('/api/vault/files').then((r) => r.data),

  uploadFile: (file: File, onProgress?: (pct: number) => void): Promise<VaultFile> =>
    api.post<VaultFile>('/api/vault/upload', (() => { const fd = new FormData(); fd.append('file', file); return fd })(), {
      headers: { 'Content-Type': 'multipart/form-data' },
      onUploadProgress: (e) => { if (onProgress && e.total) onProgress(Math.round((e.loaded * 100) / e.total)) },
    }).then((r) => r.data),

  downloadFile: (id: string): Promise<Blob> =>
    api.get<Blob>(`/api/vault/files/${id}/download`, { responseType: 'blob' }).then((r) => r.data),

  renameFile: (id: string, newName: string): Promise<VaultFile> =>
    api.patch<VaultFile>(`/api/vault/files/${id}/rename`, { newName }).then((r) => r.data),

  replaceFile: (id: string, file: File): Promise<VaultFile> =>
    api.put<VaultFile>(`/api/vault/files/${id}/replace`, (() => { const fd = new FormData(); fd.append('file', file); return fd })(), {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then((r) => r.data),

  deleteFile: (id: string): Promise<void> =>
    api.delete(`/api/vault/files/${id}`).then(() => undefined),
}
