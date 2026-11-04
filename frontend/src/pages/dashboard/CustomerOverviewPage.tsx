import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { CalendarCheck, CheckCircle2, Circle, Store } from 'lucide-react';
import { buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';

export function CustomerOverviewPage() {
  const { t } = useTranslation();
  const { user, hasRole } = useAuth();
  if (!user) return null;

  const checks = [
    { done: Boolean(user.email) && user.emailVerified, label: t('dashboard.check.email') },
    { done: Boolean(user.phone) && user.phoneVerified, label: t('dashboard.check.phone') },
    { done: user.twoFactorEnabled, label: t('dashboard.check.twoFactor') },
  ];
  const remaining = checks.filter((c) => !c.done).length;

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{t('dashboard.hello', { name: user.fullName })}</h1>
        <p className="mt-1 text-fg-muted">{t('dashboard.customerSubtitle')}</p>
      </div>

      <Card className="p-6">
        <h2 className="flex items-center gap-2 text-lg font-semibold">
          <CalendarCheck className="h-5 w-5 text-brand-600" aria-hidden="true" />
          {t('dashboard.upcomingBookings')}
        </h2>
        <EmptyState title={t('dashboard.noBookings')}>{t('dashboard.noBookingsHint')}</EmptyState>
      </Card>

      <Card className="p-6">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <h2 className="text-lg font-semibold">{t('dashboard.accountReadiness')}</h2>
          <span className="text-sm text-fg-muted">
            {remaining === 0 ? t('dashboard.allDone') : t('dashboard.stepsLeft', { count: remaining })}
          </span>
        </div>
        <ul className="mt-4 space-y-2">
          {checks.map((c) => (
            <li key={c.label} className="flex items-center gap-2.5 text-sm">
              {c.done ? (
                <CheckCircle2 className="h-5 w-5 text-success" aria-hidden="true" />
              ) : (
                <Circle className="h-5 w-5 text-fg-muted" aria-hidden="true" />
              )}
              <span className={c.done ? 'text-fg-muted' : ''}>{c.label}</span>
              <span className="sr-only">{c.done ? t('account.verified') : t('account.notVerified')}</span>
            </li>
          ))}
        </ul>
        {remaining > 0 && (
          <Link to="/account" className={buttonClasses('secondary', 'sm', 'mt-4')}>
            {t('dashboard.openAccount')}
          </Link>
        )}
      </Card>

      {!hasRole('BUSINESS_OWNER') && (
        <Card className="flex flex-wrap items-center gap-4 p-6">
          <span className="flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-700">
            <Store className="h-5 w-5" aria-hidden="true" />
          </span>
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{t('dashboard.ownBusiness')}</p>
            <p className="text-sm text-fg-muted">{t('dashboard.ownBusinessHint')}</p>
          </div>
          <Link to="/business/register" className={buttonClasses('primary', 'sm')}>
            {t('home.ctaBusiness')}
          </Link>
        </Card>
      )}
    </div>
  );
}
