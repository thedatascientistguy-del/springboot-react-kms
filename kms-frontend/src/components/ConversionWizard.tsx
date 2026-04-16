import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { motion, AnimatePresence } from 'framer-motion'
import { useConvertUpload, useJobStatus } from '../hooks/useConversion'
import { conversionApi } from '../services/conversionApi'
import useAuth from '../store/useAuth'

// Compatible conversion targets per source extension
const COMPAT: Record<string, string[]> = {
  docx: ['pdf'], pdf: ['docx'], csv: ['xlsx'], xlsx: ['csv'], txt: ['pdf'],
  jpg: ['png', 'webp', 'bmp'], jpeg: ['png', 'webp', 'bmp'],
  png: ['jpg', 'webp', 'bmp'], webp: ['jpg', 'png', 'bmp'], bmp: ['jpg', 'png', 'webp'],
  mp3: ['wav', 'flac', 'aac', 'ogg'], wav: ['mp3', 'flac', 'aac', 'ogg'],
  flac: ['mp3', 'wav', 'aac', 'ogg'], aac: ['mp3', 'wav', 'flac', 'ogg'], ogg: ['mp3', 'wav', 'flac', 'aac'],
  mp4: ['avi', 'mov', 'mkv'], avi: ['mp4', 'mov', 'mkv'], mov: ['mp4', 'avi', 'mkv'], mkv: ['mp4', 'avi', 'mov'],
}

function getExt(filename: string) {
  return filename.split('.').pop()?.toLowerCase() ?? ''
}

export default function ConversionWizard() {
  const [step, setStep] = useState(0)
  const [file, setFile] = useState<File | null>(null)
  const [targetFormat, setTargetFormat] = useState('')
  const [jobId, setJobId] = useState<string | null>(null)

  const { isAuthenticated } = useAuth()
  const convert = useConvertUpload()
  const { data: job } = useJobStatus(jobId)

  const onDrop = useCallback((accepted: File[]) => {
    if (!accepted.length) return
    setFile(accepted[0])
    setTargetFormat('')
    setStep(1)
  }, [])

  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop, multiple: false })

  async function handleConvert() {
    if (!file || !targetFormat) return
    setStep(2)
    convert.mutate({ file, targetFormat }, {
      onSuccess: (data) => { setJobId(data.jobId); },
      onError: () => setStep(1),
    })
  }

  // Advance to step 3 when job is terminal
  if (step === 2 && job && (job.status === 'DONE' || job.status === 'FAILED')) {
    setTimeout(() => setStep(3), 0)
  }

  async function handleDownload() {
    if (!job?.downloadToken) return
    const blob = await conversionApi.downloadGuestResult(job.downloadToken)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = `converted.${targetFormat}`
    a.click()
    URL.revokeObjectURL(url)
  }

  function reset() { setStep(0); setFile(null); setTargetFormat(''); setJobId(null) }

  const ext = file ? getExt(file.name) : ''
  const targets = COMPAT[ext] ?? []

  return (
    <div style={styles.wizard}>
      {/* Step indicators */}
      <div style={styles.steps}>
        {['Select File', 'Choose Format', 'Converting', 'Done'].map((label, i) => (
          <div key={label} style={{ ...styles.stepDot, ...(i <= step ? styles.stepActive : {}) }}>
            <span style={styles.stepNum}>{i + 1}</span>
            <span style={styles.stepLabel}>{label}</span>
          </div>
        ))}
      </div>

      <AnimatePresence mode="wait">
        {step === 0 && (
          <motion.div key="step0" {...fade} style={styles.stepContent}>
            <div {...getRootProps()} style={{ ...styles.dropzone, borderColor: isDragActive ? 'var(--color-neon-cyan)' : 'var(--glass-border)' }}>
              <input {...getInputProps()} />
              <span style={{ fontSize: '3rem' }}>📂</span>
              <p style={{ color: 'var(--color-text-secondary)' }}>
                {isDragActive ? 'Drop it here…' : 'Drag & drop a file, or click to browse'}
              </p>
            </div>
          </motion.div>
        )}

        {step === 1 && (
          <motion.div key="step1" {...fade} style={styles.stepContent}>
            <p style={styles.fileName}>📄 {file?.name}</p>
            {targets.length === 0 ? (
              <p style={{ color: 'var(--color-neon-pink)' }}>No supported conversions for .{ext}</p>
            ) : (
              <>
                <p style={styles.label}>Convert to:</p>
                <div style={styles.formatGrid}>
                  {targets.map((fmt) => (
                    <button key={fmt} onClick={() => setTargetFormat(fmt)}
                      style={{ ...styles.fmtBtn, ...(targetFormat === fmt ? styles.fmtBtnActive : {}) }}>
                      .{fmt}
                    </button>
                  ))}
                </div>
                <div style={styles.btnRow}>
                  <button onClick={() => setStep(0)} style={styles.secondaryBtn}>← Back</button>
                  <button onClick={handleConvert} disabled={!targetFormat} style={{ ...styles.primaryBtn, opacity: targetFormat ? 1 : 0.5 }}>
                    Convert
                  </button>
                </div>
              </>
            )}
          </motion.div>
        )}

        {step === 2 && (
          <motion.div key="step2" {...fade} style={{ ...styles.stepContent, alignItems: 'center', gap: 'var(--space-6)' }}>
            <motion.div animate={{ rotate: 360 }} transition={{ repeat: Infinity, duration: 1.2, ease: 'linear' }} style={{ fontSize: '3rem' }}>⚙️</motion.div>
            <p style={{ color: 'var(--color-text-secondary)' }}>
              {job?.status === 'PROCESSING' ? 'Converting…' : 'Queued…'}
            </p>
          </motion.div>
        )}

        {step === 3 && (
          <motion.div key="step3" {...fade} style={{ ...styles.stepContent, alignItems: 'center', gap: 'var(--space-6)' }}>
            {job?.status === 'DONE' ? (
              <>
                <span style={{ fontSize: '3rem' }}>✅</span>
                <p style={{ color: 'var(--color-text-primary)', fontWeight: 600 }}>Conversion complete!</p>
                <div style={styles.btnRow}>
                  {job.downloadToken && (
                    <button onClick={handleDownload} style={styles.primaryBtn}>⬇ Download</button>
                  )}
                  {isAuthenticated && job.resultFileId && (
                    <button style={styles.secondaryBtn}>🗄 Saved to Vault</button>
                  )}
                </div>
              </>
            ) : (
              <>
                <span style={{ fontSize: '3rem' }}>❌</span>
                <p style={{ color: 'var(--color-neon-pink)' }}>{job?.errorMessage ?? 'Conversion failed.'}</p>
              </>
            )}
            <button onClick={reset} style={styles.secondaryBtn}>Convert Another File</button>
          </motion.div>
        )}
      </AnimatePresence>
    </div>
  )
}

