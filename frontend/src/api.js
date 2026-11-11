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

async function request(path, { method = 'GET', body, staff = false } = {}) {
  const headers = { Accept: 'application/json' }
  if (body !== undefined) headers['Content-Type'] = 'application/json'
  // The server only accepts the session cookie on writes that carry this header,
  // which other websites can't add. That's the CSRF guard.
  if (staff) headers['X-CareDesk-Client'] = 'web'

  let res
  try {
    res = await fetch(BASE + path, {
      method,
      headers,
      body: body === undefined ? undefined : JSON.stringify(body),
      credentials: 'same-origin',
    })
  } catch {
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
