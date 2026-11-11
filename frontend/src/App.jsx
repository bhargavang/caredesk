import { Route, Routes } from 'react-router'
import BookingPage from './pages/BookingPage.jsx'
import BookingLookupPage from './pages/BookingLookupPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import DashboardPage from './pages/DashboardPage.jsx'
import NotFoundPage from './pages/NotFoundPage.jsx'
import ServerWakeNotice from './components/ServerWakeNotice.jsx'

export default function App({ initialData = null }) {
  return (
    <>
      <Routes>
        <Route path="/" element={<BookingPage initialData={initialData} />} />
        <Route path="/booking/:reference" element={<BookingLookupPage />} />
        <Route path="/staff/login" element={<LoginPage />} />
        <Route path="/staff" element={<DashboardPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
      <ServerWakeNotice />
    </>
  )
}
