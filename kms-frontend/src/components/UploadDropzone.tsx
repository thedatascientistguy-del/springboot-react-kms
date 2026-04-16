import { useState, useCallback } from 'react'
import { useDropzone } from 'react-dropzone'
import { useUploadFile } from '../hooks/useVault'

export default function UploadDropzone() {
  const [progress, setProgress] = useState<number | null>(null)
  const [uploadError, setUploadError] = useState<string | null>(null)
  const upload = useUploadFile()

  const onDrop = useCallback(
    (accepted: File[]) => {
      if (!accepted.length) return
      setUploadError(null)
      setProgress(0)
      upload.mutate(
        { file: accepted[0], onProgress: setProgress },
        {
          onSuccess: () => setProgress(null),
          onError: () => {
            setProgress(null)
            setUploadError('Upload failed. Please try again.')
          },
        }
      )
    },
    [upload]
  )

  const { getRootProps, getInputProps, isDragActive } = useDropzone({ onDrop, multiple: false })

  return (
    <div>
      <div
        {...getRootProps()}
        style={{
          ...styles.zone,
          borderColor: isDragActive ? 'var(--color-neon-cyan)' : 'var(--glass-border)',
          background: isDragActive ? 'rgba(0,245,255,0.06)' : 'var(--glass-bg)',
        }}
      >
        <input {...getInputProps()} />
        <span style={styles.icon}>📁</span>
        <p style={styles.text}>
          {isDragActive ? 'Drop it here…' : 'Drag & drop a file, or click to browse'}
        </p>
      </div>

      {progress !== null && (
        <div style={styles.progressWrap}>
          <div style={{ ...styles.progressBar, width: `${progress}%` }} />
          <span style={styles.progressLabel}>{progress}%</span>
        </div>
      )}

      {uploadError && <p style={styles.error}>{uploadError}</p>}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  zone: {
    border: '2px dashed',
    borderRadius: 'var(--radius-lg)',
    padding: 'var(--space-12)',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: 'var(--space-3)',
    cursor: 'pointer',
    transition: 'border-color var(--transition-fast), background var(--transition-fast)',
    backdropFilter: 'var(--glass-blur)',
  },
  icon: { fontSize: '2.5rem' },
  text: { color: 'var(--color-text-secondary)', textAlign: 'center' },
  progressWrap: {
    marginTop: 'var(--space-3)',
    height: '6px',
    background: 'rgba(255,255,255,0.1)',
    borderRadius: 'var(--radius-full)',
    overflow: 'hidden',
    position: 'relative',
  },
  progressBar: {
    height: '100%',
    background: 'var(--gradient-button)',
    borderRadius: 'var(--radius-full)',
    transition: 'width 0.2s ease',
  },
  progressLabel: {
    position: 'absolute',
    right: 0,
    top: '-20px',
    fontSize: '0.75rem',
    color: 'var(--color-text-secondary)',
  },
  error: { color: 'var(--color-neon-pink)', fontSize: '0.875rem', marginTop: 'var(--space-2)' },
}
