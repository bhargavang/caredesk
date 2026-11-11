const STATUS = {
  BOOKED: { label: 'Booked', icon: <circle cx="8" cy="8" r="4.5" fill="none" stroke="currentColor" strokeWidth="2" /> },
  DONE: { label: 'Done', icon: <path d="M3.5 8.5l3 3 6-7" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" /> },
  NO_SHOW: { label: 'No-show', icon: <path d="M4.5 4.5l7 7m0-7l-7 7" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /> },
  CANCELLED: { label: 'Cancelled', icon: <path d="M4 8h8" stroke="currentColor" strokeWidth="2" strokeLinecap="round" /> },
}

export default function StatusPill({ status }) {
  const s = STATUS[status]
  return (
    <span className={`pill pill--${status.toLowerCase().replace('_', '-')}`}>
      <svg viewBox="0 0 16 16" width="14" height="14" aria-hidden="true">{s.icon}</svg>
      {s.label}
    </span>
  )
}
