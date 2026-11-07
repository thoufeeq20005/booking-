import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { BadgeCheck, CalendarClock, MapPin, Stethoscope, Ticket, User } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { LogoMark } from '@/components/brand/Logo';
import { BookingStatusBadge } from '@/features/booking/BookingStatusBadge';
import { intlLocale } from '@/i18n';
import { bookingApi } from '@/services/bookingApi';
import { formatDateTime } from '@/utils/format';

/** Opened by scanning a booking QR with a phone camera: the booking's key details, no sign-in needed. */
export function PassPage() {
  const { t, i18n } = useTranslation();
  const { code = '' } = useParams();
  const pass = useQuery({ queryKey: ['pass', code], queryFn: () => bookingApi.pass(code), retry: false });
  const locale = intlLocale(i18n.language);

  if (pass.isLoading) return <LoadingState />;
  if (pass.error) {
    return (
      <div className="mx-auto max-w-md px-4 py-12">
        <ErrorState error={pass.error} />
      </div>
    );
  }
  const p = pass.data!;
  const rows = [
    { icon: Ticket, label: t('booking.reference'), value: <span className="font-mono">{p.reference}</span> },
    { icon: CalendarClock, label: t('passPage.when'), value: formatDateTime(p.startsAt, locale) },
    { icon: MapPin, label: t('passPage.where'), value: <>{p.organizationName} · {p.branchName}{p.branchAddress && <><br /><span className="text-fg-muted">{p.branchAddress}</span></>}</> },
    ...(p.staffName ? [{ icon: Stethoscope, label: t('booking.with'), value: p.staffName }] : []),
    ...(p.guestFirstName ? [{ icon: User, label: t('passPage.guest'), value: p.guestFirstName }] : []),
  ];

  return (
    <div className="mx-auto max-w-md px-4 py-10">
      <Card className="overflow-hidden">
        <div className="flex items-center gap-3 bg-brand-600 px-6 py-5 text-white">
          <LogoMark className="h-10 w-10" />
          <div>
            <p className="text-sm opacity-90">{t('passPage.title')}</p>
            <h1 className="text-xl font-bold">{p.serviceName}</h1>
          </div>
        </div>
        <div className="space-y-4 p-6">
          <div className="flex flex-wrap items-center gap-2">
            <BookingStatusBadge status={p.status} />
            {p.paid && <span className="inline-flex items-center gap-1 text-xs font-semibold text-success">
              <BadgeCheck className="h-4 w-4" aria-hidden="true" />{t('passPage.paid')}</span>}
            {p.checkedInAt && <span className="text-xs font-semibold text-brand-700">{t('passPage.checkedIn')}</span>}
          </div>
          <dl className="space-y-3">
            {rows.map(({ icon: Icon, label, value }) => (
              <div key={label} className="flex gap-3">
                <Icon className="mt-0.5 h-5 w-5 shrink-0 text-brand-600" aria-hidden="true" />
                <div>
                  <dt className="text-xs uppercase tracking-wide text-fg-muted">{label}</dt>
                  <dd className="text-sm">{value}</dd>
                </div>
              </div>
            ))}
          </dl>
          <p className="border-t border-border pt-4 text-xs text-fg-muted">{t('passPage.note')}</p>
          <Link to={`/b/${p.organizationSlug}`} className="text-sm font-medium text-brand-700 hover:underline">
            {t('passPage.viewBusiness')}
          </Link>
        </div>
      </Card>
    </div>
  );
}
