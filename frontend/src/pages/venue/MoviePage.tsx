import { useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { ArrowLeft, Clock, MapPin, Ticket } from 'lucide-react';
import clsx from 'clsx';
import { buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField } from '@/components/ui/FormFields';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { CityPicker, Poster, useVenueFormat, useVenueLocation } from '@/features/venue/shared';
import { venueApi, type Show } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

function useMoviePage(id: number, date?: string) {
  const [params] = useSearchParams();
  const [loc, setLoc] = useVenueLocation(params.get('city'));
  const q = useQuery({
    queryKey: ['movie', id, loc.lat, loc.lng, date],
    queryFn: () => venueApi.movie(id, { lat: loc.lat, lng: loc.lng, radiusKm: 25, date }),
    placeholderData: keepPreviousData,
  });
  return { q, loc, setLoc };
}

const initials = (name: string) => name.replace(/^Voice cast:\s*/i, '').split(/\s+/).map((p) => p[0]).slice(0, 2).join('').toUpperCase();

/** Step 1: what the movie is, who is in it, and a Book tickets button. */
export function MoviePage() {
  const { t } = useTranslation();
  const id = Number(useParams().id);
  const f = useVenueFormat();
  const { q } = useMoviePage(id);
  if (q.isLoading) return <LoadingState />;
  if (q.error || !q.data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />;
  const { movie, formats, languages, dates } = q.data;
  const cast = (movie.cast ?? '').replace(/^Voice cast:\s*/i, '').split(',').map((c) => c.trim()).filter(Boolean);
  const released = movie.releaseDate <= new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date());

  return (
    <div>
      <section className="relative overflow-hidden text-white"
        style={{ backgroundImage: `linear-gradient(110deg, #0b1220 30%, ${movie.posterTo} 60%, ${movie.posterFrom} 100%)` }}>
        <div className="mx-auto grid max-w-6xl gap-8 px-4 py-10 sm:grid-cols-[14rem_1fr] sm:px-6">
          <div>
            <Poster movie={movie} className="w-44 sm:w-56" />
            <p className="mt-2 w-44 rounded-b-xl bg-black/60 py-1 text-center text-xs sm:w-56">{released ? t('venue.inCinemas') : t('venue.releasesOn', { date: f.day(movie.releaseDate) })}</p>
          </div>
          <div className="space-y-4 self-center">
            <Link to="/movies" className="inline-flex items-center gap-1 text-sm text-white/70 hover:text-white">
              <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('venue.moviesTitle')}
            </Link>
            <h1 className="text-4xl font-extrabold tracking-tight">{movie.title}</h1>
            <p className="font-medium">{f.runtime(movie.durationMinutes)} · {movie.genres} · {movie.certificate} · {f.day(movie.releaseDate)}</p>
            {formats.length > 0 && <p className="flex flex-wrap gap-2">{formats.map((x) => <span key={x} className="rounded bg-white/15 px-2 py-0.5 text-sm font-semibold">{x}</span>)}</p>}
            <p className="flex flex-wrap gap-2">{(languages.length ? languages : movie.languages.split(',').map((l) => l.trim())).map((l) => (
              <span key={l} className="rounded bg-white/15 px-2 py-0.5 text-sm">{l}</span>
            ))}</p>
            {dates.length > 0 ? (
              <Link to={`/movies/${movie.id}/tickets`} className={buttonClasses('primary', 'lg', 'px-10')}>
                <Ticket className="h-5 w-5" aria-hidden="true" />{t('venue.bookTickets')}
              </Link>
            ) : (
              <p className="rounded-xl bg-white/10 px-4 py-3 text-sm">{t(released ? 'venue.noShowsNearby' : 'venue.notReleasedYet')}</p>
            )}
          </div>
        </div>
      </section>

      <div className="mx-auto max-w-6xl space-y-8 px-4 py-10 sm:px-6">
        {movie.synopsis && (
          <section>
            <h2 className="text-xl font-bold">{t('venue.aboutMovie')}</h2>
            <p className="mt-2 max-w-3xl text-fg-muted">{movie.synopsis}</p>
          </section>
        )}
        {cast.length > 0 && (
          <section>
            <h2 className="text-xl font-bold">{t('venue.cast')}</h2>
            <ul className="mt-4 flex flex-wrap gap-6">
              {cast.map((c) => (
                <li key={c} className="w-24 text-center">
                  <span aria-hidden="true" className="mx-auto flex h-20 w-20 items-center justify-center rounded-full text-xl font-bold text-white"
                    style={{ backgroundImage: `linear-gradient(135deg, ${movie.posterFrom}, ${movie.posterTo})` }}>{initials(c)}</span>
                  <p className="mt-2 text-sm font-medium">{c}</p>
                  <p className="text-xs text-fg-muted">{t('venue.actor')}</p>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>
    </div>
  );
}

function ShowChip({ s }: { s: Show }) {
  const { t } = useTranslation();
  const f = useVenueFormat();
  const ratio = s.totalSeats ? s.seatsLeft / s.totalSeats : 0;
  const soldOut = s.seatsLeft === 0;
  const label = `${f.time(s.startsAt)} · ${s.screenName} · ${s.format} · ${s.language} · ${t('venue.seatsLeft', { count: s.seatsLeft })} · ${t('search.from', { price: formatINR(s.fromPrice) })}`;
  const inner = (
    <>
      <span className="block text-sm font-semibold tabular-nums">{f.time(s.startsAt)}</span>
      <span className="block text-[10px] uppercase tracking-wide text-fg-muted">{s.screenName} · {s.format}</span>
    </>
  );
  const tone = soldOut ? 'border-border opacity-50' : ratio < 0.2 ? 'border-amber-400 text-amber-700' : 'border-emerald-500 text-emerald-700';
  return soldOut ? (
    <span aria-label={label} className={clsx('w-32 rounded-lg border px-3 py-1.5 text-center line-through', tone)}>{inner}</span>
  ) : (
    <Link to={`/shows/${s.id}`} aria-label={label} title={label}
      className={clsx('w-32 rounded-lg border bg-surface px-3 py-1.5 text-center transition hover:bg-brand-50', tone)}>{inner}</Link>
  );
}

/** Step 2: day, language and format → theatres and their screens' show times. */
export function MovieShowsPage() {
  const { t } = useTranslation();
  const id = Number(useParams().id);
  const [params, setParams] = useSearchParams();
  const f = useVenueFormat();
  const date = params.get('date') ?? undefined;
  const { q, loc, setLoc } = useMoviePage(id, date);
  const [language, setLanguage] = useState('');
  const [format, setFormat] = useState('');
  if (q.isLoading) return <LoadingState />;
  if (q.error || !q.data) return <ErrorState error={q.error} onRetry={() => void q.refetch()} />;
  const { movie, dates, theatres, formats, languages } = q.data;
  const pick = (s: Show) => (!language || s.language === language) && (!format || s.format === format);
  const shown = theatres.map((th) => ({ ...th, shows: th.shows.filter(pick) })).filter((th) => th.shows.length > 0);

  return (
    <div className="mx-auto max-w-6xl space-y-5 px-4 py-8 sm:px-6">
      <Link to={`/movies/${movie.id}`} className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('venue.movieDetails')}
      </Link>
      <div>
        <h1 className="text-3xl font-bold tracking-tight">{movie.title}{language && <span className="font-normal text-fg-muted"> – ({language})</span>}</h1>
        <p className="mt-2 flex flex-wrap gap-2 text-xs">
          {[`${t('venue.runtime')}: ${f.runtime(movie.durationMinutes)}`, movie.certificate, ...movie.genres.split(',').map((g) => g.trim())].map((x) => (
            <span key={x} className="rounded-full border border-border px-2.5 py-0.5">{x}</span>
          ))}
        </p>
      </div>

      <Card className="flex flex-wrap items-end justify-between gap-4 p-4">
        <div role="radiogroup" aria-label={t('booking.date')} className="-mx-1 flex max-w-full gap-1 overflow-x-auto px-1">
          {dates.map((d) => {
            const { weekday: wd, day, month: mon } = f.dayParts(d);
            const on = d === q.data.date;
            return (
              <button key={d} type="button" role="radio" aria-checked={on} aria-label={f.day(d)}
                onClick={() => { const n = new URLSearchParams(params); n.set('date', d); setParams(n, { replace: true }); }}
                className={clsx('flex w-14 shrink-0 flex-col items-center rounded-xl py-1.5 text-xs uppercase', on ? 'bg-brand-600 text-white' : 'hover:bg-surface-muted')}>
                <span>{wd}</span><span className="text-lg font-bold leading-tight">{day}</span><span>{mon}</span>
              </button>
            );
          })}
        </div>
        <div className="flex flex-wrap items-end gap-2">
          <SelectField label={t('venue.language')} className="w-36" value={language} onChange={(e) => setLanguage(e.target.value)}>
            <option value="">{t('admin.all')}</option>
            {languages.map((l) => <option key={l} value={l}>{l}</option>)}
          </SelectField>
          <SelectField label={t('venue.format')} className="w-28" value={format} onChange={(e) => setFormat(e.target.value)}>
            <option value="">{t('admin.all')}</option>
            {formats.map((x) => <option key={x} value={x}>{x}</option>)}
          </SelectField>
          <CityPicker loc={loc} onChange={setLoc} />
        </div>
      </Card>

      <p className="flex flex-wrap justify-end gap-4 text-xs uppercase text-fg-muted">
        <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-emerald-500" />{t('venue.legendAvailable')}</span>
        <span className="flex items-center gap-1"><span className="h-2.5 w-2.5 rounded-full bg-amber-400" />{t('venue.legendFilling')}</span>
      </p>

      {shown.length === 0 ? (
        <EmptyState title={t('venue.noShows')}>{t('venue.noShowsHint')}</EmptyState>
      ) : (
        <ul className="divide-y divide-border overflow-hidden rounded-2xl border border-border bg-surface">
          {shown.map((th) => (
            <li key={th.branchId} className="grid gap-3 p-5 md:grid-cols-[18rem_1fr]">
              <div>
                <Link to={`/b/${th.slug}`} className="font-semibold hover:text-brand-700">{th.theatreName}{th.locality ? `: ${th.locality}` : ''}</Link>
                <p className="mt-0.5 flex items-center gap-1 text-xs text-fg-muted">
                  <MapPin className="h-3.5 w-3.5" aria-hidden="true" />{th.city} · {t('search.distance', { km: th.distanceKm })}
                </p>
                <p className="mt-0.5 flex items-center gap-1 text-xs text-fg-muted"><Clock className="h-3.5 w-3.5" aria-hidden="true" />{t('venue.cancellable')}</p>
              </div>
              <div className="flex flex-wrap gap-2">{th.shows.map((s) => <ShowChip key={s.id} s={s} />)}</div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
