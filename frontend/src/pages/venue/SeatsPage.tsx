import { useEffect, useRef, useState } from 'react';
import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Pencil, Ticket } from 'lucide-react';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { useApiForm } from '@/hooks/useApiForm';
import { SeatGrid } from '@/features/venue/SeatGrid';
import { useVenueFormat } from '@/features/venue/shared';
import { usePublicConfig } from '@/services/platformApi';
import { seatLabel, venueApi, type SeatMap } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

export const MAX_SEATS = 10;

/**
 * Seats picked by tapping one seat: it and the free seats to its right in the same block (up to the next aisle,
 * a sold seat or the row's end), until the chosen number is reached. Tapping a picked seat drops it; tapping
 * a new seat when the number is already reached starts again from there.
 */
export function pickSeats(map: SeatMap, seat: string, quantity: number, current: string[]): string[] {
  if (current.includes(seat)) return current.filter((s) => s !== seat);
  const row = map.rows.find((r) => seat.startsWith(r.row) && /^\d+$/.test(seat.slice(r.row.length)));
  if (!row) return current;
  const base = current.length >= quantity ? [] : current;
  const taken = new Set([...map.taken, ...base]);
  const out = [...base];
  for (let n = Number(seat.slice(row.row.length)); n <= row.seats && out.length < quantity; n++) {
    const label = seatLabel(row.row, n);
    if (taken.has(label)) break;
    out.push(label);
    if (row.gapsAfter.includes(n)) break;
  }
  return out;
}

function tierStatus(map: SeatMap, tier: string): 'SOLD_OUT' | 'ALMOST_FULL' | 'AVAILABLE' {
  const rows = map.rows.filter((r) => r.tier === tier);
  const total = rows.reduce((n, r) => n + r.seats, 0);
  const taken = map.taken.filter((s) => rows.some((r) => s.startsWith(r.row) && /^\d+$/.test(s.slice(r.row.length)))).length;
  const free = total - taken;
  return free <= 0 ? 'SOLD_OUT' : free / total < 0.2 ? 'ALMOST_FULL' : 'AVAILABLE';
}

function HowManySeats({ map, value, onChoose, onClose }: { map: SeatMap; value: number; onChoose: (n: number) => void; onClose?: () => void }) {
  const { t } = useTranslation();
  const [n, setN] = useState(value);
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => { ref.current?.focus(); }, []);
  const tiers = [...new Set(map.rows.map((r) => r.tier))].reverse();
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4" onKeyDown={(e) => { if (e.key === 'Escape') onClose?.(); }}>
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="how-many" className="w-full max-w-md overflow-hidden rounded-2xl bg-surface shadow-lift outline-none">
        <div className="p-6 text-center">
          <h2 id="how-many" className="text-lg font-semibold">{t('venue.howManySeats')}</h2>
          <Ticket className="mx-auto mt-4 h-14 w-14 rotate-[-12deg] text-brand-500" aria-hidden="true" />
          <div role="radiogroup" aria-label={t('venue.howManySeats')} className="mt-4 flex flex-wrap justify-center gap-1.5">
            {Array.from({ length: MAX_SEATS }, (_, i) => i + 1).map((k) => (
              <button key={k} type="button" role="radio" aria-checked={k === n} onClick={() => setN(k)}
                className={clsx('h-9 w-9 rounded-full text-sm font-medium', k === n ? 'bg-brand-600 text-white' : 'hover:bg-surface-muted')}>
                {k}
              </button>
            ))}
          </div>
        </div>
        <ul className="flex justify-center gap-8 border-t border-border px-6 py-4">
          {tiers.map((tier) => {
            const status = tierStatus(map, tier);
            return (
              <li key={tier} className="text-center">
                <p className="text-xs uppercase tracking-wide text-fg-muted">{t(`venue.tier.${tier}`, { defaultValue: tier })}</p>
                <p className="font-semibold">{formatINR(map.prices[tier] ?? 0)}</p>
                <p className={clsx('text-[11px] font-semibold uppercase', status === 'SOLD_OUT' ? 'text-fg-muted' : status === 'ALMOST_FULL' ? 'text-amber-600' : 'text-emerald-600')}>
                  {t(`venue.tierStatus.${status}`)}
                </p>
              </li>
            );
          })}
        </ul>
        <div className="bg-surface-muted p-4">
          <Button className="w-full" size="lg" onClick={() => onChoose(n)}>{t('venue.selectSeats')}</Button>
        </div>
      </div>
    </div>
  );
}

