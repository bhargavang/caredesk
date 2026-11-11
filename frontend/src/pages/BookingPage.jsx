import { cloneElement, useEffect, useMemo, useRef, useState } from 'react'
import { Link } from 'react-router'
import { api, ApiError } from '../api.js'
import {
  addDays, dayOfMonth, firstName, formatDayLong, formatPrice, formatTime, hourOf, month, todayIn, weekday,
} from '../format.js'
import { DemoBanner, SiteFooter, SiteHeader } from '../components/SiteChrome.jsx'
import { useDocumentMeta } from '../useDocumentMeta.js'

const DAYS_SHOWN = 14
const ANYONE = 'any'

export default function BookingPage({ initialData }) {
  useDocumentMeta(`Book an appointment | ${initialData?.clinic?.name || 'Juniper Family Clinic'}`)

  const [clinic, setClinic] = useState(initialData?.clinic ?? null)
  const [services, setServices] = useState(initialData?.services ?? null)
  const [staff, setStaff] = useState(initialData?.staff ?? null)
  const [loadError, setLoadError] = useState(null)

  const [serviceId, setServiceId] = useState(null)
  const [staffChoice, setStaffChoice] = useState(ANYONE)
  const [date, setDate] = useState(null)
  const [slots, setSlots] = useState(null)
  const [slotsLoading, setSlotsLoading] = useState(false)
  const [slotsError, setSlotsError] = useState(null)
  const [slot, setSlot] = useState(null)
  const [notice, setNotice] = useState(null)
  const [reloadKey, setReloadKey] = useState(0)
  const [confirmation, setConfirmation] = useState(null)

  const timeRef = useRef(null)
  const detailsRef = useRef(null)
  const doneRef = useRef(null)

  useEffect(() => {
    // Prerendered data means no request is needed yet, so wake the API now rather than when
    // someone picks a service.
    if (clinic && services && staff) { if (initialData) api.warmUp(); return }
    Promise.all([api.clinic(), api.services(), api.staff()])
      .then(([c, s, p]) => { setClinic(c); setServices(s); setStaff(p) })
      .catch((e) => setLoadError(e.message))
  }, [clinic, services, staff])

  const service = services?.find((s) => s.id === serviceId) ?? null
  const people = useMemo(
    () => (staff && serviceId ? staff.filter((p) => p.serviceIds.includes(serviceId)) : []),
    [staff, serviceId],
  )
  const today = clinic ? todayIn(clinic.timezone) : null
  const days = useMemo(
    () => (today ? Array.from({ length: DAYS_SHOWN }, (_, i) => addDays(today, i)) : []),
    [today],
  )

  // Load free slots whenever the service, person or day changes.
  useEffect(() => {
    if (!serviceId || !date) return
    let cancelled = false
    setSlotsLoading(true)
    setSlotsError(null)
    api.slots(serviceId, date, staffChoice === ANYONE ? null : staffChoice)
      .then((result) => { if (!cancelled) setSlots(result) })
      .catch((e) => { if (!cancelled) { setSlots([]); setSlotsError(e.message) } })
      .finally(() => { if (!cancelled) setSlotsLoading(false) })
    return () => { cancelled = true }
  }, [serviceId, staffChoice, date, reloadKey])

  async function jumpToFirstFreeDay(nextServiceId, nextStaff) {
    // Start on the first day that has something free, so nobody lands on an empty Sunday.
    for (const day of days) {
      try {
        const free = await api.slots(nextServiceId, day, nextStaff === ANYONE ? null : nextStaff)
        if (free.length) { setDate(day); return }
      } catch {
        break
      }
    }
    setDate(days[0])
  }

  function chooseService(id) {
    if (id === serviceId) return
    setServiceId(id)
    setStaffChoice(ANYONE)
    setSlot(null)
    setSlots(null)
    setNotice(null)
    setDate(null)
    jumpToFirstFreeDay(id, ANYONE)
    requestAnimationFrame(() => timeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }

  function chooseStaff(choice) {
    setStaffChoice(choice)
    setSlot(null)
    setNotice(null)
    setDate(null)
    jumpToFirstFreeDay(serviceId, choice)
  }

  function chooseSlot(s) {
    setSlot(s)
    setNotice(null)
    requestAnimationFrame(() => detailsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
  }

  function onSlotTaken(message) {
    setSlot(null)
    setNotice(message)
    setReloadKey((k) => k + 1) // reload so the taken time disappears
    timeRef.current?.scrollIntoView({ behavior: 'smooth', block: 'start' })
  }

  function onBooked(result) {
    setConfirmation(result)
    requestAnimationFrame(() => doneRef.current?.focus())
  }

  function startOver() {
    setConfirmation(null)
    setServiceId(null)
    setSlot(null)
    setSlots(null)
    setDate(null)
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  const groups = groupByPartOfDay(slots || [])

  return (
    <div className="page page--public">
      <DemoBanner />
      <SiteHeader clinicName={clinic?.name} />

      <main>
        <section className="hero">
          <div className="container hero__inner">
            <div className="hero__copy">
              <p className="eyebrow">Online booking</p>
              <h1>Book a visit in under a minute.</h1>
              <p className="lead">
                Choose what you need, pick a time that suits you and you&rsquo;re done. You&rsquo;ll only see times
                that are really free, and we&rsquo;ll email you a confirmation and a reminder the day before.
              </p>
            </div>
            <aside className="clinic-card" aria-label="Clinic details">
              <h2 className="clinic-card__name">{clinic?.name || 'Juniper Family Clinic'}</h2>
              <dl>
                <div><dt>Address</dt><dd>{clinic?.address || 'Indiranagar, Bengaluru'}</dd></div>
                <div><dt>Phone</dt><dd>{clinic?.phone || '—'}</dd></div>
              </dl>
            </aside>
          </div>
        </section>

        <div className="container booking">
          {confirmation ? (
            <Confirmation booking={confirmation} focusRef={doneRef} onAgain={startOver} />
          ) : (
            <>
              <Step number="1" title="What do you need?">
                {loadError && <p className="alert alert--error">{loadError}</p>}
                {!services && !loadError && <ServiceSkeleton />}
                {services && (
                  <ul className="service-grid">
                    {services.map((s) => (
                      <li key={s.id}>
                        <button
                          type="button"
                          className="service-card"
                          aria-pressed={s.id === serviceId}
                          onClick={() => chooseService(s.id)}
                        >
                          <span className="service-card__name">{s.name}</span>
                          <span className="service-card__desc">{s.description}</span>
                          <span className="service-card__meta">
                            <span>{s.durationMinutes} min</span>
                            <span aria-hidden="true">·</span>
                            <span>{formatPrice(s.price)}</span>
                          </span>
                        </button>
                      </li>
                    ))}
                  </ul>
                )}
              </Step>

              {service && (
                <div ref={timeRef} className="scroll-target">
                  <Step number="2" title="Who would you like to see?">
                    <div className="chip-row" role="radiogroup" aria-label="Staff member">
                      <Chip selected={staffChoice === ANYONE} onClick={() => chooseStaff(ANYONE)}>
                        <span className="chip__title">Anyone available</span>
                        <span className="chip__sub">Most choice of times</span>
                      </Chip>
                      {people.map((p) => (
                        <Chip key={p.id} selected={staffChoice === p.id} onClick={() => chooseStaff(p.id)}>
                          <span className="chip__title">{p.fullName}</span>
                          <span className="chip__sub">{p.title}</span>
                        </Chip>
                      ))}
                    </div>
                  </Step>

                  <Step number="3" title="Pick a day and time">
                    <div className="day-strip" role="radiogroup" aria-label="Day">
                      {days.map((d) => (
                        <button
                          key={d}
                          type="button"
                          role="radio"
                          aria-checked={d === date}
                          className="day"
                          onClick={() => { setDate(d); setSlot(null); setNotice(null) }}
                        >
                          <span className="day__weekday">{d === today ? 'Today' : weekday(d)}</span>
                          <span className="day__num">{dayOfMonth(d)}</span>
                          <span className="day__month">{month(d)}</span>
                        </button>
                      ))}
                    </div>

                    {notice && <p className="alert alert--warn" role="alert">{notice}</p>}
                    <div className="slots" aria-live="polite" aria-busy={slotsLoading || !date}>
                      {(slotsLoading || !date) && <p className="muted">Finding free times…</p>}
                      {!slotsLoading && date && slotsError && <p className="alert alert--error">{slotsError}</p>}
                      {!slotsLoading && date && !slotsError && slots?.length === 0 && (
                        <p className="empty">Nothing free on {formatDayLong(date)}. Try another day.</p>
                      )}
                      {!slotsLoading && date && groups.map(([label, list]) => (
                        <div key={label} className="slot-group">
                          <h3 className="slot-group__label">{label}</h3>
                          <div className="slot-grid">
                            {list.map((s) => (
                              <button
                                key={`${s.start}-${s.staffId}`}
                                type="button"
                                className="slot"
                                aria-pressed={slot?.start === s.start && slot?.staffId === s.staffId}
                                onClick={() => chooseSlot(s)}
                                title={`With ${s.staffName}`}
                              >
                                {formatTime(s.start)}
                              </button>
                            ))}
                          </div>
                        </div>
                      ))}
                    </div>
                  </Step>
                </div>
              )}

              {service && slot && (
                <div ref={detailsRef} className="scroll-target">
                  <Step number="4" title="Your details">
                    <DetailsForm service={service} slot={slot} onBooked={onBooked} onSlotTaken={onSlotTaken} />
                  </Step>
                </div>
              )}
            </>
          )}
        </div>
      </main>

      <SiteFooter />
    </div>
  )
}

function Step({ number, title, children }) {
  return (
    <section className="step" aria-labelledby={`step-${number}`}>
      <h2 id={`step-${number}`} className="step__title">
        <span className="step__num" aria-hidden="true">{number}</span>
        {title}
      </h2>
      {children}
    </section>
  )
}

function Chip({ selected, onClick, children }) {
  return (
    <button type="button" role="radio" aria-checked={selected} className="chip" onClick={onClick}>
      {children}
    </button>
  )
}

function ServiceSkeleton() {
  return (
    <ul className="service-grid" aria-label="Loading services">
      {Array.from({ length: 6 }, (_, i) => <li key={i} className="skeleton service-skeleton" />)}
    </ul>
  )
}

function groupByPartOfDay(slots) {
  const groups = { Morning: [], Afternoon: [], Evening: [] }
  for (const s of slots) {
    const h = hourOf(s.start)
    groups[h < 12 ? 'Morning' : h < 17 ? 'Afternoon' : 'Evening'].push(s)
  }
  return Object.entries(groups).filter(([, list]) => list.length)
}

function DetailsForm({ service, slot, onBooked, onSlotTaken }) {
  const [form, setForm] = useState({ customerName: '', customerEmail: '', customerPhone: '', notes: '' })
  const [errors, setErrors] = useState({})
  const [submitting, setSubmitting] = useState(false)
  const [formError, setFormError] = useState(null)

  const set = (field) => (e) => setForm((f) => ({ ...f, [field]: e.target.value }))

  function validate() {
    const next = {}
    if (!form.customerName.trim()) next.customerName = 'Please tell us your name'
    if (!/^\S+@\S+\.\S+$/.test(form.customerEmail.trim())) next.customerEmail = 'Please enter a valid email'
    if (form.customerPhone && !/^[0-9+()\-\s]*$/.test(form.customerPhone)) {
      next.customerPhone = 'Only digits, spaces and + ( ) -'
    }
    return next
  }

  async function submit(e) {
    e.preventDefault()
    const found = validate()
    setErrors(found)
    setFormError(null)
    if (Object.keys(found).length) return
    setSubmitting(true)
    try {
      const result = await api.book({
        serviceId: service.id,
        staffId: slot.staffId,
        start: slot.start,
        ...form,
      })
      onBooked(result)
    } catch (err) {
      if (err instanceof ApiError && err.status === 409) {
        onSlotTaken(err.message)
      } else if (err instanceof ApiError && err.status === 400 && err.problem?.errors) {
        setErrors(err.problem.errors)
      } else {
        setFormError(err.message)
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form className="details" onSubmit={submit} noValidate>
      <div className="summary" aria-label="Your choice">
        <p className="summary__what">{service.name}</p>
        <p className="summary__when">{formatDayLong(slot.start.slice(0, 10))}, {formatTime(slot.start)}</p>
        <p className="summary__who">with {slot.staffName} · {service.durationMinutes} min · {formatPrice(service.price)}</p>
      </div>

      <div className="fields">
        <Field label="Full name" error={errors.customerName}>
          <input autoComplete="name" value={form.customerName} onChange={set('customerName')} maxLength={120} />
        </Field>
        <Field label="Email" hint="For your confirmation and reminder" error={errors.customerEmail}>
          <input type="email" autoComplete="email" inputMode="email" value={form.customerEmail}
            onChange={set('customerEmail')} maxLength={160} />
        </Field>
        <Field label="Phone" hint="Optional" error={errors.customerPhone}>
          <input type="tel" autoComplete="tel" inputMode="tel" value={form.customerPhone}
            onChange={set('customerPhone')} maxLength={32} />
        </Field>
        <Field label="Anything we should know?" hint="Optional" error={errors.notes}>
          <textarea rows={3} value={form.notes} onChange={set('notes')} maxLength={500} />
        </Field>
      </div>

      {formError && <p className="alert alert--error" role="alert">{formError}</p>}
      <button className="btn btn--primary btn--lg" type="submit" disabled={submitting}>
        {submitting ? 'Booking…' : `Confirm booking for ${formatTime(slot.start)}`}
      </button>
      <p className="fine-print">No payment now. You can pay at the clinic.</p>
    </form>
  )
}

function Field({ label, hint, error, children }) {
  const id = label.toLowerCase().replace(/[^a-z]+/g, '-')
  return (
    <div className={`field${error ? ' field--error' : ''}`}>
      <label htmlFor={id}>
        {label}
        {hint && <span className="field__hint"> {hint}</span>}
      </label>
      {cloneElement(children, { id, 'aria-invalid': !!error, 'aria-describedby': error ? `${id}-err` : undefined })}
      {error && <p id={`${id}-err`} className="field__error">{error}</p>}
    </div>
  )
}

function Confirmation({ booking, focusRef, onAgain }) {
  return (
    <section className="confirmation" aria-labelledby="done-title">
      <div className="confirmation__tick" aria-hidden="true">
        <svg viewBox="0 0 24 24" width="28" height="28"><path d="M5 12.5l4.5 4.5L19 7.5" fill="none" stroke="currentColor" strokeWidth="2.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
      </div>
      <h2 id="done-title" tabIndex={-1} ref={focusRef}>You&rsquo;re booked, {firstName(booking.customerFirstName)}.</h2>
      <p className="lead">A confirmation email is on its way, and we&rsquo;ll remind you the day before.</p>
      <dl className="confirmation__details">
        <div><dt>Reference</dt><dd className="mono">{booking.reference}</dd></div>
        <div><dt>Service</dt><dd>{booking.serviceName}</dd></div>
        <div><dt>When</dt><dd>{formatDayLong(booking.start.slice(0, 10))}, {formatTime(booking.start)} to {formatTime(booking.end)}</dd></div>
        <div><dt>With</dt><dd>{booking.staffName}, {booking.staffTitle}</dd></div>
        <div><dt>Price</dt><dd>{formatPrice(booking.price)}, paid at the clinic</dd></div>
      </dl>
      <div className="confirmation__actions">
        <Link className="btn btn--ghost" to={`/booking/${booking.reference}`}>View this booking</Link>
        <button className="btn btn--primary" type="button" onClick={onAgain}>Book something else</button>
      </div>
    </section>
  )
}
