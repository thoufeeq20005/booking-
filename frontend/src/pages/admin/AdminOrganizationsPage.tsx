import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Search } from 'lucide-react';
import clsx from 'clsx';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { StatusBadge, useLocalized } from '@/features/organization/shared';
import { intlLocale } from '@/i18n';
import { adminOrganizationApi, orgKeys, type OrganizationStatus } from '@/services/organizationApi';
import { formatDateTime } from '@/utils/format';

const FILTERS: (OrganizationStatus | 'ALL')[] = ['PENDING', 'APPROVED', 'REJECTED', 'SUSPENDED', 'DRAFT', 'ALL'];

export function AdminOrganizationsPage() {
  const { t, i18n } = useTranslation();
  const pick = useLocalized();
  const [params, setParams] = useSearchParams();
  const status = (params.get('status') ?? 'PENDING') as OrganizationStatus | 'ALL';
  const page = Number(params.get('page') ?? 0);
  const [q, setQ] = useState(params.get('q') ?? '');
  const search = { status: status === 'ALL' ? undefined : status, q: params.get('q') || undefined, page, size: 20 };
  const query = useQuery({
    queryKey: orgKeys.adminList(search),
    queryFn: () => adminOrganizationApi.search(search),
    placeholderData: keepPreviousData,
  });
  const update = (patch: Record<string, string>) => setParams({ status, q: params.get('q') ?? '', ...patch, page: patch.page ?? '0' });
  const locale = intlLocale(i18n.language);

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{t('admin.organizationsTitle')}</h1>
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <div role="tablist" aria-label={t('admin.statusFilter')} className="flex flex-wrap gap-1">
          {FILTERS.map((f) => (
            <button key={f} type="button" role="tab" aria-selected={status === f} onClick={() => update({ status: f })}
              className={clsx('rounded-full border px-3 py-1 text-sm', status === f
                ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-border text-fg-muted hover:text-fg')}>
              {f === 'ALL' ? t('admin.all') : t(`orgStatus.${f}`)}
            </button>
          ))}
        </div>
        <form className="ml-auto flex gap-2" onSubmit={(e) => { e.preventDefault(); update({ q }); }}>
          <input aria-label={t('admin.searchBusinesses')} placeholder={t('admin.searchBusinesses')} value={q}
            onChange={(e) => setQ(e.target.value)}
            className="h-9 w-56 rounded-xl border border-border bg-surface px-3 text-sm" />
          <Button type="submit" variant="secondary" size="sm" aria-label={t('admin.search')}>
            <Search className="h-4 w-4" aria-hidden="true" />
          </Button>
        </form>
      </div>

      {query.isLoading && <LoadingState />}
      {query.error && <ErrorState error={query.error} onRetry={() => void query.refetch()} />}
      {query.data && query.data.items.length === 0 && (
        <EmptyState title={status === 'PENDING' ? t('admin.queueEmpty') : t('admin.noResults')} />
      )}
      {query.data && query.data.items.length > 0 && (
        <Card className="overflow-x-auto">
          <table className="w-full text-left text-sm">
            <thead className="border-b border-border text-xs uppercase tracking-wide text-fg-muted">
              <tr>
                <th className="px-4 py-3">{t('admin.business')}</th>
                <th className="px-4 py-3">{t('admin.owner')}</th>
                <th className="px-4 py-3">{t('admin.city')}</th>
                <th className="px-4 py-3">{t('admin.submitted')}</th>
                <th className="px-4 py-3">{t('admin.status')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-border">
              {query.data.items.map((o) => (
                <tr key={o.id} className="hover:bg-surface-muted">
                  <td className="px-4 py-3">
                    <Link to={`/admin/organizations/${o.id}`} className="font-medium text-brand-700 hover:underline">{o.name}</Link>
                    <div className="text-xs text-fg-muted">{pick(o.subcategoryName)}</div>
                  </td>
                  <td className="px-4 py-3">
                    {o.ownerName}
                    <div className="text-xs text-fg-muted">{o.ownerEmail}</div>
                  </td>
                  <td className="px-4 py-3">{o.city ?? '—'}</td>
                  <td className="px-4 py-3">{o.submittedAt ? formatDateTime(o.submittedAt, locale) : '—'}</td>
                  <td className="px-4 py-3"><StatusBadge status={o.status} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </Card>
      )}
      {query.data && query.data.totalPages > 1 && (
        <div className="flex items-center justify-center gap-3 text-sm">
          <Button variant="secondary" size="sm" disabled={page === 0} onClick={() => update({ page: String(page - 1) })}>
            {t('admin.previous')}
          </Button>
          <span>{t('admin.pageOf', { page: page + 1, total: query.data.totalPages })}</span>
          <Button variant="secondary" size="sm" disabled={page + 1 >= query.data.totalPages}
            onClick={() => update({ page: String(page + 1) })}>
            {t('admin.next')}
          </Button>
        </div>
      )}
    </div>
  );
}
