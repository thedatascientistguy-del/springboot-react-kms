import { useQuery, useMutation } from '@tanstack/react-query'
import { conversionApi } from '../services/conversionApi'

const TERMINAL = new Set(['DONE', 'FAILED'])

export function useJobStatus(jobId: string | null) {
  return useQuery({
    queryKey: ['conversion', 'job', jobId],
    queryFn: () => conversionApi.getJobStatus(jobId!),
    enabled: !!jobId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && TERMINAL.has(status) ? false : 2000
    },
  })
}

export function useConvertUpload() {
  return useMutation({
    mutationFn: ({ file, targetFormat }: { file: File; targetFormat: string }) =>
      conversionApi.convertUpload(file, targetFormat),
  })
}

export function useConvertVaultFile() {
  return useMutation({
    mutationFn: ({ sourceFileId, targetFormat }: { sourceFileId: string; targetFormat: string }) =>
      conversionApi.convertVaultFile(sourceFileId, targetFormat),
  })
}
