import { useRef, useState } from 'react'
import type { VaultFile } from '../services/vaultApi'
import { vaultApi } from '../services/vaultApi'
import { useRenameFile, useReplaceFile, useDeleteFile } from '../hooks/useVault'

const CATEGORY_ICONS: Record<VaultFile['category'], string> = {
  DOCUMENT: '📄',
  IMAGE: '🖼️',
  VIDEO: '🎬',
  AUDIO: '🎵',
}

function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

interface Props {
  file: VaultFile
}

export default function FileCard({ file }: Props) {
  const [renaming, setRenaming] = useState(false)
  const [newName, setNewName] = useState(file.filename)
  const replaceRef = useRef<HTMLInputElement>(null)

  const rename = useRenameFile()
  const replace = useReplaceFile()
  const del = useDeleteFile()

  async function handleDownload() {
    const blob = await vaultApi.downloadFile(file.id)
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = file.filename
    a.click()
    URL.revokeObjectURL(url)
  }

  function handleRenameSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (newName.trim() && newName !== file.filename) {
      rename.mutate({ id: file.id, newName: newName.trim() })
    }
    setRenaming(false)
  }

  function handleReplace(e: React.ChangeEvent<HTMLInputElement>) {
    const f = e.target.files?.[0]
    if (f) replace.mutate({ id: file.id, file: f })
  }

  return (
    <div className="glass" style={styles.card}>
      <div style={styles.header}>
        <span style={styles.icon}>{CATEGORY_ICONS[file.category]}</span>
        <div style={styles.meta}>
          {renaming ? (
            <form onSubmit={handleRenameSubmit} style={{ display: 'flex', gap: 'var(--space-2)' }}>
              <input
                value={newName}
                onChange={(e) => setNewName(e.target.value)}
                autoFocus
                style={styles.renameInput}
              />
              <button type="submit" style={styles.iconBtn} title="Save">✓</button>
              <button type="button" onClick={() => setRenaming(false)} style={styles.iconBtn} title="Cancel">✕</button>
            </form>
          ) : (
            <p style={styles.filename} title={file.filename}>{file.filename}</p>
          )}
          <p style={styles.size}>{formatBytes(file.originalSize)}</p>
        </div>
      </div>

      <div style={styles.actions}>
        <button onClick={handleDownload} style={styles.actionBtn} title="Download">⬇</button>
        <button onClick={() => setRenaming(true)} style={styles.actionBtn} title="Rename">✏️</button>
        <button onClick={() => replaceRef.current?.click()} style={styles.actionBtn} title="Replace">🔄</button>
        <button
          onClick={() => { if (confirm(`Delete "${file.filename}"?`)) del.mutate(file.id) }}
          style={{ ...styles.actionBtn, color: 'var(--color-neon-pink)' }}
          title="Delete"
        >🗑</button>
      </div>

      <input ref={replaceRef} type="file" style={{ display: 'none' }} onChange={handleReplace} />
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  card: { padding: 'var(--space-5)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' },
  header: { display: 'flex', gap: 'var(--space-3)', alignItems: 'flex-start' },
  icon: { fontSize: '2rem', flexShrink: 0 },
  meta: { flex: 1, minWidth: 0, display: 'flex', flexDirection: 'column', gap: 'var(--space-1)' },
  filename: { fontWeight: 600, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', color: 'var(--color-text-primary)' },
  size: { fontSize: '0.8rem', color: 'var(--color-text-muted)' },
  actions: { display: 'flex', gap: 'var(--space-2)', justifyContent: 'flex-end' },
  actionBtn: { background: 'none', border: 'none', cursor: 'pointer', fontSize: '1.1rem', padding: 'var(--space-1)', borderRadius: 'var(--radius-sm)', color: 'var(--color-text-secondary)', transition: 'color var(--transition-fast)' },
  iconBtn: { background: 'none', border: '1px solid var(--glass-border)', borderRadius: 'var(--radius-sm)', color: 'var(--color-neon-cyan)', cursor: 'pointer', padding: '2px 6px', fontSize: '0.85rem' },
  renameInput: { flex: 1, background: 'rgba(255,255,255,0.06)', border: '1px solid var(--glass-border-accent)', borderRadius: 'var(--radius-sm)', color: 'var(--color-text-primary)', padding: '2px var(--space-2)', outline: 'none', fontSize: '0.9rem' },
}
