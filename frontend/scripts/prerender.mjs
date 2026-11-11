// Turns the client build into a crawlable site:
//  - dist/index.html: the public booking page rendered to real HTML (good for SEO and first paint)
//  - dist/app.html:   an empty shell for staff pages, which search engines shouldn't index
// If PRERENDER_API_URL points at a running API, the services list is baked into the HTML too.
// SITE_URL (for example https://caredesk-demo.vercel.app) fills canonical/Open Graph URLs and the sitemap.
import { readFile, writeFile, rm } from 'node:fs/promises'
import { fileURLToPath } from 'node:url'
import path from 'node:path'

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)))
const dist = path.join(root, 'dist')
const site = (process.env.SITE_URL || '').replace(/\/$/, '')
const apiUrl = (process.env.PRERENDER_API_URL || '').replace(/\/$/, '')

const template = await readFile(path.join(dist, 'index.html'), 'utf8')
const { render } = await import(path.join(root, 'dist-ssr', 'entry-server.js'))

async function loadData() {
  if (!apiUrl) return null
  try {
    const get = async (p) => {
      const res = await fetch(apiUrl + p, { signal: AbortSignal.timeout(8000) })
      if (!res.ok) throw new Error(`${p} -> ${res.status}`)
      return res.json()
    }
    const [clinic, services, staff] = await Promise.all([
      get('/api/public/clinic'), get('/api/public/services'), get('/api/public/staff'),
    ])
    return { clinic, services, staff }
  } catch (e) {
    console.warn(`prerender: couldn't load data from ${apiUrl} (${e.message}); rendering without it`)
    return null
  }
}

// JSON inside a <script> tag: escape "<" so the data can never close the tag.
const safeJson = (value) => JSON.stringify(value).replace(/</g, '\\u003c')

function withSiteUrl(html) {
  return site ? html.replaceAll('__SITE_URL__', site) : html.replace(/^.*__SITE_URL__.*\n/gm, '')
}

const data = await loadData()
const appHtml = render('/', data)
let page = template.replace('<!--app-html-->', appHtml)
page = page.replace('<!--app-data-->', data
  ? `<script id="caredesk-data" type="application/json">${safeJson(data)}</script>`
  : '')
await writeFile(path.join(dist, 'index.html'), withSiteUrl(page))

const shell = template
  .replace('<!--app-html-->', '')
  .replace('<!--app-data-->', '')
  .replace('<title>Book an appointment | Juniper Family Clinic</title>',
    '<title>CareDesk</title>\n    <meta name="robots" content="noindex" />')
await writeFile(path.join(dist, 'app.html'), shell.replace(/^.*__SITE_URL__.*\n/gm, ''))

if (site) {
  await writeFile(path.join(dist, 'sitemap.xml'),
    `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n  <url><loc>${site}/</loc></url>\n</urlset>\n`)
  const robots = await readFile(path.join(dist, 'robots.txt'), 'utf8')
  await writeFile(path.join(dist, 'robots.txt'), `${robots.trimEnd()}\n\nSitemap: ${site}/sitemap.xml\n`)
}

await rm(path.join(root, 'dist-ssr'), { recursive: true, force: true })
console.log(`prerender: wrote index.html${data ? ' (with live services)' : ''}, app.html${site ? ', sitemap.xml' : ''}`)
