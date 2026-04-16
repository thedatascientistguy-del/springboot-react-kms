import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import useAuth from '../store/useAuth'
import ConversionWizard from '../components/ConversionWizard'

const qc = new QueryClient()

function ConvertContent() {
  const { isAuthenticated } = useAuth()

  return (
    <div style={styles.page}>
      <header style={styles.header}>
        <Link to="/" style={styles.logo}><span className="neon-text">Vault</span></Link>
        <div style={styles.headerRight}>
          {isAuthenticated ? (
            <Link to="/dashboard" style={styles.navLink}>My Vault</Link>
          ) : (
            <>
              <Link to="/login" style={styles.navLink}>Sign In</Link>
              <Link to="/signup" style={{ ...styles.navLink, ...styles.navLinkPrimary }}>Sign Up</Link>
            </>
          )}
        </div>
      </header>

      <main style={styles.main}>
        <div style={styles.titleBlock}>
          <h1 style={styles.title}><span className="neon-text">File Converter</span></h1>
          <p style={styles.subtitle}>
            {isAuthenticated
              ? 'Convert files and save results directly to your vault.'
              : 'Convert files instantly — no account needed. Results expire after 30 minutes.'}
          </p>
        </div>

        <div className="glass" style={styles.wizardCard}>
          <ConversionWizard />
        </div>
      </main>
    </div>
  )
}

export default function ConvertPage() {
  return (
    <QueryClientProvider client={qc}>
      <ConvertContent />
    </QueryClientProvider>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { minHeight: '100dvh', display: 'flex', flexDirection: 'column' },
  header: { display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: 'var(--space-4) var(--space-8)', borderBottom: '1px solid var(--glass-border)', backdropFilter: 'var(--glass-blur)', background: 'rgba(8,11,20,0.8)', position: 'sticky', top: 0, zIndex: 10 },
  logo: { fontSize: '1.5rem', fontWeight: 800, textDecoration: 'none' },
  headerRight: { display: 'flex', gap: 'var(--space-4)', alignItems: 'center' },
  navLink: { color: 'var(--color-text-secondary)', textDecoration: 'none', fontSize: '0.9rem', fontWeight: 500 },
  navLinkPrimary: { color: 'var(--color-neon-cyan)', border: '1px solid var(--color-neon-cyan)', padding: 'var(--space-2) var(--space-4)', borderRadius: 'var(--radius-full)' },
  main: { flex: 1, display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-10)', padding: 'var(--space-12) var(--space-6)' },
  titleBlock: { textAlign: 'center', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' },
  title: { fontSize: 'clamp(2rem, 5vw, 3rem)', fontWeight: 800 },
  subtitle: { color: 'var(--color-text-secondary)', maxWidth: '520px' },
  wizardCard: { width: '100%', maxWidth: '640px', padding: 'var(--space-10)' },
}
