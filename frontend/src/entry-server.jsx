import { StrictMode } from 'react'
import { renderToString } from 'react-dom/server'
import { StaticRouter } from 'react-router'
import App from './App.jsx'

// Used only at build time by scripts/prerender.mjs.
export function render(url, initialData) {
  return renderToString(
    <StrictMode>
      <StaticRouter location={url}>
        <App initialData={initialData} />
      </StaticRouter>
    </StrictMode>,
  )
}
