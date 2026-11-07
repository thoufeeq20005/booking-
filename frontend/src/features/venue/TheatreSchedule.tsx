import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Clapperboard } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { LoadingState } from '@/components/ui/StateMessage';
import { venueApi, type TheatreShow } from '@/services/venueApi';
import { useVenueFormat } from './shared';

/** Today's and tomorrow's shows at one theatre, grouped by movie, on the theatre's business page. */
export function TheatreSchedule({ orgId }: { orgId: number }) {
  const { t } = useTranslation();
  const f = useVenueFormat();
  const q = useQuery({ queryKey: ['theatre-schedule', orgId], queryFn: () => venueApi.theatreSchedule(orgId) });
  const byMovie = new Map<number, TheatreShow[]>();
  (q.data ?? []).forEach((s) => byMovie.set(s.movieId, [...(byMovie.get(s.movieId) ?? []), s]));

  return (
    <Card className="p-6">
      <h2 className="flex items-center gap-2 text-lg font-semibold"><Clapperboard className="h-5 w-5 text-brand-600" aria-hidden="true" />{t('venue.showsHere')}</h2>
      {q.isLoading && <LoadingState />}
      {q.data && q.data.length === 0 && <p className="mt-3 text-sm text-fg-muted">{t('venue.noShowsHere')}</p>}
      <ul className="mt-3 divide-y divide-border">
        {[...byMovie.values()].map((shows) => (
          <li key={shows[0]!.movieId} className="py-3">
            <Link to={`/movies/${shows[0]!.movieId}`} className="font-medium hover:text-brand-700">{shows[0]!.title}</Link>
            <span className="ml-2 text-xs text-fg-muted">{shows[0]!.certificate}</span>
            <div className="mt-2 flex flex-wrap gap-2">
              {shows.map((s) => (
                <Link key={s.id} to={`/shows/${s.id}`} title={`${f.longDay(s.startsAt)} · ${s.screen}`}
                  className="rounded-lg border border-emerald-500 px-2.5 py-1 text-center text-emerald-700 hover:bg-emerald-50">
                  <span className="block text-sm font-semibold tabular-nums">{f.time(s.startsAt)}</span>
                  <span className="block text-[10px] uppercase">{f.day(new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Kolkata' }).format(new Date(s.startsAt)))} · {s.format}</span>
                </Link>
              ))}
            </div>
          </li>
        ))}
      </ul>
    </Card>
  );
}
