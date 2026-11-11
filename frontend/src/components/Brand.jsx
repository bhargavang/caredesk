export function LogoMark({ size = 32 }) {
  return (
    <svg className="logo-mark" width={size} height={size} viewBox="0 0 64 64" aria-hidden="true">
      <rect width="64" height="64" rx="16" fill="currentColor" />
      <path d="M32 14c-7 9-11 16-11 23a11 11 0 0 0 22 0c0-7-4-14-11-23z" fill="var(--logo-leaf)" />
      <path d="M32 30v20M32 40l-6-5M32 36l6-5" stroke="currentColor" strokeWidth="3" strokeLinecap="round" fill="none" />
    </svg>
  )
}

export const REPO_URL = 'https://github.com/bhargavang/caredesk'
