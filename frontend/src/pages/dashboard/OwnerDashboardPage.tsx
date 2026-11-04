import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { CalendarOff, ChevronRight, Plus } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { StatTile } from '@/components/ui/StatTile';
import { fileUrl, StatusBadge, useLocalized } from '@/features/organization/shared';
import { intlLocale } from '@/i18n';
import { dashboardApi, type OwnerBusinessCard } from '@/services/dashboardApi';
import { formatDate } from '@/utils/format';

function BusinessRow({ card }: { card: OwnerBusinessCard }) {
  const { t } = useTranslation();
  const pick = useLocalized();
  const s = card.summary;
  const docs = card.documents;
  const needsAction = s.status === 'DRAFT' || s.status === 'REJECTED';
  const pct = docs.required === 0 ? 100 : Math.round((docs.uploaded / docs.required) * 100);

  return (
    <li>
      <Link to={`/owner/organizations/${s.id}${needsAction ? '?step=review' : ''}`} className="block">
        <Card className="p-5 transition hover:border-brand-300">
          <div className="flex items-center gap-4">
            {s.logoUrl ? (
              <img src={fileUrl(s.logoUrl)} alt="" className="h-11 w-11 rounded-xl object-cover" />
            ) : (
              <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 font-bold text-brand-700">
                {s.name.charAt(0)}
              </span>
            )}
            <div className="min-w-0 flex-1">
              <p className="truncate font-semibold">{s.name}</p>
              <p className="truncate text-sm text-fg-muted">{[pick(s.subcategoryName), s.city].filter(Boolean).join(' · ')}</p>
            </div>
            <StatusBadge status={s.status} />
            <ChevronRight className="h-5 w-5 text-fg-muted" aria-hidden="true" />
          </div>
          <dl className="mt-4 grid grid-cols-2 gap-3 text-sm sm:grid-cols-3">
            <div>
              <dt className="text-xs text-fg-muted">{t('dashboard.branches')}</dt>
              <dd className="font-medium tabular-nums">
                {card.activeBranches}
                {card.branchesWithoutHours > 0 && (
                  <span className="ml-1 text-xs font-normal text-danger">
                    {t('dashboard.withoutHours', { count: card.branchesWithoutHours })}
                  </span>
                )}
              </dd>
            </div>
            <div className="col-span-1 sm:col-span-2">
              <dt className="text-xs text-fg-muted">{t('dashboard.requiredDocuments')}</dt>
              <dd className="font-medium tabular-nums">
                {t('dashboard.docsUploaded', { uploaded: docs.uploaded, required: docs.required })}
                {docs.rejected > 0 && (
                  <span className="ml-1 text-xs font-normal text-danger">{t('dashboard.docsRejected', { count: docs.rejected })}</span>
                )}
              </dd>
              <div className="mt-1.5 h-1.5 overflow-hidden rounded-full bg-surface-muted" role="progressbar"
                aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct} aria-label={t('dashboard.requiredDocuments')}>
                <div className="h-full rounded-full bg-brand-600" style={{ width: `${pct}%` }} />
              </div>
            </div>
          </dl>
          {needsAction && card.missingSteps.length > 0 && (
            <p className="mt-3 text-sm text-fg-muted">
              {t('dashboard.nextStep')}: <span className="font-medium text-fg">{t(`business.missing.${card.missingSteps[0]}`)}</span>
            </p>
          )}
          {needsAction && card.missingSteps.length === 0 && (
            <p className="mt-3 text-sm font-medium text-brand-700">{t('dashboard.readyToSubmit')}</p>
          )}
        </Card>
      </Link>
    </li>
  );
}

export function OwnerDashboardPage() {
  const { t, i18n } = useTranslation();
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ['owner-dashboard'], queryFn: dashboardApi.owner });

  if (isLoading) return <LoadingState />;
  if (error || !data) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const n = (k: keyof typeof data.byStatus) => data.byStatus[k] ?? 0;
  const needsAction = n('DRAFT') + n('REJECTED');
  const locale = intlLocale(i18n.language);

  return (
    <div className="mx-auto max-w-5xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.ownerTitle')}</h1>
        <Link to="/business/register" className={buttonClasses('secondary', 'sm')}>
          <Plus className="h-4 w-4" aria-hidden="true" />
          {t('business.addBusiness')}
        </Link>
      </div>

      {n('SUSPENDED') > 0 && <Alert tone="error">{t('dashboard.suspendedNotice', { count: n('SUSPENDED') })}</Alert>}

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatTile label={t('dashboard.businesses')} value={data.businesses.length} />
        <StatTile label={t('orgStatus.APPROVED')} value={n('APPROVED')} />
        <StatTile label={t('orgStatus.PENDING')} value={n('PENDING')} />
        <StatTile label={t('dashboard.needsAction')} value={needsAction} emphasis={needsAction > 0} />
      </div>

      <section>
        <h2 className="mb-3 text-lg font-semibold">{t('business.myBusinesses')}</h2>
        {data.businesses.length === 0 ? (
          <Card className="p-6">
            <EmptyState title={t('business.noBusinesses')}>
              <Link to="/business/register" className={buttonClasses('primary', 'sm', 'mt-2')}>{t('home.ctaBusiness')}</Link>
            </EmptyState>
          </Card>
        ) : (
          <ul className="space-y-3">
            {data.businesses.map((c) => <BusinessRow key={c.summary.id} card={c} />)}
          </ul>
        )}
      </section>

      <section>
        <h2 className="mb-3 flex items-center gap-2 text-lg font-semibold">
          <CalendarOff className="h-5 w-5 text-brand-600" aria-hidden="true" />
          {t('dashboard.upcomingHolidays')}
        </h2>
        <Card className="p-5">
          {data.upcomingHolidays.length === 0 ? (
            <p className="text-sm text-fg-muted">{t('business.noHolidays')}</p>
          ) : (
            <ul className="divide-y divide-border text-sm">
              {data.upcomingHolidays.map((h) => (
                <li key={`${h.branchId}-${h.date}`} className="flex flex-wrap justify-between gap-2 py-2">
                  <span className="font-medium">{formatDate(`${h.date}T12:00:00+05:30`, locale)}</span>
                  <span className="text-fg-muted">
                    {h.organizationName} · {h.branchName}{h.reason ? ` · ${h.reason}` : ''}
                  </span>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </section>
    </div>
  );
}
