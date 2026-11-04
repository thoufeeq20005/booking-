import type { ReactNode } from 'react';
import { useTranslation } from 'react-i18next';
import { AlertTriangle, Inbox } from 'lucide-react';
import { ApiError } from '@/services/apiClient';
import { Button } from './Button';
import { Spinner } from './Spinner';

export function LoadingState({ label }: { label?: string }) {
  const { t } = useTranslation();
  return (
    <div role="status" className="flex items-center justify-center gap-3 py-10 text-fg-muted">
      <Spinner />
      <span>{label ?? t('states.loading')}</span>
    </div>
  );
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const { t } = useTranslation();
  const code = error instanceof ApiError ? error.code : 'INTERNAL_ERROR';
  return (
    <div role="alert" className="flex flex-col items-center gap-3 py-10 text-center">
      <AlertTriangle className="h-8 w-8 text-danger" aria-hidden="true" />
      <p className="max-w-md text-fg-muted">{t(`errors.${code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          {t('states.retry')}
        </Button>
      )}
    </div>
  );
}

export function EmptyState({ title, children }: { title: string; children?: ReactNode }) {
  return (
    <div className="flex flex-col items-center gap-2 py-10 text-center">
      <Inbox className="h-8 w-8 text-fg-muted" aria-hidden="true" />
      <p className="font-semibold">{title}</p>
      {children && <div className="max-w-md text-sm text-fg-muted">{children}</div>}
    </div>
  );
}
