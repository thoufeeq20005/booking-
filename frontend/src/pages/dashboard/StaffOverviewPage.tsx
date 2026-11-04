import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { DAY_KEYS, hhmm } from '@/features/organization/shared';
import { intlLocale } from '@/i18n';
import { invitationApi } from '@/services/teamApi';
import { formatDateTime } from '@/utils/format';

/** Where a staff member works, what they offer and when. Bookings join this in Phase 7. */
export function StaffOverviewPage() {
  const { t, i18n } = useTranslation();
  const { user } = useAuth();
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ['staff-workplaces'], queryFn: invitationApi.workplaces });
  const locale = intlLocale(i18n.language);

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.hello', { name: user?.fullName ?? '' })}</h1>
      {isLoading && <LoadingState />}
      {error && <ErrorState error={error} onRetry={() => void refetch()} />}
      {data?.length === 0 && (
        <Card className="p-6"><EmptyState title={t('dashboard.staffEmpty')}>{t('dashboard.staffEmptyHint')}</EmptyState></Card>
      )}
      {data?.map((w) => {
        const branchName = (id: number) => w.branches.find((b) => b.id === id)?.name ?? '';
        return (
          <Card key={w.profile.id} className="space-y-4 p-6">
            <div>
              <h2 className="text-lg font-semibold">{w.organizationName}</h2>
              <p className="text-sm text-fg-muted">{[w.profile.title, w.profile.displayName].filter(Boolean).join(' ')}</p>
            </div>
            <dl className="grid gap-4 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-xs uppercase tracking-wide text-fg-muted">{t('team.services')}</dt>
                <dd>{w.services.map((s) => s.name).join(', ') || '—'}</dd>
              </div>
              <div>
                <dt className="text-xs uppercase tracking-wide text-fg-muted">{t('team.branches')}</dt>
                <dd>{w.branches.map((b) => b.name).join(', ') || '—'}</dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-xs uppercase tracking-wide text-fg-muted">{t('team.weeklyAvailability')}</dt>
                <dd>
                  {w.profile.availability.length === 0 ? t('team.noAvailability') : (
                    <ul>{w.profile.availability.map((a, i) => (
                      <li key={i}>{t(`days.${DAY_KEYS[a.dayOfWeek - 1]}`)} {hhmm(a.startTime)}–{hhmm(a.endTime)} · {branchName(a.branchId)}</li>
                    ))}</ul>
                  )}
                </dd>
              </div>
              <div className="sm:col-span-2">
                <dt className="text-xs uppercase tracking-wide text-fg-muted">{t('team.leave')}</dt>
                <dd>
                  {w.profile.upcomingLeave.length === 0 ? t('team.noLeave') : (
                    <ul>{w.profile.upcomingLeave.map((l) => (
                      <li key={l.id}>{formatDateTime(l.startsAt, locale)} – {formatDateTime(l.endsAt, locale)}{l.reason ? ` · ${l.reason}` : ''}</li>
                    ))}</ul>
                  )}
                </dd>
              </div>
            </dl>
            <p className="text-xs text-fg-muted">
              {w.profile.permissions.includes('CAN_EDIT_HOURS') ? t('team.canEditHint') : t('team.askOwnerHint')}
            </p>
          </Card>
        );
      })}
    </div>
  );
}
