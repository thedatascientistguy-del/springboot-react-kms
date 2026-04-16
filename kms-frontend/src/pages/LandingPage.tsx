import { Link } from 'react-router-dom'

const features = [
  { icon: '🔐', title: 'End-to-End Encrypted', desc: 'Every file is encrypted with a unique key before leaving your device.' },
  { icon: '⚡', title: 'Instant Conversion', desc: 'Convert documents, images, audio and video — no account needed.' },
  { icon: '🗄️', title: 'Personal Vault', desc: 'Your files, your keys. Zero-knowledge storage backed by Supabase.' },
  { icon: '🌐', title: 'Guest-Friendly', desc: 'Convert files anonymously. Results auto-expire after 30 minutes.' },
]

export default function LandingPage() {
  return (
    <div style={styles.page}>
      {/* Hero */}
      <section style={styles.hero}>
        <h1 style={styles.heroTitle}>
          <span className="neon-text">Secure Cloud Vault</span>
        </h1>
        <p style={styles.heroSub}>
          Encrypted file storage and format conversion — with or without an account.
        </p>
        <div style={styles.ctaRow}>
          <Link to="/signup" style={{ ...styles.btn, ...styles.btnPrimary }}>
            Store My Files
          </Link>
          <Link to="/convert" style={{ ...styles.btn, ...styles.btnSecondary }}>
            Convert a File — No Login Needed
          </Link>
        </div>
      </section>

      {/* Feature highlights */}
      <section style={styles.features}>
        {features.map((f) => (
          <div key={f.title} className="glass" style={styles.featureCard}>
            <span style={styles.featureIcon}>{f.icon}</span>
            <h3 style={styles.featureTitle}>{f.title}</h3>
            <p style={styles.featureDesc}>{f.desc}</p>
          </div>
        ))}
      </section>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { minHeight: '100dvh', display: 'flex', flexDirection: 'column', alignItems: 'center', padding: 'var(--space-16) var(--space-6)', gap: 'var(--space-16)' },
  hero: { display: 'flex', flexDirection: 'column', alignItems: 'center', gap: 'var(--space-6)', textAlign: 'center', maxWidth: '720px' },
  heroTitle: { fontSize: 'clamp(2.5rem, 6vw, 4rem)', fontWeight: 800, lineHeight: 1.1 },
  heroSub: { fontSize: '1.2rem', color: 'var(--color-text-secondary)', maxWidth: '540px' },
  ctaRow: { display: 'flex', flexWrap: 'wrap', gap: 'var(--space-4)', justifyContent: 'center', marginTop: 'var(--space-4)' },
  btn: { padding: 'var(--space-4) var(--space-8)', borderRadius: 'var(--radius-full)', fontWeight: 700, fontSize: '1rem', textDecoration: 'none', transition: 'opacity var(--transition-fast)' },
  btnPrimary: { background: 'var(--gradient-button)', color: 'var(--color-text-inverse)', boxShadow: 'var(--shadow-neon-cyan)' },
  btnSecondary: { background: 'var(--glass-bg)', border: '1px solid var(--glass-border-accent)', color: 'var(--color-neon-cyan)', backdropFilter: 'var(--glass-blur)' },
  features: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: 'var(--space-6)', width: '100%', maxWidth: '960px' },
  featureCard: { padding: 'var(--space-8)', display: 'flex', flexDirection: 'column', gap: 'var(--space-3)' },
  featureIcon: { fontSize: '2rem' },
  featureTitle: { fontWeight: 700, fontSize: '1.1rem', color: 'var(--color-text-primary)' },
  featureDesc: { color: 'var(--color-text-secondary)', fontSize: '0.9rem', lineHeight: 1.6 },
}
