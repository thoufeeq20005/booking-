import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { StatTile } from '@/components/ui/StatTile';
import { AuditEntryRow } from '@/features/admin/AuditEntryRow';
import { StatusBadge } from '@/features/organization/shared';
import { intlLocale } from '@/i18n';
import type { Role } from '@/services/apiClient';
import { dashboardApi } from '@/services/dashboardApi';
import type { OrganizationStatus } from '@/services/organizationApi';
import { formatDateTime, formatIndianNumber } from '@/utils/format';

const STATUSES: OrganizationStatus[] = ['PENDING', 'APPROVED', 'REJECTED', 'DRAFT', 'SUSPENDED'];
const ROLES: Role[] = ['CUSTOMER', 'BUSINESS_OWNER', 'STAFF', 'SUPER_ADMIN'];

export function AdminDashboardPage() {
  const { t, i18n } = useTranslation();
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ['admin-dashboard'], queryFn: dashboardApi.admin });

  if (isLoading) return <LoadingState />;
  if (error || !data) return <ErrorState error={error} onRetry={() => void refetch()} />;
  const q = data.reviewQueue;
  const locale = intlLocale(i18n.language);

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.adminTitle')}</h1>

      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        <StatTile label={t('dashboard.pendingReviews')} value={q.pending} to="/admin/organizations?status=PENDING"
          emphasis={q.pending > 0}
          note={q.oldestSubmittedAt ? t('dashboard.oldestSince', { date: formatDateTime(q.oldestSubmittedAt, locale) }) : undefined} />
        <StatTile label={t('dashboard.docsAwaitingReview')} value={q.documentsAwaitingReview}
          to="/admin/organizations?status=PENDING" />
        <StatTile label={t('dashboard.totalUsers')} value={formatIndianNumber(data.users.total)} to="/admin/users"
          note={t('dashboard.newThisWeek', { count: data.users.newLast7Days })} />
        <StatTile label={t('dashboard.suspendedUsers')} value={data.users.suspended} to="/admin/users?status=SUSPENDED" />
      </div>

      <div className="grid gap-6 lg:grid-cols-2">
        <Card className="p-6">
          <h2 className="text-lg font-semibold">{t('dashboard.businessesByStatus')}</h2>
          <ul className="mt-4 divide-y divide-border">
            {STATUSES.map((s) => (
              <li key={s}>
                <Link to={`/admin/organizations?status=${s}`} className="flex items-center justify-between py-2.5 text-sm hover:text-brand-700">
                  <StatusBadge status={s} />
                  <span className="font-semibold tabular-nums">{data.organizations[s] ?? 0}</span>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
        <Card className="p-6">
          <h2 className="text-lg font-semibold">{t('dashboard.usersByRole')}</h2>
          <ul className="mt-4 divide-y divide-border">
            {ROLES.map((r) => (
              <li key={r}>
                <Link to={`/admin/users?role=${r}`} className="flex items-center justify-between py-2.5 text-sm hover:text-brand-700">
                  <span>{t(`dashboard.role.${r}`)}</span>
                  <span className="font-semibold tabular-nums">{formatIndianNumber(data.users.byRole[r] ?? 0)}</span>
                </Link>
              </li>
            ))}
          </ul>
          <p className="mt-3 text-xs text-fg-muted">{t('dashboard.rolesOverlap')}</p>
        </Card>
      </div>

      <Card className="p-6">
        <div className="flex items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">{t('dashboard.recentActivity')}</h2>
          <Link to="/admin/audit" className="text-sm font-medium text-brand-700 hover:underline">{t('dashboard.viewAll')}</Link>
        </div>
        {data.recentActivity.length === 0 ? (
          <p className="mt-4 text-sm text-fg-muted">{t('business.noHistory')}</p>
        ) : (
          <ul className="mt-2 divide-y divide-border">
            {data.recentActivity.map((e) => <AuditEntryRow key={e.id} entry={e} />)}
          </ul>
        )}
      </Card>
    </div>
  );
}
