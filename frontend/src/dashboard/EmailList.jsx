import { formatDayMedium, formatTime } from '../format.js'

const STATUS_NOTE = {
  LOGGED: 'Logged only (demo mode, not sent)',
  SENT: 'Sent',
  FAILED: 'Failed, will retry',
}

export default function EmailList({ emails }) {
  if (emails.length === 0) {
    return <p className="empty">No emails yet. Confirmations go out within a minute of a booking.</p>
  }
  return (
    <ul className="email-list">
      {emails.map((e) => (
        <li key={e.id}>
          <details>
            <summary>
              <span className={`tag tag--${e.kind.toLowerCase()}`}>{e.kind === 'REMINDER' ? 'Reminder' : 'Confirmation'}</span>
              <span className="email-list__to">{e.recipient}</span>
              <time className="email-list__time">{formatTime(e.createdAt)}, {formatDayMedium(e.createdAt.slice(0, 10))}</time>
            </summary>
            <div className="email-list__body">
              <p className="email-list__subject">{e.subject}</p>
              <pre>{e.body}</pre>
              <p className="muted">{STATUS_NOTE[e.status] || e.status}</p>
            </div>
          </details>
        </li>
      ))}
    </ul>
  )
}
