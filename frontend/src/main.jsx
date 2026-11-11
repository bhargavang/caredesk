import { StrictMode } from 'react'
import { createRoot, hydrateRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router'
import '@fontsource-variable/public-sans'
import '@fontsource-variable/fraunces'
import './styles.css'
import App from './App.jsx'

const container = document.getElementById('root')
const dataTag = document.getElementById('caredesk-data')
const initialData = dataTag ? JSON.parse(dataTag.textContent) : null

const app = (
  <StrictMode>
    <BrowserRouter>
      <App initialData={initialData} />
    </BrowserRouter>
  </StrictMode>
)

// "/" arrives prerendered from the build, so React attaches to that HTML instead of redrawing it.
if (container.firstElementChild && window.location.pathname === '/') {
  hydrateRoot(container, app)
} else {
  container.textContent = ''
  createRoot(container).render(app)
}
