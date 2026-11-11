import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router'
import { api } from '../api.js'
import { formatDayLong, formatPrice, formatTime } from '../format.js'
import { DemoBanner, SiteFooter, SiteHeader } from '../components/SiteChrome.jsx'
import { useDocumentMeta } from '../useDocumentMeta.js'

const STATUS_TEXT = {
  BOOKED: 'Booked',
  DONE: 'Completed',
  NO_SHOW: 'Missed',
  CANCELLED: 'Cancelled',
}

export default function BookingLookupPage() {
  const { reference } = useParams()
  useDocumentMeta(`Booking ${reference}`, { noindex: true })
  const [booking, setBooking] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.lookup(reference).then(setBooking).catch((e) => setError(e.message))
  }, [reference])

  return (
    <div className="page page--public">
      <DemoBanner />
      <SiteHeader />
      <main className="container narrow">
        {error && (
          <section className="card stack">
            <h1 className="h2">We couldn&rsquo;t find that booking</h1>
            <p>{error}. Check the reference in your confirmation email.</p>
            <p><Link className="btn btn--primary" to="/">Make a new booking</Link></p>
          </section>
        )}
        {!error && !booking && <p className="muted">Loading your booking…</p>}
        {booking && (
          <section className="card stack">
            <p className="eyebrow">Booking {booking.reference}</p>
            <h1 className="h2">{booking.serviceName}</h1>
            <dl className="confirmation__details">
              <div><dt>Status</dt><dd>{STATUS_TEXT[booking.status]}</dd></div>
              <div><dt>When</dt><dd>{formatDayLong(booking.start.slice(0, 10))}, {formatTime(booking.start)} to {formatTime(booking.end)}</dd></div>
              <div><dt>With</dt><dd>{booking.staffName}, {booking.staffTitle}</dd></div>
              <div><dt>Price</dt><dd>{formatPrice(booking.price)}, paid at the clinic</dd></div>
            </dl>
            <p className="fine-print">Need to change it? Call the clinic and quote your reference.</p>
          </section>
        )}
      </main>
      <SiteFooter />
    </div>
  )
}
