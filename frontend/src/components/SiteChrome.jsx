import { Link } from 'react-router'
import { LogoMark, REPO_URL } from './Brand.jsx'

export function DemoBanner() {
  return (
    <div className="demo-banner" role="note">
      <span>
        <strong>Portfolio demo.</strong> Juniper Family Clinic is made up. Bookings live in a demo database and
        emails are written to the server log, not sent.
      </span>
    </div>
  )
}

export function SiteHeader({ clinicName }) {
  return (
    <header className="site-header">
      <div className="container site-header__inner">
        <Link to="/" className="brand" aria-label={`${clinicName || 'Clinic'} home`}>
          <LogoMark />
          <span className="brand__name">{clinicName || 'Juniper Family Clinic'}</span>
        </Link>
        <Link to="/staff" className="link-quiet">Staff login</Link>
      </div>
    </header>
  )
}

export function SiteFooter() {
  return (
    <footer className="site-footer">
      <div className="container site-footer__inner">
        <p>
          <strong>CareDesk</strong> is an appointment booking system for clinics and salons, built by Bhargav Angadi
          with Spring Boot and React.
        </p>
        <p className="site-footer__links">
          <a href={REPO_URL} rel="noopener">Source on GitHub</a>
          <a href="/swagger-ui.html">API docs</a>
          <Link to="/staff">Staff dashboard</Link>
        </p>
      </div>
    </footer>
  )
}
