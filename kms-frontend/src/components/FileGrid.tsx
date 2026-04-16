import type { VaultFile } from '../services/vaultApi'
import FileCard from './FileCard'

interface Props {
  files: VaultFile[]
}

export default function FileGrid({ files }: Props) {
  if (!files.length) {
    return (
      <div style={styles.empty}>
        <span style={styles.emptyIcon}>🗄️</span>
        <p style={styles.emptyText}>Your vault is empty</p>
        <p style={styles.emptyHint}>Upload a file above to get started</p>
      </div>
    )
  }

  return (
    <div style={styles.grid}>
      {files.map((f) => (
        <FileCard key={f.id} file={f} />
      ))}
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  grid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(260px, 1fr))', gap: 'var(--space-5)' },
  empty: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-3)', padding: 'var(--space-16)', color: 'var(--color-text-secondary)' },
  emptyIcon: { fontSize: '3rem' },
  emptyText: { fontSize: '1.2rem', fontWeight: 600, color: 'var(--color-text-primary)' },
  emptyHint: { fontSize: '0.9rem' },
}