/** Step 3: how many seats, then the seat map, then pay. */
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
  const [quantity, setQuantity] = useState<number | null>(null);
  const [asking, setAsking] = useState(true);
  const [selected, setSelected] = useState<string[]>([]);
  const q = useQuery({ queryKey: ['seats', id], queryFn: () => venueApi.seats(id), refetchInterval: 30_000 });
  if (q.isLoading) return <LoadingState />;
  if (q.error || !q.data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />;
  const map = q.data;

  const tierOf = (seat: string) => map.rows.find((r) => seat.startsWith(r.row) && /^\d+$/.test(seat.slice(r.row.length)))?.tier ?? '';
  const live = selected.filter((s) => !map.taken.includes(s));
  const subtotal = live.reduce((sum, s) => sum + Number(map.prices[tierOf(s)] ?? 0), 0);
  const fee = config.data ? Math.round(subtotal * Number(config.data.commissionPercent)) / 100 : 0;
  const ready = quantity != null && live.length === quantity;
  const pay = async () => {
    const b = await form.submit(() => venueApi.buyTickets(id, live));
    if (!b) {
      setSelected([]);
      void qc.invalidateQueries({ queryKey: ['seats', id] });
      return;
    }
    if (b.status === 'PENDING_PAYMENT') navigate(`/customer/bookings/${b.id}/pay`);
    else navigate(`/customer/bookings/${b.id}`, { state: { justBooked: true } });
  };

  return (
    <div className="pb-36">
      <div className="border-b border-border bg-surface">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center gap-3 px-4 py-3 sm:px-6">
          <Link to={`/movies/${map.movie.id}/tickets`} aria-label={t('venue.shows')} className="rounded-lg p-1 hover:bg-surface-muted">
            <ArrowLeft className="h-5 w-5" aria-hidden="true" />
          </Link>
          <div className="min-w-0 flex-1">
            <h1 className="truncate font-semibold">{map.movie.title} ({map.language}) · {map.format}</h1>
            <p className="truncate text-sm text-fg-muted">{map.theatreName} · {map.screenName} | {f.longDay(map.startsAt)} | {f.time(map.startsAt)}</p>
          </div>
          {quantity != null && (
            <Button variant="secondary" size="sm" onClick={() => setAsking(true)}>
              <Pencil className="h-3.5 w-3.5" aria-hidden="true" />{t('venue.tickets', { count: quantity })}
            </Button>
          )}
        </div>
      </div>

      <div className="mx-auto max-w-6xl space-y-4 px-4 py-6 sm:px-6">
        {!map.bookable && <Alert tone="error">{t('errors.SHOW_CLOSED')}</Alert>}
        {form.formError && <Alert tone="error">{form.formError}</Alert>}
        {quantity != null && live.length < quantity && (
          <p className="text-center text-sm text-fg-muted">{t('venue.pickMore', { count: quantity - live.length })}</p>
        )}
        <Card className="p-4 sm:p-8">
          <SeatGrid map={map} selected={live} onToggle={(seat) => quantity != null && setSelected(pickSeats(map, seat, quantity, live))} />
        </Card>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/95 backdrop-blur">
        <div className="mx-auto max-w-6xl space-y-3 px-4 py-3 sm:px-6">
          <p className="flex flex-wrap justify-center gap-5 text-xs text-fg-muted">
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-sm border border-emerald-500" />{t('venue.legendAvailable')}</span>
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-sm bg-surface-muted ring-1 ring-border" />{t('venue.legendSold')}</span>
            <span className="flex items-center gap-1"><span className="h-3 w-3 rounded-sm bg-brand-600" />{t('venue.legendSelected')}</span>
          </p>
          {status === 'authenticated' ? (
            <Button className="mx-auto flex w-full max-w-sm" size="lg" disabled={!ready || !map.bookable} loading={form.submitting} onClick={() => void pay()}>
              {ready ? t('venue.payAmount', { amount: formatINR(subtotal + fee) }) : t('venue.selectSeats')}
            </Button>
          ) : (
            <Link to={`/signin?next=${encodeURIComponent(location.pathname)}`} className={buttonClasses('primary', 'lg', 'mx-auto flex w-full max-w-sm')}>{t('booking.signInToBook')}</Link>
          )}
          {ready && fee > 0 && (
            <p className="text-center text-xs text-fg-muted">
              {t('venue.tickets', { count: live.length })} {formatINR(subtotal)} + {t('home.examplePlatform', { percent: config.data!.commissionPercent })} {formatINR(fee)}
            </p>
          )}
        </div>
      </div>

      {asking && map.bookable && (
        <HowManySeats map={map} value={quantity ?? 2}
          onClose={quantity != null ? () => setAsking(false) : undefined}
          onChoose={(n) => { setQuantity(n); setSelected((s) => s.slice(0, n)); setAsking(false); }} />
      )}
    </div>
  );
}
