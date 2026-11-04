import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { ChevronRight } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { BookingStatusBadge } from '@/features/booking/BookingStatusBadge';
import { intlLocale } from '@/i18n';
import { ACTIVE, bookingApi, type Booking } from '@/services/bookingApi';
import { formatDateTime } from '@/utils/format';

function Row({ b }: { b: Booking }) {
  const { i18n } = useTranslation();
  return (
    <li>
      <Link to={`/customer/bookings/${b.id}`} className="block">
        <Card className="flex items-center gap-4 p-4 transition hover:border-brand-300">
          <div className="min-w-0 flex-1">
            <p className="font-semibold">{b.serviceName} · {b.organizationName}</p>
            <p className="text-sm text-fg-muted">{formatDateTime(b.startsAt, intlLocale(i18n.language))}{b.staffName ? ` · ${b.staffName}` : ''}</p>
          </div>
          <BookingStatusBadge status={b.status} />
          <ChevronRight className="h-5 w-5 text-fg-muted" aria-hidden="true" />
        </Card>
      </Link>
    </li>
  );
}

export function BookingsPage() {
  const { t } = useTranslation();
  const { data, error, isLoading, refetch } = useQuery({ queryKey: ['my-bookings'], queryFn: bookingApi.mine });
  if (isLoading) return <LoadingState />;
  if (error || !data) return <ErrorState error={error} onRetry={() => void refetch()} />;
  const now = Date.now();
  const upcoming = data.filter((b) => ACTIVE.includes(b.status) && new Date(b.endsAt).getTime() > now)
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt));
  const past = data.filter((b) => !upcoming.includes(b));

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('booking.myBookings')}</h1>
      <section className="space-y-3">
        <h2 className="text-lg font-semibold">{t('dashboard.upcomingBookings')}</h2>
        {upcoming.length === 0 ? (
          <EmptyState title={t('dashboard.noBookings')}><Link to="/search" className="text-brand-700 hover:underline">{t('nav.findServices')}</Link></EmptyState>
        ) : <ul className="space-y-3">{upcoming.map((b) => <Row key={b.id} b={b} />)}</ul>}
      </section>
      {past.length > 0 && (
        <section className="space-y-3">
          <h2 className="text-lg font-semibold">{t('booking.past')}</h2>
          <ul className="space-y-3">{past.map((b) => <Row key={b.id} b={b} />)}</ul>
        </section>
      )}
    </div>
  );
}
