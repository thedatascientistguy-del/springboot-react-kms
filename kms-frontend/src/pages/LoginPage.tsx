import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../services/api'
import useAuth from '../store/useAuth'

interface LoginForm {
  email: string
  password: string
}

function validate(form: LoginForm): string | null {
  if (!form.email || !form.password) return 'All fields are required.'
  const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRe.test(form.email)) return 'Please enter a valid email address.'
  return null
}

export default function LoginPage() {
  const navigate = useNavigate()
  const login = useAuth((s) => s.login)

  const [form, setForm] = useState<LoginForm>({ email: '', password: '' })
  const [error, setError] = useState<string | null>(null)
  const [loading, setLoading] = useState(false)

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    setForm((prev) => ({ ...prev, [e.target.name]: e.target.value }))
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    setError(null)
    const validationError = validate(form)
    if (validationError) { setError(validationError); return }
    setLoading(true)
    try {
      const response = await api.post('/api/clients/login', { email: form.email, password: form.password })
      const { token, emailHash } = response.data
      login(token, emailHash)
      navigate('/dashboard')
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr.response?.data?.message ?? 'Invalid email or password.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <div className="glass" style={styles.card}>
        <h1 style={styles.title}><span className="neon-text">Sign In</span></h1>
        <p style={styles.subtitle}>Access your encrypted vault</p>
        <form onSubmit={handleSubmit} noValidate style={styles.form}>
          <div style={styles.field}>
            <label htmlFor="email" style={styles.label}>Email</label>
            <input id="email" name="email" type="email" autoComplete="email"
              value={form.email} onChange={handleChange} style={styles.input} placeholder="you@example.com" />
          </div>
          <div style={styles.field}>
            <label htmlFor="password" style={styles.label}>Password</label>
            <input id="password" name="password" type="password" autoComplete="current-password"
              value={form.password} onChange={handleChange} style={styles.input} placeholder="••••••••" />
          </div>
          {error && <p style={styles.error}>{error}</p>}
          <button type="submit" disabled={loading}
            style={{ ...styles.button, opacity: loading ? 0.7 : 1, cursor: loading ? 'not-allowed' : 'pointer' }}>
            {loading ? 'Signing in…' : 'Sign In'}
          </button>
        </form>
        <p style={styles.footer}>
          Don't have an account?{' '}
          <Link to="/signup" style={styles.link}>Create one</Link>
        </p>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { minHeight: '100dvh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-6)' },
  card: { width: '100%', maxWidth: '420px', padding: 'var(--space-10)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' },
  title: { fontSize: '2rem', fontWeight: 700, textAlign: 'center' },
  subtitle: { color: 'var(--color-text-secondary)', textAlign: 'center', marginBottom: 'var(--space-2)' },
  form: { display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' },
  field: { display: 'flex', flexDirection: 'column', gap: 'var(--space-2)' },
  label: { fontSize: '0.875rem', color: 'var(--color-text-secondary)', fontWeight: 500 },
  input: { background: 'rgba(255,255,255,0.06)', border: '1px solid var(--glass-border)', borderRadius: 'var(--radius-md)', color: 'var(--color-text-primary)', padding: 'var(--space-3) var(--space-4)', outline: 'none' },
  button: { marginTop: 'var(--space-2)', padding: 'var(--space-3) var(--space-6)', background: 'var(--gradient-button)', border: 'none', borderRadius: 'var(--radius-md)', color: 'var(--color-text-inverse)', fontWeight: 700, fontSize: '1rem' },
  error: { color: 'var(--color-neon-pink)', fontSize: '0.875rem', textAlign: 'center' },
  footer: { textAlign: 'center', color: 'var(--color-text-secondary)', fontSize: '0.875rem', marginTop: 'var(--space-2)' },
  link: { color: 'var(--color-neon-cyan)', textDecoration: 'none', fontWeight: 600 },
}
