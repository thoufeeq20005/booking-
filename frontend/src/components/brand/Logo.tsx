import clsx from 'clsx';

/** BookEasy mark: a calendar with a check, on a blue gradient tile. */
export function LogoMark({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 64 64" className={clsx('h-9 w-9', className)} aria-hidden="true">
      <defs>
        <linearGradient id="be-logo" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0" stopColor="#3b82f6" />
          <stop offset="1" stopColor="#1d4ed8" />
        </linearGradient>
      </defs>
      <rect width="64" height="64" rx="16" fill="url(#be-logo)" />
      <rect x="15" y="18" width="34" height="30" rx="6" fill="none" stroke="#fff" strokeWidth="4" />
      <path d="M23 13v9M41 13v9" stroke="#fff" strokeWidth="4" strokeLinecap="round" />
      <path
        d="M24 34.5l5.5 5.5L41 28.5"
        fill="none"
        stroke="#fff"
        strokeWidth="4.5"
        strokeLinecap="round"
        strokeLinejoin="round"
      />
    </svg>
  );
}

export function Logo({ className }: { className?: string }) {
  return (
    <span className={clsx('inline-flex items-center gap-2.5', className)}>
      <LogoMark />
      <span className="text-xl font-extrabold tracking-tight">
        Book<span className="text-brand-600">Easy</span>
      </span>
    </span>
  );
}
