import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router'
import { api } from '../api.js'
import { addDays, formatDayLong, todayIn } from '../format.js'
import { LogoMark } from '../components/Brand.jsx'
import { useDocumentMeta } from '../useDocumentMeta.js'
import AppointmentList from '../dashboard/AppointmentList.jsx'
import EmailList from '../dashboard/EmailList.jsx'
import StatsChart from '../dashboard/StatsChart.jsx'

export default function DashboardPage() {
  useDocumentMeta('Dashboard | CareDesk', { noindex: true })
  const navigate = useNavigate()

  const [user, setUser] = useState(null)
  const [clinic, setClinic] = useState(null)
  const [staff, setStaff] = useState([])
  const [date, setDate] = useState(null)
  const [appointments, setAppointments] = useState(null)
  const [stats, setStats] = useState(null)
  const [emails, setEmails] = useState(null)
  const [staffFilter, setStaffFilter] = useState('all')
  const [busyId, setBusyId] = useState(null)
  const [error, setError] = useState(null)

  const fail = useCallback((e) => {
    if (e.status === 401) navigate('/staff/login', { replace: true })
    else setError(e.message)
  }, [navigate])

  useEffect(() => {
    Promise.all([api.me(), api.clinic(), api.staff()])
      .then(([u, c, s]) => { setUser(u); setClinic(c); setStaff(s); setDate(todayIn(c.timezone)) })
      .catch(fail)
  }, [fail])

  useEffect(() => {
    if (!date) return undefined
    let stale = false
    setAppointments(null)
    api.appointments(date).then((list) => { if (!stale) setAppointments(list) }).catch(fail)
    return () => { stale = true }
  }, [date, fail])

  const loadStats = useCallback(() => api.stats().then(setStats).catch(fail), [fail])
  const loadEmails = useCallback(() => api.emails().then(setEmails).catch(fail), [fail])

  useEffect(() => {
    if (!user) return undefined
    loadStats()
    loadEmails()
    const timer = setInterval(loadEmails, 30_000)
    return () => clearInterval(timer)
  }, [user, loadStats, loadEmails])

  async function changeStatus(appointment, status) {
    setBusyId(appointment.id)
    setError(null)
    try {
      const updated = await api.setStatus(appointment.id, status)
      setAppointments((list) => list.map((a) => (a.id === updated.id ? updated : a)))
      loadStats()
    } catch (e) {
      fail(e)
    } finally {
      setBusyId(null)
    }
  }

  async function logout() {
    await api.logout().catch(() => {})
    navigate('/staff/login', { replace: true })
  }

  const today = clinic ? todayIn(clinic.timezone) : null
  const visible = useMemo(
    () => (appointments || []).filter((a) => staffFilter === 'all' || a.staffId === Number(staffFilter)),
    [appointments, staffFilter],
  )
  const live = visible.filter((a) => a.status !== 'CANCELLED').length

  if (!user) {
    return <div className="page page--app"><p className="container muted pad-top">Loading…</p></div>
  }

  const s = stats?.summary
  return (
    <div className="page page--app">
      <header className="app-header">
        <div className="container app-header__inner">
          <Link to="/staff" className="brand">
            <LogoMark size={28} />
            <span className="brand__name">CareDesk</span>
            <span className="brand__sub">{clinic?.name}</span>
          </Link>
          <div className="app-header__user">
            <span className="muted">{user.name}</span>
            <button type="button" className="btn btn--ghost btn--sm" onClick={logout}>Log out</button>
          </div>
        </div>
      </header>

      <main className="container app">
        {error && <p className="alert alert--error" role="alert">{error} <button type="button" className="link-btn" onClick={() => setError(null)}>Dismiss</button></p>}

        <section className="tiles" aria-label="Summary">
          <Tile label="Today" value={s?.today} note={s ? `${s.todayRemaining} still to come` : null} />
          <Tile label="Next 7 days" value={s?.next7Days} note="Booked, not counting today" />
          <Tile label="No-show rate" value={s ? `${Math.round(s.noShowRate * 100)}%` : null}
            note={s ? `${s.noShow} of ${s.done + s.noShow} in the last 2 weeks` : null} />
          <Tile label="Cancelled" value={s?.cancelled} note="Last 2 weeks and next 7 days" />
        </section>

        <div className="app__grid">
          <section className="card day-card" aria-labelledby="day-title">
            <div className="day-card__head">
              <div>
                <h1 id="day-title" className="h3">{date === today ? 'Today' : 'Appointments'}</h1>
                <p className="muted">{date && formatDayLong(date)} · {live} {live === 1 ? 'appointment' : 'appointments'}</p>
              </div>
              <div className="day-card__controls">
                <div className="day-nav" role="group" aria-label="Change day">
                  <button type="button" className="btn btn--ghost btn--sm" aria-label="Previous day"
                    onClick={() => setDate((d) => addDays(d, -1))}>‹</button>
                  <button type="button" className="btn btn--ghost btn--sm" disabled={date === today}
                    onClick={() => setDate(today)}>Today</button>
                  <button type="button" className="btn btn--ghost btn--sm" aria-label="Next day"
                    onClick={() => setDate((d) => addDays(d, 1))}>›</button>
                </div>
                <label className="visually-hidden" htmlFor="staff-filter">Show appointments for</label>
                <select id="staff-filter" className="select" value={staffFilter} onChange={(e) => setStaffFilter(e.target.value)}>
                  <option value="all">Everyone</option>
                  {staff.map((p) => <option key={p.id} value={p.id}>{p.fullName}</option>)}
                </select>
              </div>
            </div>
            {appointments === null
              ? <p className="muted">Loading appointments…</p>
              : <AppointmentList appointments={visible} canMarkOutcome={date <= today} onChange={changeStatus} busyId={busyId} />}
          </section>

          <div className="app__side">
            <section className="card" aria-labelledby="chart-title">
              <h2 id="chart-title" className="h3">Appointments per day</h2>
              <p className="muted small">Last 2 weeks and the week ahead</p>
              {stats ? <StatsChart days={stats.days} today={today} /> : <div className="skeleton chart-skeleton" />}
            </section>

            <section className="card" aria-labelledby="emails-title">
              <h2 id="emails-title" className="h3">Recent emails</h2>
              <p className="muted small">Confirmations within a minute of booking, reminders the day before.</p>
              {emails ? <EmailList emails={emails} /> : <p className="muted">Loading…</p>}
            </section>
          </div>
        </div>
      </main>
    </div>
  )
}

function Tile({ label, value, note }) {
  return (
    <div className="tile">
      <p className="tile__label">{label}</p>
      <p className="tile__value">{value ?? '–'}</p>
      {note && <p className="tile__note">{note}</p>}
    </div>
  )
}
