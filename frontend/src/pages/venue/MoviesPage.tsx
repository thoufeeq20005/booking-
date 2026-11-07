import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Clapperboard } from 'lucide-react';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { CityPicker, Poster, useVenueFormat, useVenueLocation } from '@/features/venue/shared';
import { venueApi } from '@/services/venueApi';

export function MoviesPage() {
  const { t } = useTranslation();
  const [params] = useSearchParams();
  const [loc, setLoc] = useVenueLocation(params.get('city'));
  const f = useVenueFormat();
  const q = useQuery({
    queryKey: ['movies', loc.lat, loc.lng],
    queryFn: () => venueApi.movies({ lat: loc.lat, lng: loc.lng, radiusKm: 25 }),
    placeholderData: keepPreviousData,
  });

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="flex items-center gap-2 text-3xl font-bold tracking-tight">
            <Clapperboard className="h-7 w-7 text-brand-600" aria-hidden="true" />{t('venue.moviesTitle')}
          </h1>
          <p className="mt-1 text-fg-muted">{t('venue.moviesSubtitle')}</p>
        </div>
        <CityPicker loc={loc} onChange={setLoc} />
      </div>
      {q.isLoading && <LoadingState />}
      {q.error && <ErrorState error={q.error} onRetry={() => void q.refetch()} />}
      {q.data && q.data.length === 0 && <EmptyState title={t('venue.noMovies')}>{t('venue.noMoviesHint')}</EmptyState>}
      {q.data && q.data.length > 0 && (
        <ul className="grid grid-cols-2 gap-5 sm:grid-cols-3 lg:grid-cols-4">
          {q.data.map(({ movie, theatres }) => (
            <li key={movie.id}>
              <Link to={`/movies/${movie.id}`} className="group block">
                <Poster movie={movie} className="transition group-hover:-translate-y-0.5 group-hover:shadow-lift" />
                <p className="mt-2 font-semibold leading-snug group-hover:text-brand-700">{movie.title}</p>
                <p className="text-sm text-fg-muted">{movie.certificate} · {f.runtime(movie.durationMinutes)} · {movie.languages}</p>
                <p className="text-xs text-fg-muted">{t('venue.theatresNear', { count: theatres })}</p>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
