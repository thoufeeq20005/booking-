import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import { useTranslation } from 'react-i18next';
import { LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import type { Role } from '@/services/apiClient';

/**
 * Hides pages from users without access. This is a UX convenience only: the API enforces every rule itself.
 */
export function RequireAuth({ roles, children }: { roles?: Role[]; children: ReactNode }) {
  const { status, user } = useAuth();
  const location = useLocation();
  const { t } = useTranslation();

  if (status === 'loading') return <LoadingState />;
  if (status === 'anonymous' || !user) {
    const next = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/signin?next=${next}`} replace />;
  }
  if (roles && !roles.some((r) => user.roles.includes(r))) {
    return (
      <div role="alert" className="mx-auto max-w-lg px-4 py-24 text-center text-fg-muted">
        {t('errors.ACCESS_DENIED')}
      </div>
    );
  }
  return <>{children}</>;
}

/** Only same-site relative paths are accepted as post-login destinations (no open redirects). */
export function safeNext(raw: string | null, fallback = '/account'): string {
  if (!raw || !raw.startsWith('/') || raw.startsWith('//') || raw.startsWith('/\\')) return fallback;
  return raw;
}
