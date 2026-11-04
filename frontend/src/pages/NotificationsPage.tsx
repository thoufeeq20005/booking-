import { useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import clsx from 'clsx';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { intlLocale } from '@/i18n';
import { notificationApi, type InboxItem } from '@/services/engagementApi';
import { formatDateTime } from '@/utils/format';

export function NotificationsPage() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const navigate = useNavigate();
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ['inbox'], queryFn: notificationApi.inbox });
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: ['inbox'] });
    void qc.invalidateQueries({ queryKey: ['unread'] });
  };
  const open = async (n: InboxItem) => {
    if (!n.readAt) await notificationApi.read(n.id).catch(() => undefined);
    refresh();
    if (n.link) navigate(n.link);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{t('notify.title')}</h1>
        {data?.some((n) => !n.readAt) && (
          <Button variant="secondary" size="sm" onClick={() => void notificationApi.readAll().then(refresh)}>{t('notify.readAll')}</Button>
        )}
      </div>
      {isLoading && <LoadingState />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {data?.length === 0 && <EmptyState title={t('notify.empty')} />}
      <ul className="space-y-2">
        {data?.map((n) => (
          <li key={n.id}>
            <button type="button" onClick={() => void open(n)} className="block w-full text-left">
              <Card className={clsx('p-4 transition hover:border-brand-300', !n.readAt && 'border-brand-300 bg-brand-50/40')}>
                <p className={clsx('text-sm', !n.readAt && 'font-semibold')}>{n.title}</p>
                <p className="mt-0.5 text-sm text-fg-muted">{n.body}</p>
                <p className="mt-1 text-xs text-fg-muted">{formatDateTime(n.createdAt, intlLocale(i18n.language))}</p>
              </Card>
            </button>
          </li>
        ))}
      </ul>
    </div>
  );
}
