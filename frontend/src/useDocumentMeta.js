import { useEffect } from 'react'

/** Sets the page title, and keeps private pages (staff, booking lookups) out of search results. */
export function useDocumentMeta(title, { noindex = false } = {}) {
  useEffect(() => {
    document.title = title
    let tag = document.querySelector('meta[name="robots"]')
    if (noindex) {
      if (!tag) {
        tag = document.createElement('meta')
        tag.name = 'robots'
        document.head.appendChild(tag)
      }
      tag.content = 'noindex'
    } else if (tag) {
      tag.remove()
    }
  }, [title, noindex])
}