const fade = { initial: { opacity: 0, y: 12 }, animate: { opacity: 1, y: 0 }, exit: { opacity: 0, y: -12 }, transition: { duration: 0.2 } }

const styles: Record<string, React.CSSProperties> = {
  wizard: { display: 'flex', flexDirection: 'column', gap: 'var(--space-8)' },
  steps: { display: 'flex', gap: 'var(--space-4)', justifyContent: 'center', flexWrap: 'wrap' },
  stepDot: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-1)', opacity: 0.4, transition: 'opacity var(--transition-base)' },
  stepActive: { opacity: 1 },
  stepNum: { width: '28px', height: '28px', borderRadius: '50%', background: 'var(--gradient-button)', display: 'flex', alignItems: 'center', justifyContent: 'center', fontSize: '0.8rem', fontWeight: 700, color: 'var(--color-text-inverse)' },
  stepLabel: { fontSize: '0.75rem', color: 'var(--color-text-secondary)' },
  stepContent: { display: 'flex', flexDirection: 'column', gap: 'var(--space-5)', minHeight: '200px' },
  dropzone: { border: '2px dashed', borderRadius: 'var(--radius-lg)', padding: 'var(--space-12)', display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-3)', cursor: 'pointer', background: 'var(--glass-bg)', backdropFilter: 'var(--glass-blur)' },
  fileName: { fontWeight: 600, color: 'var(--color-text-primary)' },
  label: { color: 'var(--color-text-secondary)', fontSize: '0.9rem' },
  formatGrid: { display: 'flex', flexWrap: 'wrap', gap: 'var(--space-3)' },
  fmtBtn: { padding: 'var(--space-2) var(--space-5)', borderRadius: 'var(--radius-full)', border: '1px solid var(--glass-border)', background: 'var(--glass-bg)', color: 'var(--color-text-secondary)', cursor: 'pointer', fontWeight: 600, transition: 'all var(--transition-fast)' },
  fmtBtnActive: { borderColor: 'var(--color-neon-cyan)', color: 'var(--color-neon-cyan)', background: 'rgba(0,245,255,0.08)', boxShadow: 'var(--shadow-neon-cyan)' },
  btnRow: { display: 'flex', gap: 'var(--space-4)', flexWrap: 'wrap' },
  primaryBtn: { padding: 'var(--space-3) var(--space-8)', background: 'var(--gradient-button)', border: 'none', borderRadius: 'var(--radius-md)', color: 'var(--color-text-inverse)', fontWeight: 700, cursor: 'pointer' },
  secondaryBtn: { padding: 'var(--space-3) var(--space-6)', background: 'none', border: '1px solid var(--glass-border)', borderRadius: 'var(--radius-md)', color: 'var(--color-text-secondary)', cursor: 'pointer' },
}
