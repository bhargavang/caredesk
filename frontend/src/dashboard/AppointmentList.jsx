import { formatTime } from '../format.js'
import StatusPill from './StatusPill.jsx'

export default function AppointmentList({ appointments, canMarkOutcome, onChange, busyId }) {
  if (appointments.length === 0) {
    return <p className="empty">No appointments for this day.</p>
  }
  return (
    <ul className="appt-list">
      {appointments.map((a) => (
        <li key={a.id} className={`appt appt--${a.status.toLowerCase().replace('_', '-')}`}>
          <div className="appt__time">
            <span className="appt__start">{formatTime(a.start)}</span>
            <span className="appt__end">to {formatTime(a.end)}</span>
          </div>
          <div className="appt__main">
            <p className="appt__name">{a.customerName}</p>
            <p className="appt__meta">{a.serviceName} <span aria-hidden="true">·</span> {a.staffName}</p>
            <p className="appt__contact">
              <a href={`mailto:${a.customerEmail}`}>{a.customerEmail}</a>
              {a.customerPhone && <> <span aria-hidden="true">·</span> <a href={`tel:${a.customerPhone}`}>{a.customerPhone}</a></>}
            </p>
            {a.notes && <p className="appt__note">{a.notes}</p>}
          </div>
          <div className="appt__side">
            <StatusPill status={a.status} />
            <Actions a={a} canMarkOutcome={canMarkOutcome} onChange={onChange} busy={busyId === a.id} />
          </div>
        </li>
      ))}
    </ul>
  )
}

function Actions({ a, canMarkOutcome, onChange, busy }) {
  if (a.status === 'CANCELLED') return null
  if (a.status === 'DONE' || a.status === 'NO_SHOW') {
    return (
      <div className="appt__actions">
        <button type="button" className="btn btn--ghost btn--sm" disabled={busy} onClick={() => onChange(a, 'BOOKED')}>
          Undo
        </button>
      </div>
    )
  }
  const cancel = () => {
    if (window.confirm(`Cancel ${a.customerName}'s ${formatTime(a.start)} appointment? The time becomes free to book again.`)) {
      onChange(a, 'CANCELLED')
    }
  }
  return (
    <div className="appt__actions">
      {canMarkOutcome && (
        <>
          <button type="button" className="btn btn--sm btn--done" disabled={busy} onClick={() => onChange(a, 'DONE')}>Done</button>
          <button type="button" className="btn btn--sm btn--ghost" disabled={busy} onClick={() => onChange(a, 'NO_SHOW')}>No-show</button>
        </>
      )}
      <button type="button" className="btn btn--sm btn--ghost btn--danger" disabled={busy} onClick={cancel}>Cancel</button>
    </div>
  )
}
