import { useLayoutEffect, useRef, useState } from 'react'
import { dayOfMonth, formatDayMedium, weekday } from '../format.js'

// Stacked columns: appointments per day. Cancelled ones aren't stacked (they never
// happened) but are listed in the tooltip and the table.
const SERIES = [
  { key: 'done', label: 'Done' },
  { key: 'noShow', label: 'No-show' },
  { key: 'booked', label: 'Booked' },
]
const HEIGHT = 210
const PAD = { top: 22, right: 4, bottom: 30, left: 30 }
const GAP = 2
const RADIUS = 4
const NICE = [4, 6, 8, 10, 12, 16, 20, 24, 30, 40, 50, 60, 80, 100, 120, 160, 200, 300, 400]

const niceMax = (v) => NICE.find((n) => n >= v) ?? Math.ceil(v / 100) * 100

function topRoundedRect(x, y, w, h, r) {
  const rr = Math.min(r, w / 2, h)
  return `M${x},${y + h}V${y + rr}Q${x},${y} ${x + rr},${y}H${x + w - rr}Q${x + w},${y} ${x + w},${y + rr}V${y + h}Z`
}

export default function StatsChart({ days, today }) {
  const wrapRef = useRef(null)
  const [width, setWidth] = useState(520)
  const [active, setActive] = useState(null)

  useLayoutEffect(() => {
    const el = wrapRef.current
    if (!el) return undefined
    const ro = new ResizeObserver(([entry]) => setWidth(Math.max(260, Math.floor(entry.contentRect.width))))
    ro.observe(el)
    return () => ro.disconnect()
  }, [])

  const plotW = width - PAD.left - PAD.right
  const plotH = HEIGHT - PAD.top - PAD.bottom
  const max = niceMax(Math.max(1, ...days.map((d) => d.total)))
  const ticks = [0, max / 2, max]
  const band = plotW / days.length
  const barW = Math.max(6, Math.min(24, band - 6))
  const labelEvery = Math.max(1, Math.ceil(28 / band))
  const todayIndex = days.findIndex((d) => d.date === today)
  const y = (v) => PAD.top + plotH - (v / max) * plotH
  // "Today" is wider than a day number, so neighbouring labels give it room.
  const todayRoom = Math.ceil(44 / band)
  const showLabel = (i) => i === todayIndex
    || (i % labelEvery === 0 && (todayIndex < 0 || Math.abs(i - todayIndex) >= todayRoom))

  const activeDay = active === null ? null : days[active]
  const tooltipLeft = active === null ? 0 : Math.min(Math.max(PAD.left + active * band + band / 2, 80), width - 80)

  return (
    <div className="chart">
      <ul className="legend" aria-label="Legend">
        {SERIES.map((s) => (
          <li key={s.key}><span className={`swatch swatch--${s.key}`} aria-hidden="true" />{s.label}</li>
        ))}
      </ul>

      <div className="chart__plot" ref={wrapRef} onMouseLeave={() => setActive(null)}>
        <svg width={width} height={HEIGHT} role="img"
          aria-label={`Appointments per day from ${formatDayMedium(days[0].date)} to ${formatDayMedium(days.at(-1).date)}`}>
          {todayIndex >= 0 && (
            <rect className="chart__today-band" x={PAD.left + todayIndex * band} y={PAD.top - 18} width={band}
              height={plotH + 18} rx="6" />
          )}
          {ticks.map((t) => (
            <g key={t}>
              <line className={t === 0 ? 'chart__baseline' : 'chart__grid'} x1={PAD.left} x2={width - PAD.right}
                y1={y(t)} y2={y(t)} />
              <text className="chart__tick" x={PAD.left - 8} y={y(t)} dy="0.32em" textAnchor="end">{t}</text>
            </g>
          ))}

          {days.map((d, i) => {
            const x = PAD.left + i * band + (band - barW) / 2
            const parts = SERIES.filter((s) => d[s.key] > 0)
            let acc = 0
            return (
              <g key={d.date}>
                {parts.map((s, j) => {
                  const y0 = y(acc)
                  acc += d[s.key]
                  const y1 = y(acc)
                  const isTop = j === parts.length - 1
                  const top = isTop ? y1 : y1 + GAP
                  const h = Math.max(1, y0 - top)
                  return isTop
                    ? <path key={s.key} className={`bar bar--${s.key}`} d={topRoundedRect(x, top, barW, h, RADIUS)} />
                    : <rect key={s.key} className={`bar bar--${s.key}`} x={x} y={top} width={barW} height={h} />
                })}
                {i === todayIndex && d.total > 0 && (
                  <text className="chart__value" x={x + barW / 2} y={y(d.total) - 6} textAnchor="middle">{d.total}</text>
                )}
                {showLabel(i) && (
                  <text className={`chart__xlabel${i === todayIndex ? ' chart__xlabel--today' : ''}`}
                    x={PAD.left + i * band + band / 2} y={HEIGHT - 10} textAnchor="middle">
                    {i === todayIndex ? 'Today' : dayOfMonth(d.date)}
                  </text>
                )}
                <rect
                  className="chart__hit"
                  x={PAD.left + i * band}
                  y={PAD.top}
                  width={band}
                  height={plotH}
                  tabIndex={0}
                  aria-label={`${formatDayMedium(d.date)}: ${d.done} done, ${d.noShow} no-show, ${d.booked} booked, ${d.cancelled} cancelled`}
                  onMouseEnter={() => setActive(i)}
                  onFocus={() => setActive(i)}
                  onBlur={() => setActive(null)}
                />
              </g>
            )
          })}
        </svg>

        {activeDay && (
          <div className="tooltip" style={{ left: tooltipLeft }} role="status">
            <p className="tooltip__title">{formatDayMedium(activeDay.date)}</p>
            {SERIES.map((s) => (
              <p key={s.key} className="tooltip__row">
                <span className={`swatch swatch--${s.key}`} aria-hidden="true" />
                <span>{s.label}</span>
                <span className="tooltip__value">{activeDay[s.key]}</span>
              </p>
            ))}
            <p className="tooltip__row tooltip__row--muted">
              <span className="swatch swatch--none" aria-hidden="true" />
              <span>Cancelled</span>
              <span className="tooltip__value">{activeDay.cancelled}</span>
            </p>
          </div>
        )}
      </div>

      <details className="chart__table">
        <summary>Show as a table</summary>
        <div className="table-scroll">
          <table>
            <thead>
              <tr><th scope="col">Day</th><th scope="col">Done</th><th scope="col">No-show</th><th scope="col">Booked</th><th scope="col">Cancelled</th></tr>
            </thead>
            <tbody>
              {days.map((d) => (
                <tr key={d.date}>
                  <th scope="row">{weekday(d.date)} {dayOfMonth(d.date)}</th>
                  <td>{d.done}</td><td>{d.noShow}</td><td>{d.booked}</td><td>{d.cancelled}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </details>
    </div>
  )
}
