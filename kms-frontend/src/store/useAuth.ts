import { create } from 'zustand'

const TOKEN_KEY = 'kms_token'
const EMAIL_HASH_KEY = 'kms_email_hash'

interface AuthState {
  token: string | null
  emailHash: string | null
  isAuthenticated: boolean
  login: (token: string, emailHash: string) => void
  logout: () => void
}

const storedToken = localStorage.getItem(TOKEN_KEY)
const storedEmailHash = localStorage.getItem(EMAIL_HASH_KEY)
const hasPersistedSession = !!(storedToken && storedEmailHash)

const useAuth = create<AuthState>((set) => ({
  token: hasPersistedSession ? storedToken : null,
  emailHash: hasPersistedSession ? storedEmailHash : null,
  isAuthenticated: hasPersistedSession,

  login: (token: string, emailHash: string) => {
    localStorage.setItem(TOKEN_KEY, token)
    localStorage.setItem(EMAIL_HASH_KEY, emailHash)
    set({ token, emailHash, isAuthenticated: true })
  },

  logout: () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(EMAIL_HASH_KEY)
    set({ token: null, emailHash: null, isAuthenticated: false })
  },
}))

export default useAuth
