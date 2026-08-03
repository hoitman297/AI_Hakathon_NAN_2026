import { useState } from 'react'
import type { FormEvent } from 'react'
import { Modal } from '../components/Modal'
import { login, signup, type AuthResult } from '../api/authApi'
import './LoginModal.css'

interface LoginModalProps {
  onSuccess: (result: AuthResult) => void
  onClose: () => void
}

export function LoginModal({ onSuccess, onClose }: LoginModalProps) {
  const [mode, setMode] = useState<'login' | 'signup'>('login')
  const [username, setUsername] = useState('')
  const [password, setPassword] = useState('')
  const [nickname, setNickname] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(event: FormEvent) {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      const result = mode === 'login' ? await login(username, password) : await signup(username, password, nickname)
      onSuccess(result)
    } catch (err) {
      setError(err instanceof Error ? err.message : '요청에 실패했습니다.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <Modal title={mode === 'login' ? '로그인' : '회원가입'}>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: 10, minWidth: 260 }}>
        <label>
          아이디
          <input
            className="pixel-input"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoComplete="username"
            required
          />
        </label>
        <label>
          비밀번호
          <input
            className="pixel-input"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
            required
          />
        </label>
        {mode === 'signup' && (
          <label>
            닉네임
            <input className="pixel-input" value={nickname} onChange={(e) => setNickname(e.target.value)} required />
          </label>
        )}

        {error && <p className="pixel-error">{error}</p>}

        <div style={{ display: 'flex', gap: 8, marginTop: 8 }}>
          <button type="submit" className="pixel-button pixel-button--accent" disabled={submitting}>
            {mode === 'login' ? '로그인' : '가입하고 시작하기'}
          </button>
          <button type="button" className="pixel-button" onClick={onClose} disabled={submitting}>
            취소
          </button>
        </div>

        <button
          type="button"
          className="login-mode-toggle"
          onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}
        >
          {mode === 'login' ? '계정이 없으신가요? 회원가입' : '이미 계정이 있으신가요? 로그인'}
        </button>
      </form>
    </Modal>
  )
}
