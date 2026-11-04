import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { intlLocale } from '@/i18n';
import { extrasApi, type SupportStatus } from '@/services/extrasApi';
import { formatDateTime } from '@/utils/format';

export function AdminSupportPage() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const [status, setStatus] = useState<SupportStatus | ''>('OPEN');
  const q = useQuery({ queryKey: ['admin-support', status], queryFn: () => extrasApi.adminSupport(status || undefined) });
  const runs = useQuery({ queryKey: ['retention-runs'], queryFn: extrasApi.retentionRuns });
  const [replies, setReplies] = useState<Record<number, string>>({});
  const locale = intlLocale(i18n.language);
  const answer = (id: number, s: SupportStatus) =>
    void extrasApi.answer(id, s, replies[id]?.trim() || undefined).then(() => qc.invalidateQueries({ queryKey: ['admin-support'] }));

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('extras.support')}</h1>
      <SelectField label={t('admin.status')} value={status} onChange={(e) => setStatus(e.target.value as SupportStatus | '')} className="w-48">
        <option value="">{t('admin.all')}</option>
        {(['OPEN', 'IN_PROGRESS', 'RESOLVED'] as const).map((s) => <option key={s} value={s}>{t(`extras.status.${s}`)}</option>)}
      </SelectField>
      {q.isLoading && <LoadingState />}
      {q.data?.length === 0 && <EmptyState title={t('extras.noRequests')} />}
      <div className="space-y-3">
        {q.data?.map((r) => (
          <Card key={r.id} className="space-y-2 p-4 text-sm">
            <p className="font-semibold">{r.subject} <span className="ml-2 text-xs font-normal text-fg-muted">{t(`extras.status.${r.status}`)}</span></p>
            <p className="text-xs text-fg-muted">{r.userName} · {r.userEmail} · {formatDateTime(r.createdAt, locale)}{r.reference ? ` · ${r.reference}` : ''}</p>
            <p className="whitespace-pre-line">{r.message}</p>
            {r.adminReply && <p className="rounded-lg bg-surface-muted p-2">{r.adminReply}</p>}
            {r.status !== 'RESOLVED' && (
              <div className="flex flex-wrap gap-2">
                <input aria-label={t('extras.supportReply')} placeholder={t('extras.supportReply')} value={replies[r.id] ?? ''}
                  onChange={(e) => setReplies((x) => ({ ...x, [r.id]: e.target.value }))}
                  className="h-9 min-w-[16rem] flex-1 rounded-lg border border-border bg-surface px-2 text-sm" />
                <Button size="sm" variant="secondary" onClick={() => answer(r.id, 'IN_PROGRESS')}>{t('extras.status.IN_PROGRESS')}</Button>
                <Button size="sm" onClick={() => answer(r.id, 'RESOLVED')}>{t('extras.resolve')}</Button>
              </div>
            )}
          </Card>
        ))}
      </div>
      <Card className="space-y-3 p-5">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 className="text-lg font-semibold">{t('extras.retention')}</h2>
          <Button variant="secondary" size="sm" onClick={() => void extrasApi.runRetention().then(() => qc.invalidateQueries({ queryKey: ['retention-runs'] }))}>
            {t('extras.runRetention')}
          </Button>
        </div>
        <p className="text-sm text-fg-muted">{t('extras.retentionIntro')}</p>
        <ul className="text-sm">
          {runs.data?.slice(0, 5).map((r) => (
            <li key={r.id}>{formatDateTime(r.startedAt, locale)} · {t('extras.retentionResult', { anonymised: r.appointmentsAnonymised, deleted: r.notificationsDeleted + r.outboxDeleted + r.webhookEventsDeleted + r.rateLimitsDeleted })}</li>
          ))}
        </ul>
      </Card>
    </div>
  );
}
