import { useTranslation } from 'react-i18next';
import { Database, Server } from 'lucide-react';
import clsx from 'clsx';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useHealth } from '@/services/platformApi';
import { formatDateTime } from '@/utils/format';
import { intlLocale } from '@/i18n';

function StatusRow({ icon: Icon, label, up }: { icon: typeof Server; label: string; up: boolean }) {
  const { t } = useTranslation();
  return (
    <div className="flex items-center justify-between px-6 py-5">
      <span className="flex items-center gap-3 font-medium">
        <Icon className="h-5 w-5 text-fg-muted" aria-hidden="true" />
        {label}
      </span>
      <span className={clsx('flex items-center gap-2 text-sm font-semibold', up ? 'text-success' : 'text-danger')}>
        <span className={clsx('h-2.5 w-2.5 rounded-full', up ? 'bg-success' : 'bg-danger')} aria-hidden="true" />
        {up ? t('status.up') : t('status.down')}
      </span>
    </div>
  );
}

export function StatusPage() {
  const { t, i18n } = useTranslation();
  const { data, error, isPending, refetch } = useHealth();

  return (
    <div className="mx-auto max-w-2xl px-4 py-16 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('status.title')}</h1>
      <p className="mt-2 text-fg-muted">{t('status.subtitle')}</p>
      <Card className="mt-8 divide-y divide-border">
        {isPending && <LoadingState />}
        {error && <ErrorState error={error} onRetry={() => void refetch()} />}
        {data && (
          <>
            <StatusRow icon={Server} label={t('status.api')} up />
            <StatusRow icon={Database} label={t('status.database')} up={data.database === 'UP'} />
            <p className="px-6 py-3 text-xs text-fg-muted">
              {t('status.checkedAt', { time: formatDateTime(data.checkedAt, intlLocale(i18n.language)) })}
            </p>
          </>
        )}
      </Card>
    </div>
  );
}
