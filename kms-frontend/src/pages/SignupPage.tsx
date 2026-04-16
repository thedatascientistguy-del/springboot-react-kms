import { useState } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../services/api'

interface SignupForm {
  name: string
  email: string
  phone: string
  password: string
  confirmPassword: string
}

function validate(form: SignupForm): string | null {
  if (!form.name || !form.email || !form.phone || !form.password || !form.confirmPassword)
    return 'All fields are required.'
  const emailRe = /^[^\s@]+@[^\s@]+\.[^\s@]+$/
  if (!emailRe.test(form.email)) return 'Please enter a valid email address.'
  if (form.password.length < 8) return 'Password must be at least 8 characters.'
  if (form.password !== form.confirmPassword) return 'Passwords do not match.'
  return null
}

export default function SignupPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<SignupForm>({ name: '', email: '', phone: '', password: '', confirmPassword: '' })
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
      await api.post('/api/clients/register', { name: form.name, email: form.email, phone: form.phone, password: form.password })
      navigate('/login', { state: { message: 'Account created! Please sign in.' } })
    } catch (err: unknown) {
      const axiosErr = err as { response?: { data?: { message?: string } } }
      setError(axiosErr.response?.data?.message ?? 'Registration failed. Please try again.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div style={styles.page}>
      <div className="glass" style={styles.card}>
        <h1 style={styles.title}><span className="neon-text">Create Account</span></h1>
        <p style={styles.subtitle}>Your encrypted vault awaits</p>
        <form onSubmit={handleSubmit} noValidate style={styles.form}>
          {(['name', 'email', 'phone'] as const).map((field) => (
            <div key={field} style={styles.field}>
              <label htmlFor={field} style={styles.label}>{field.charAt(0).toUpperCase() + field.slice(1)}</label>
              <input id={field} name={field} type={field === 'email' ? 'email' : field === 'phone' ? 'tel' : 'text'}
                value={form[field]} onChange={handleChange} style={styles.input}
                placeholder={field === 'email' ? 'you@example.com' : field === 'phone' ? '+1 555 000 0000' : 'Jane Doe'} />
            </div>
          ))}
          <div style={styles.field}>
            <label htmlFor="password" style={styles.label}>Password</label>
            <input id="password" name="password" type="password" autoComplete="new-password"
              value={form.password} onChange={handleChange} style={styles.input} placeholder="Min. 8 characters" />
          </div>
          <div style={styles.field}>
            <label htmlFor="confirmPassword" style={styles.label}>Confirm Password</label>
            <input id="confirmPassword" name="confirmPassword" type="password" autoComplete="new-password"
              value={form.confirmPassword} onChange={handleChange} style={styles.input} placeholder="••••••••" />
          </div>
          {error && <p style={styles.error}>{error}</p>}
          <button type="submit" disabled={loading}
            style={{ ...styles.button, opacity: loading ? 0.7 : 1, cursor: loading ? 'not-allowed' : 'pointer' }}>
            {loading ? 'Creating account…' : 'Create Account'}
          </button>
        </form>
        <p style={styles.footer}>
          Already have an account?{' '}
          <Link to="/login" style={styles.link}>Sign in</Link>
        </p>
      </div>
    </div>
  )
}

const styles: Record<string, React.CSSProperties> = {
  page: { minHeight: '100dvh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: 'var(--space-6)' },
  card: { width: '100%', maxWidth: '440px', padding: 'var(--space-10)', display: 'flex', flexDirection: 'column', gap: 'var(--space-4)' },
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
