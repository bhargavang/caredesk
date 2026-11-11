import { useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api.js'
import { LogoMark } from '../components/Brand.jsx'
import { useDocumentMeta } from '../useDocumentMeta.js'

const DEMO = { email: 'demo@caredesk.test', password: 'demo1234' }

export default function LoginPage() {
  useDocumentMeta('Staff login | CareDesk', { noindex: true })
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState(null)
  const [busy, setBusy] = useState(false)

  async function submit(e) {
    e.preventDefault()
    setBusy(true)
    setError(null)
    try {
      await api.login(email.trim(), password)
      navigate('/staff', { replace: true })
    } catch (err) {
      setError(err.message)
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="page page--auth">
      <main className="auth">
        <Link to="/" className="brand brand--center">
          <LogoMark size={40} />
          <span className="brand__name">CareDesk</span>
        </Link>
        <form className="card auth__card" onSubmit={submit}>
          <h1 className="h2">Staff login</h1>
          <div className="field">
            <label htmlFor="email">Email</label>
            <input id="email" type="email" autoComplete="username" value={email}
              onChange={(e) => setEmail(e.target.value)} required />
          </div>
          <div className="field">
            <label htmlFor="password">Password</label>
            <input id="password" type="password" autoComplete="current-password" value={password}
              onChange={(e) => setPassword(e.target.value)} required />
          </div>
          {error && <p className="alert alert--error" role="alert">{error}</p>}
          <button className="btn btn--primary btn--block" type="submit" disabled={busy}>
            {busy ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <div className="demo-login">
          <p><strong>Trying the demo?</strong> Use <span className="mono">{DEMO.email}</span> with password{' '}
            <span className="mono">{DEMO.password}</span>.</p>
          <button type="button" className="btn btn--ghost btn--sm"
            onClick={() => { setEmail(DEMO.email); setPassword(DEMO.password) }}>
            Fill in the demo login
          </button>
        </div>
      </main>
    </div>
  )
}
