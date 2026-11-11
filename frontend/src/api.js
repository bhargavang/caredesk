// Thin wrapper around fetch. Staff calls rely on the HttpOnly session cookie the
// server sets at login: this code never sees or stores the token.
const BASE = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '')

export class ApiError extends Error {
  constructor(status, problem) {
    super(problem?.detail || problem?.title || `Request failed (${status})`)
    this.status = status
    this.problem = problem
  }
}

// The hosted demo's API sleeps when idle and takes up to a minute to wake. Requests that run
// longer than this are reported to the "waking up" notice, and reads are retried meanwhile.
const SLOW_AFTER_MS = 3500
const RETRY_FOR_MS = 90_000
const RETRYABLE = new Set([502, 503, 504])

let slowRequests = 0
const slowListeners = new Set()
function setSlow(delta) {
  slowRequests += delta
  slowListeners.forEach((listener) => listener(slowRequests > 0))
}

/** Calls back with true while any request is taking unusually long. Returns an unsubscribe function. */
export function onSlowServer(listener) {
  slowListeners.add(listener)
  return () => slowListeners.delete(listener)
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

async function request(path, { method = 'GET', body, staff = false, quiet = false } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  // The server only accepts the session cookie on writes that carry this header,
  // which other websites can't add. That's the CSRF guard.
  if (staff) headers['X-CareDesk-Client'] = 'web'

  let slow = false
  const timer = quiet ? null : setTimeout(() => { slow = true; setSlow(1) }, SLOW_AFTER_MS)
  const started = Date.now()
  let res
  try {
    // Only reads are retried: repeating a booking or login could do it twice.
    for (let attempt = 1; ; attempt++) {
      let failed = false
      try {
        res = await fetch(BASE + path, {
          method,
          headers,
          body: body === undefined ? undefined : JSON.stringify(body),
          credentials: 'same-origin',
        })
        failed = RETRYABLE.has(res.status)
      } catch {
        failed = true
        res = null
      }
      if (!failed || method !== 'GET' || Date.now() - started > RETRY_FOR_MS) break
      await wait(Math.min(1500 * attempt, 6000))
    }
  } finally {
    clearTimeout(timer)
    if (slow) setSlow(-1)
  }
  if (!res) {
    throw new ApiError(0, { detail: "Can't reach the server. Check your connection and try again." })
  }
  if (res.status === 204) return null
  const data = await res.json().catch(() => null)
  if (!res.ok) throw new ApiError(res.status, data)
  return data
}

const q = (params) =>
  new URLSearchParams(Object.entries(params).filter(([, v]) => v !== undefined && v !== null && v !== '')).toString()

export const api = {
  /** Starts waking a sleeping server in the background, without showing the notice. */
  warmUp: () => request('/api/public/clinic', { quiet: true }).catch(() => {}),
  clinic: () => request('/api/public/clinic'),
  services: () => request('/api/public/services'),
  staff: () => request('/api/public/staff'),
  slots: (serviceId, date, staffId) => request(`/api/public/slots?${q({ serviceId, date, staffId })}`),
  book: (booking) => request('/api/public/appointments', { method: 'POST', body: booking }),
  lookup: (reference) => request(`/api/public/appointments/${encodeURIComponent(reference)}`),

  login: (email, password) => request('/api/auth/login', { method: 'POST', body: { email, password } }),
  logout: () => request('/api/auth/logout', { method: 'POST' }),
  me: () => request('/api/admin/me', { staff: true }),
  appointments: (date) => request(`/api/admin/appointments?${q({ date })}`, { staff: true }),
  setStatus: (id, status) =>
    request(`/api/admin/appointments/${id}/status`, { method: 'PATCH', body: { status }, staff: true }),
  stats: () => request('/api/admin/stats/daily', { staff: true }),
  emails: () => request('/api/admin/emails?limit=12', { staff: true }),
}
