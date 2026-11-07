import { useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { useVenueFormat } from '@/features/venue/shared';
import { usePublicConfig } from '@/services/platformApi';
import { venueApi } from '@/services/venueApi';
import { SeatGrid } from '@/features/venue/SeatGrid';
import { formatINR } from '@/utils/format';

export const MAX_SEATS = 10;

export function SeatsPage() {
  const { t } = useTranslation();
  const id = Number(useParams().id);
  const navigate = useNavigate();
  const location = useLocation();
  const qc = useQueryClient();
  const { status } = useAuth();
  const config = usePublicConfig();
  const f = useVenueFormat();
  const form = useApiForm();
  const [selected, setSelected] = useState<string[]>([]);
  const q = useQuery({ queryKey: ['seats', id], queryFn: () => venueApi.seats(id), refetchInterval: 30_000 });
  if (q.isLoading) return <LoadingState />;
  if (q.error || !q.data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />;
  const map = q.data;

  const tierOf = (seat: string) => map.rows.find((r) => seat.startsWith(r.row) && /^\d+$/.test(seat.slice(r.row.length)))?.tier ?? '';
  const live = selected.filter((s) => !map.taken.includes(s));
  const subtotal = live.reduce((sum, s) => sum + Number(map.prices[tierOf(s)] ?? 0), 0);
  const fee = config.data ? Math.round(subtotal * Number(config.data.commissionPercent)) / 100 : null;
  const toggle = (seat: string) => {
    setSelected((cur) => cur.includes(seat) ? cur.filter((s) => s !== seat) : cur.length >= MAX_SEATS ? cur : [...cur, seat]);
  };
  const pay = async () => {
    const b = await form.submit(() => venueApi.buyTickets(id, live));
    if (!b) {
      void qc.invalidateQueries({ queryKey: ['seats', id] });
      return;
    }
    if (b.status === 'PENDING_PAYMENT') navigate(`/customer/bookings/${b.id}/pay`);
    else navigate(`/customer/bookings/${b.id}`, { state: { justBooked: true } });
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <Link to={`/movies/${map.movie.id}`} className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />{map.movie.title}
      </Link>
      <div>
        <h1 className="text-2xl font-bold tracking-tight">{map.movie.title} <span className="text-base font-normal text-fg-muted">· {map.movie.certificate} · {map.format} · {map.language}</span></h1>
        <p className="text-fg-muted">{map.theatreName} · {map.screenName} · {f.longDay(map.startsAt)}, {f.time(map.startsAt)}</p>
      </div>
      {!map.bookable && <Alert tone="error">{t('errors.SHOW_CLOSED')}</Alert>}
      <div className="grid gap-6 lg:grid-cols-[1fr_20rem]">
        <Card className="p-4 sm:p-6">
          <SeatGrid map={map} selected={live} onToggle={toggle} />
          <p className="mt-4 flex flex-wrap justify-center gap-4 text-xs text-fg-muted">
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-sm border border-emerald-500" />{t('venue.legendAvailable')}</span>
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-sm bg-brand-600" />{t('venue.legendSelected')}</span>
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-sm bg-surface-muted" />{t('venue.legendSold')}</span>
          </p>
        </Card>
        <Card className="space-y-4 self-start p-5 lg:sticky lg:top-20">
          <h2 className="font-semibold">{t('venue.yourSeats')}</h2>
          {live.length === 0 ? (
            <p className="text-sm text-fg-muted">{t('venue.pickSeats', { max: MAX_SEATS })}</p>
          ) : (
            <p className="flex flex-wrap gap-1">{[...live].sort().map((s) => (
              <span key={s} className="rounded-md bg-brand-50 px-2 py-0.5 text-sm font-medium text-brand-700">{s}</span>
            ))}</p>
          )}
          <dl className="space-y-1 rounded-xl bg-surface-muted p-3 text-sm">
            <div className="flex justify-between"><dt>{t('venue.tickets', { count: live.length })}</dt><dd>{formatINR(subtotal)}</dd></div>
            {fee != null && <div className="flex justify-between"><dt>{t('home.examplePlatform', { percent: config.data!.commissionPercent })}</dt><dd>{formatINR(fee)}</dd></div>}
            {fee != null && <div className="flex justify-between font-semibold"><dt>{t('home.exampleTotal')}</dt><dd>{formatINR(subtotal + fee)}</dd></div>}
          </dl>
          {form.formError && <Alert tone="error">{form.formError}</Alert>}
          {status === 'authenticated' ? (
            <Button className="w-full" disabled={live.length === 0 || !map.bookable} loading={form.submitting} onClick={() => void pay()}>
              {t('venue.proceedToPay')}
            </Button>
          ) : (
            <Link to={`/signin?next=${encodeURIComponent(location.pathname)}`} className={buttonClasses('primary', 'md', 'w-full')}>{t('booking.signInToBook')}</Link>
          )}
          <p className="text-xs text-fg-muted">{t('venue.holdNote')}</p>
        </Card>
      </div>
    </div>
  );
}
