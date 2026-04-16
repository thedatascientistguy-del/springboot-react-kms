import api from './api'

export type JobStatus = 'PENDING' | 'PROCESSING' | 'DONE' | 'FAILED'

export interface ConversionJob {
  jobId: string
  status: JobStatus
  sourceFormat: string
  targetFormat: string
  downloadToken: string | null   // guest DONE jobs
  resultFileId: string | null    // authenticated DONE jobs
  errorMessage: string | null
}

export const conversionApi = {
  convertUpload: (file: File, targetFormat: string): Promise<ConversionJob> => {
    const fd = new FormData()
    fd.append('file', file)
    fd.append('targetFormat', targetFormat)
    return api.post<ConversionJob>('/api/convert', fd, {
      headers: { 'Content-Type': 'multipart/form-data' },
    }).then((r) => r.data)
  },

  convertVaultFile: (sourceFileId: string, targetFormat: string): Promise<ConversionJob> =>
    api.post<ConversionJob>(`/api/convert/vault/${sourceFileId}`, null, {
      params: { targetFormat },
    }).then((r) => r.data),

  getJobStatus: (jobId: string): Promise<ConversionJob> =>
    api.get<ConversionJob>(`/api/convert/jobs/${jobId}`).then((r) => r.data),

  downloadGuestResult: (token: string): Promise<Blob> =>
    api.get<Blob>(`/api/convert/download/${token}`, { responseType: 'blob' }).then((r) => r.data),
}
