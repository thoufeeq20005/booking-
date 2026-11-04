import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { detailsText, useAuditLabel } from '@/features/admin/AuditEntryRow';
import { intlLocale } from '@/i18n';
import { auditApi } from '@/services/dashboardApi';
import { formatDateTime } from '@/utils/format';

const FILTERS = ['action', 'entityType', 'entityId', 'actorUserId'] as const;

export function AdminAuditPage() {
  const { t, i18n } = useTranslation();
  const label = useAuditLabel();
  const [params, setParams] = useSearchParams();
  const [draft, setDraft] = useState<Record<string, string>>(
    Object.fromEntries(FILTERS.map((f) => [f, params.get(f) ?? ''])),
  );
  const page = Number(params.get('page') ?? 0);
  const search = {
    action: params.get('action') || undefined,
    entityType: params.get('entityType') || undefined,
    entityId: params.get('entityId') || undefined,
    actorUserId: params.get('actorUserId') ? Number(params.get('actorUserId')) : undefined,
    page,
    size: 50,
  };
  const query = useQuery({ queryKey: ['admin-audit', search], queryFn: () => auditApi.search(search), placeholderData: keepPreviousData });
  const apply = (e: FormEvent) => {
    e.preventDefault();
    const next = new URLSearchParams();
    FILTERS.forEach((f) => draft[f]?.trim() && next.set(f, f === 'action' || f === 'entityType' ? draft[f]!.trim().toUpperCase() : draft[f]!.trim()));
    setParams(next);
  };
  const goPage = (p: number) => {
    const next = new URLSearchParams(params);
    next.set('page', String(p));
    setParams(next);
  };
  const locale = intlLocale(i18n.language);

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.auditLog')}</h1>
        <p className="mt-1 text-sm text-fg-muted">{t('dashboard.auditIntro')}</p>
      </div>
      <form onSubmit={apply} className="grid gap-3 sm:grid-cols-2 lg:grid-cols-5 lg:items-end">
        {FILTERS.map((f) => (
          <TextField key={f} label={t(`dashboard.auditFilter.${f}`)} value={draft[f] ?? ''}
            inputMode={f === 'actorUserId' ? 'numeric' : undefined}
            onChange={(e) => setDraft((d) => ({ ...d, [f]: e.target.value }))} />
        ))}
        <Button type="submit" variant="secondary">{t('dashboard.applyFilters')}</Button>
      </form>

      {query.isLoading && <LoadingState />}
      {query.error && <ErrorState error={query.error} onRetry={() => void query.refetch()} />}
      {query.data?.items.length === 0 && <EmptyState title={t('dashboard.noAudit')} />}
      {query.data && query.data.items.length > 0 && (
        <Card className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border text-xs uppercase tracking-wide text-fg-muted">
              <tr>
                <th className="px-4 py-3">{t('dashboard.auditCol.time')}</th>
                <th className="px-4 py-3">{t('dashboard.auditCol.actor')}</th>
                <th className="px-4 py-3">{t('dashboard.auditCol.action')}</th>
                <th className="px-4 py-3">{t('dashboard.auditCol.entity')}</th>
                <th className="px-4 py-3">{t('dashboard.auditCol.details')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border align-top">
              {query.data.items.map((e) => (
                <tr key={e.id}>
                  <td className="whitespace-nowrap px-4 py-3 text-fg-muted">{formatDateTime(e.createdAt, locale)}</td>
                  <td className="px-4 py-3">
                    {e.actorName ?? t('dashboard.system')}
                    {e.actorUserId != null && <div className="text-xs text-fg-muted">#{e.actorUserId}{e.ipAddress ? ` · ${e.ipAddress}` : ''}</div>}
                  </td>
                  <td className="px-4 py-3 font-medium">{label(e.action)}</td>
                  <td className="whitespace-nowrap px-4 py-3">{e.entityType}{e.entityId ? ` #${e.entityId}` : ''}</td>
                  <td className="max-w-md break-words px-4 py-3 text-fg-muted">{detailsText(e.details)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      {query.data && query.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => goPage(page - 1)}>{t('admin.previous')}</Button>
          <span>{t('admin.pageOf', { page: page + 1, total: query.data.totalPages })}</span>
          <Button variant="secondary" size="sm" disabled={page + 1 >= query.data.totalPages} onClick={() => goPage(page + 1)}>
            {t('admin.next')}
          </Button>
        </div>
      )}
    </div>
  );
}
