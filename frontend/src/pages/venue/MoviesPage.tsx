import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { CalendarDays, ChevronDown, ChevronUp, Clapperboard } from 'lucide-react';
import clsx from 'clsx';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { CityPicker, Poster, useVenueFormat, useVenueLocation } from '@/features/venue/shared';
import { venueApi, type NowShowing } from '@/services/venueApi';

type FilterKey = 'languages' | 'genres' | 'formats';

const genresOf = (n: NowShowing) => n.movie.genres.split(',').map((g) => g.trim()).filter(Boolean);
const valuesOf = (n: NowShowing, key: FilterKey) =>
  key === 'genres' ? genresOf(n) : key === 'formats' ? n.formats : n.languages;

function Chip({ on, children, onClick }: { on: boolean; children: React.ReactNode; onClick: () => void }) {
  return (
    <button type="button" aria-pressed={on} onClick={onClick}
      className={clsx('rounded-full border px-3 py-1 text-sm transition',
        on ? 'border-brand-600 bg-brand-600 text-white' : 'border-border text-brand-700 hover:border-brand-400')}>
      {children}
    </button>
  );
}

function FilterGroup({ title, options, selected, onToggle, onClear, open, onOpen }: {
  title: string; options: string[]; selected: string[]; onToggle: (v: string) => void; onClear: () => void;
  open: boolean; onOpen: () => void;
}) {
  const { t } = useTranslation();
  return (
    <Card className="p-4">
      <div className="flex items-center justify-between">
        <button type="button" aria-expanded={open} onClick={onOpen} className="flex items-center gap-1.5 text-sm font-medium">
          {open ? <ChevronUp className="h-4 w-4" aria-hidden="true" /> : <ChevronDown className="h-4 w-4" aria-hidden="true" />}
          {title}{selected.length > 0 && <span className="rounded-full bg-brand-100 px-1.5 text-xs text-brand-700">{selected.length}</span>}
        </button>
        {selected.length > 0 && <button type="button" onClick={onClear} className="text-xs text-fg-muted hover:text-fg">{t('venue.clear')}</button>}
      </div>
      {open && (
        <div className="mt-3 flex flex-wrap gap-2">
          {options.map((o) => <Chip key={o} on={selected.includes(o)} onClick={() => onToggle(o)}>{o}</Chip>)}
        </div>
      )}
    </Card>
  );
}

export function MoviesPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const [loc, setLoc] = useVenueLocation(params.get('city'));
  const f = useVenueFormat();
  const [chosen, setChosen] = useState<Record<FilterKey, string[]>>({ languages: [], genres: [], formats: [] });
  const [open, setOpen] = useState<FilterKey | null>('languages');
  const q = useQuery({
    queryKey: ['movies', loc.lat, loc.lng],
    queryFn: () => venueApi.movies({ lat: loc.lat, lng: loc.lng, radiusKm: 25 }),
    placeholderData: keepPreviousData,
  });
  const upcoming = useQuery({ queryKey: ['movies-upcoming'], queryFn: venueApi.upcoming, staleTime: 5 * 60 * 1000 });

  const all = q.data ?? [];
  const options = useMemo(() => {
    const collect = (key: FilterKey) => [...new Set(all.flatMap((n) => valuesOf(n, key)))].sort();
    return { languages: collect('languages'), genres: collect('genres'), formats: collect('formats') };
  }, [all]);
  const visible = all.filter((n) => (Object.keys(chosen) as FilterKey[])
    .every((k) => chosen[k].length === 0 || valuesOf(n, k).some((v) => chosen[k].includes(v))));
  const toggle = (k: FilterKey, v: string) =>
    setChosen((c) => ({ ...c, [k]: c[k].includes(v) ? c[k].filter((x) => x !== v) : [...c[k], v] }));
  const city = loc.label === 'gps' ? t('search.myLocation') : t(`search.city.${loc.label}`, { defaultValue: loc.label });

  return (
    <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6">
      <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
        <h1 className="flex items-center gap-2 text-3xl font-bold tracking-tight">
          <Clapperboard className="h-7 w-7 text-brand-600" aria-hidden="true" />{t('venue.moviesIn', { city })}
        </h1>
        <CityPicker loc={loc} onChange={setLoc} />
      </div>

      <div className="grid gap-8 lg:grid-cols-[16rem_1fr]">
        <aside className="space-y-3" aria-label={t('venue.filters')}>
          <h2 className="text-lg font-semibold">{t('venue.filters')}</h2>
          {(['languages', 'genres', 'formats'] as FilterKey[]).map((k) => (
            <FilterGroup key={k} title={t(`venue.filter.${k}`)} options={options[k]} selected={chosen[k]}
              onToggle={(v) => toggle(k, v)} onClear={() => setChosen((c) => ({ ...c, [k]: [] }))}
              open={open === k} onOpen={() => setOpen(open === k ? null : k)} />
          ))}
        </aside>

        <div className="min-w-0 space-y-6">
          {options.languages.length > 0 && (
            <div className="flex flex-wrap gap-2" aria-label={t('venue.filter.languages')}>
              {options.languages.map((l) => <Chip key={l} on={chosen.languages.includes(l)} onClick={() => toggle('languages', l)}>{l}</Chip>)}
            </div>
          )}

          {Boolean(upcoming.data?.length) && (
            <Card className="p-5">
              <h2 className="flex items-center gap-2 font-semibold"><CalendarDays className="h-5 w-5 text-brand-600" aria-hidden="true" />{t('venue.comingSoon')}</h2>
              <ul className="-mx-1 mt-3 flex gap-4 overflow-x-auto px-1 pb-1">
                {upcoming.data!.map((m) => (
                  <li key={m.id} className="w-28 shrink-0">
                    <Link to={`/movies/${m.id}`} className="group block">
                      <Poster movie={m} size="sm" />
                      <p className="mt-1.5 truncate text-sm font-medium group-hover:text-brand-700">{m.title}</p>
                      <p className="text-xs text-fg-muted">{t('venue.releasesOn', { date: f.day(m.releaseDate) })}</p>
                    </Link>
                  </li>
                ))}
              </ul>
            </Card>
          )}

          {q.isLoading && <LoadingState />}
          {q.error && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
          {q.data && visible.length === 0 && (
            <EmptyState title={all.length === 0 ? t('venue.noMovies') : t('venue.noMatches')}>
              {all.length === 0 ? t('venue.noMoviesHint') : t('venue.noMatchesHint')}
            </EmptyState>
          )}
          <ul className="grid grid-cols-2 gap-5 sm:grid-cols-3 xl:grid-cols-4">
            {visible.map(({ movie, theatres }) => (
              <li key={movie.id}>
                <Link to={`/movies/${movie.id}`} className="group block">
                  <Poster movie={movie} className="transition group-hover:-translate-y-0.5 group-hover:shadow-lift" />
                  <p className="mt-2 font-semibold leading-snug group-hover:text-brand-700">{movie.title}</p>
                  <p className="text-sm text-fg-muted">{movie.certificate}</p>
                  <p className="truncate text-sm text-fg-muted">{movie.languages}</p>
                  <p className="text-xs text-fg-muted">{t('venue.theatresNear', { count: theatres })}</p>
                </Link>
              </li>
            ))}
          </ul>
        </div>
      </div>
    </div>
  );
}
