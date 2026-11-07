import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight, Plus } from 'lucide-react';
import { buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { fileUrl, StatusBadge, useLocalized } from '@/features/organization/shared';
import { orgKeys, ownerOrganizationApi } from '@/services/organizationApi';

export function MyBusinessesPage() {
  const { t } = useTranslation();
  const pick = useLocalized();
  const { data, error, isLoading, refetch } = useQuery({ queryKey: orgKeys.mine, queryFn: ownerOrganizationApi.list });

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{t('business.myBusinesses')}</h1>
        <Link to="/business/register" className={buttonClasses('primary', 'sm')}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('business.addBusiness')}
        </Link>
      </div>
      {isLoading && <LoadingState />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {data?.length === 0 && <EmptyState title={t('business.noBusinesses')} />}
      <ul className="space-y-3">
        {data?.map((o) => (
          <li key={o.id}>
            <Link to={`/owner/organizations/${o.id}`} className="block">
              <Card className="flex items-center gap-4 p-5 transition hover:border-brand-300">
                {o.logoUrl ? (
                  <img src={fileUrl(o.logoUrl)} alt="" className="h-12 w-12 rounded-xl object-cover" />
                ) : (
                  <span className="flex h-12 w-12 items-center justify-center rounded-xl bg-brand-50 text-lg font-bold text-brand-700">
                    {o.name.charAt(0)}
                  </span>
                )}
                <div className="min-w-0 flex-1">
                  <p className="truncate font-semibold">{o.name}</p>
                  <p className="truncate text-sm text-fg-muted">
                    {[pick(o.subcategoryName), o.city].filter(Boolean).join(' · ')}
                  </p>
                </div>
                <StatusBadge status={o.status} />
                <ChevronRight className="h-5 w-5 text-fg-muted" aria-hidden="true" />
              </Card>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}
