import { useEffect, useState } from 'react'
import { onSlowServer } from '../api.js'

/** Explains the wait when the free-plan API is waking up, instead of leaving a spinner unexplained. */
export default function ServerWakeNotice() {
  const [slow, setSlow] = useState(false)
  useEffect(() => onSlowServer(setSlow), [])

  return (
    <div className="wake-notice" role="status" aria-live="polite">
      {slow && (
        <p className="wake-notice__inner">
          <span className="wake-notice__dot" aria-hidden="true" />
          <span>
            <strong>Waking up the demo server.</strong> It's on a free plan that sleeps when nobody is using it,
            so the first request can take up to a minute.
          </span>
        </p>
      )}
    </div>
  )
}
