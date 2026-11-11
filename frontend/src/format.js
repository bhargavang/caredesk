// The API sends clinic-local times like "2030-03-05T10:30:00". They're formatted
// by hand (not through Date) so a visitor in another time zone still sees clinic time.

const pad = (n) => String(n).padStart(2, '0')

function parts(iso) {
  const [date, time = '00:00'] = iso.split('T')
  const [y, m, d] = date.split('-').map(Number)
  const [h, min] = time.split(':').map(Number)
  return { y, m, d, h, min }
}

const asUtcDate = (isoDate) => {
  const { y, m, d } = parts(isoDate)
  return new Date(Date.UTC(y, m - 1, d))
}

const fmt = (options) => new Intl.DateTimeFormat('en-IN', { timeZone: 'UTC', ...options })
const weekdayShort = fmt({ weekday: 'short' })
const monthShort = fmt({ month: 'short' })
const dayLong = fmt({ weekday: 'long', day: 'numeric', month: 'long' })
const dayMedium = fmt({ weekday: 'short', day: 'numeric', month: 'short' })

export function formatTime(iso) {
  const { h, min } = parts(iso)
  const suffix = h < 12 ? 'am' : 'pm'
  const h12 = h % 12 === 0 ? 12 : h % 12
  return `${h12}:${pad(min)} ${suffix}`
}

export const hourOf = (iso) => parts(iso).h
export const dateOf = (iso) => iso.slice(0, 10)
export const formatDayLong = (isoDate) => dayLong.format(asUtcDate(isoDate))
export const formatDayMedium = (isoDate) => dayMedium.format(asUtcDate(isoDate))
export const weekday = (isoDate) => weekdayShort.format(asUtcDate(isoDate))
export const month = (isoDate) => monthShort.format(asUtcDate(isoDate))
export const dayOfMonth = (isoDate) => parts(isoDate).d

export function todayIn(timeZone) {
  // en-CA formats as YYYY-MM-DD
  return new Intl.DateTimeFormat('en-CA', {
    timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(new Date())
}

export function addDays(isoDate, days) {
  const date = asUtcDate(isoDate)
  date.setUTCDate(date.getUTCDate() + days)
  return `${date.getUTCFullYear()}-${pad(date.getUTCMonth() + 1)}-${pad(date.getUTCDate())}`
}

const rupees = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 0 })
export const formatPrice = (amount) => rupees.format(amount)

export const firstName = (name) => (name || '').trim().split(/\s+/)[0]
