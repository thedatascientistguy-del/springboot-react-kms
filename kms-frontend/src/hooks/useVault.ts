import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { vaultApi } from '../services/vaultApi'

const VAULT_KEY = ['vault', 'files']

export function useVaultFiles() {
  return useQuery({ queryKey: VAULT_KEY, queryFn: vaultApi.listFiles })
}

export function useUploadFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ file, onProgress }: { file: File; onProgress?: (pct: number) => void }) =>
      vaultApi.uploadFile(file, onProgress),
    onSuccess: () => qc.invalidateQueries({ queryKey: VAULT_KEY }),
  })
}

export function useRenameFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, newName }: { id: string; newName: string }) => vaultApi.renameFile(id, newName),
    onSuccess: () => qc.invalidateQueries({ queryKey: VAULT_KEY }),
  })
}

export function useReplaceFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, file }: { id: string; file: File }) => vaultApi.replaceFile(id, file),
    onSuccess: () => qc.invalidateQueries({ queryKey: VAULT_KEY }),
  })
}

export function useDeleteFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) => vaultApi.deleteFile(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: VAULT_KEY }),
  })
}
