import { Link } from 'react-router'
import { SiteFooter, SiteHeader } from '../components/SiteChrome.jsx'
import { useDocumentMeta } from '../useDocumentMeta.js'

export default function NotFoundPage() {
  useDocumentMeta('Page not found', { noindex: true })
  return (
    <div className="page page--public">
      <SiteHeader />
      <main className="container narrow">
        <section className="card stack">
          <h1 className="h2">That page doesn&rsquo;t exist</h1>
          <p><Link className="btn btn--primary" to="/">Go to booking</Link></p>
        </section>
      </main>
      <SiteFooter />
    </div>
  )
}
