import { Link, useParams, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowLeft, MapPin } from 'lucide-react';
import clsx from 'clsx';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { CityPicker, Poster, useVenueFormat, useVenueLocation } from '@/features/venue/shared';
import { venueApi, type Show } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

function ShowChip({ s }: { s: Show }) {
  const { t } = useTranslation();
  const f = useVenueFormat();
  const ratio = s.totalSeats ? s.seatsLeft / s.totalSeats : 0;
  const soldOut = s.seatsLeft === 0;
  const tone = soldOut ? 'border-border text-fg-muted' : ratio < 0.2 ? 'border-amber-400 text-amber-700' : 'border-emerald-500 text-emerald-700';
  const label = `${f.time(s.startsAt)} · ${s.format} · ${s.language} · ${t('venue.seatsLeft', { count: s.seatsLeft })} · ${t('search.from', { price: formatINR(s.fromPrice) })}`;
  const inner = (
    <>
      <span className="block text-sm font-semibold tabular-nums">{f.time(s.startsAt)}</span>
      <span className="block text-[11px] uppercase tracking-wide">{s.format} · {s.language}</span>
    </>
  );
  return soldOut ? (
    <span aria-label={label} className={clsx('rounded-lg border px-3 py-1.5 text-center line-through', tone)}>{inner}</span>
  ) : (
    <Link to={`/shows/${s.id}`} aria-label={label} title={label}
      className={clsx('rounded-lg border px-3 py-1.5 text-center transition hover:bg-brand-50', tone)}>{inner}</Link>
  );
}

export function MoviePage() {
  const { t } = useTranslation();
  const id = Number(useParams().id);
  const [params, setParams] = useSearchParams();
  const [loc, setLoc] = useVenueLocation(params.get('city'));
  const f = useVenueFormat();
  const date = params.get('date') ?? undefined;
  const q = useQuery({
    queryKey: ['movie', id, loc.lat, loc.lng, date],
    queryFn: () => venueApi.movie(id, { lat: loc.lat, lng: loc.lng, radiusKm: 25, date }),
    placeholderData: keepPreviousData,
  });
  if (q.isLoading) return <LoadingState />;
  if (q.error || !q.data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />;
  const { movie, dates, theatres } = q.data;

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <Link to="/movies" className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('venue.moviesTitle')}
      </Link>
      <div className="grid gap-6 sm:grid-cols-[12rem_1fr]">
        <Poster movie={movie} className="w-40 sm:w-48" />
        <div className="space-y-3">
          <h1 className="text-3xl font-bold tracking-tight">{movie.title}</h1>
          <p className="flex flex-wrap gap-2 text-sm">
            <span className="rounded-md bg-surface-muted px-2 py-0.5 font-semibold">{movie.certificate}</span>
            <span className="rounded-md bg-surface-muted px-2 py-0.5">{f.runtime(movie.durationMinutes)}</span>
            <span className="rounded-md bg-surface-muted px-2 py-0.5">{movie.genres}</span>
            <span className="rounded-md bg-surface-muted px-2 py-0.5">{movie.languages}</span>
          </p>
          {movie.synopsis && <p className="max-w-2xl text-fg-muted">{movie.synopsis}</p>}
          {movie.cast && <p className="text-sm"><span className="text-fg-muted">{t('venue.cast')}:</span> {movie.cast}</p>}
        </div>
      </div>

      <div className="flex flex-wrap items-end justify-between gap-4 border-t border-border pt-6">
        <div role="radiogroup" aria-label={t('booking.date')} className="-mx-1 flex max-w-full gap-2 overflow-x-auto px-1 pb-1">
          {dates.map((d) => (
            <button key={d} type="button" role="radio" aria-checked={d === q.data.date}
              onClick={() => { const n = new URLSearchParams(params); n.set('date', d); setParams(n, { replace: true }); }}
              className={clsx('shrink-0 rounded-xl border px-3 py-2 text-sm', d === q.data.date ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-border hover:border-brand-300')}>
              {f.day(d)}
            </button>
          ))}
        </div>
        <CityPicker loc={loc} onChange={setLoc} />
      </div>

      {theatres.length === 0 ? (
        <EmptyState title={t('venue.noShows')}>{t('venue.noShowsHint')}</EmptyState>
      ) : (
        <ul className="space-y-3">
          {theatres.map((th) => (
            <li key={th.branchId}>
              <Card className="grid gap-3 p-5 md:grid-cols-[16rem_1fr]">
                <div>
                  <Link to={`/b/${th.slug}`} className="font-semibold hover:text-brand-700">{th.theatreName}</Link>
                  <p className="flex items-center gap-1 text-sm text-fg-muted">
                    <MapPin className="h-3.5 w-3.5" aria-hidden="true" />
                    {[th.locality, th.city].filter(Boolean).join(', ')} · {t('search.distance', { km: th.distanceKm })}
                  </p>
                </div>
                <div className="flex flex-wrap gap-2">{th.shows.map((s) => <ShowChip key={s.id} s={s} />)}</div>
              </Card>
            </li>
          ))}
        </ul>
      )}
      <p className="flex flex-wrap gap-4 text-xs text-fg-muted">
        <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />{t('venue.legendAvailable')}</span>
        <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-amber-400" />{t('venue.legendFilling')}</span>
      </p>
    </div>
  );
}
