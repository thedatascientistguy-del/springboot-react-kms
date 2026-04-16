import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import useAuth from '../store/useAuth'
import { useVaultFiles } from '../hooks/useVault'
import UploadDropzone from '../components/UploadDropzone'
import FileGrid from '../components/FileGrid'

const qc = new QueryClient()

function Dashboard() {
  const navigate = useNavigate()
  const { emailHash, logout } = useAuth()
  const { data: files = [], isLoading, isError } = useVaultFiles()

  function handleLogout() {
    logout()
    navigate('/')
  }

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <h1 style={styles.logo}><span className="neon-text">Vault</span></h1>
        <div style={styles.headerRight}>
          <span style={styles.email}>{emailHash?.slice(0, 12)}…</span>
          <button onClick={handleLogout} style={styles.logoutBtn}>Sign Out</button>
        </div>
      </header>

      <main style={styles.main}>
        <section style={styles.section}>
          <h2 style={styles.sectionTitle}>Upload a File</h2>
          <UploadDropzone />
        </section>

        <section style={styles.section}>
          <h2 style={styles.sectionTitle}>My Files</h2>
          {isLoading && <p style={styles.status}>Loading…</p>}
          {isError && <p style={{ ...styles.status, color: 'var(--color-neon-pink)' }}>Failed to load files.</p>}
          {!isLoading && !isError && <FileGrid files={files} />}
        </section>
      </main>
    </div>
  )
}

export default function DashboardPage() {
  return (
    <QueryClientProvider client={qc}>
      <Dashboard />
    </QueryClientProvider>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { minHeight: '100dvh', display: 'flex', flexDirection: 'column' },
  header: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 'var(--space-4) var(--space-8)', borderBottom: '1px solid var(--glass-border)', backdropFilter: 'var(--glass-blur)', position: 'sticky', top: 0, zIndex: 10, background: 'rgba(8,11,20,0.8)' },
  logo: { fontSize: '1.5rem', fontWeight: 800 },
  headerRight: { display: 'flex', alignItems: 'center', gap: 'var(--space-4)' },
  email: { fontSize: '0.85rem', color: 'var(--color-text-muted)', fontFamily: 'monospace' },
  logoutBtn: { background: 'none', border: '1px solid var(--glass-border)', borderRadius: 'var(--radius-md)', color: 'var(--color-text-secondary)', padding: 'var(--space-2) var(--space-4)', cursor: 'pointer', fontSize: '0.875rem' },
  main: { flex: 1, padding: 'var(--space-8)', display: 'flex', flexDirection: 'column', gap: 'var(--space-10)', maxWidth: '1200px', width: '100%', margin: '0 auto' },
  section: { display: 'flex', flexDirection: 'column', gap: 'var(--space-5)' },
  sectionTitle: { fontSize: '1.25rem', fontWeight: 700, color: 'var(--color-text-primary)' },
  status: { color: 'var(--color-text-secondary)', textAlign: 'center', padding: 'var(--space-8)' },
}
