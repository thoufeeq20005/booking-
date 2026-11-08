import { useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { LocateFixed } from 'lucide-react';
import clsx from 'clsx';
import { Button } from '@/components/ui/Button';
import { SelectField } from '@/components/ui/FormFields';
import { intlLocale } from '@/i18n';
import { CITIES } from '@/services/discoveryApi';
import { fileUrl } from '@/features/organization/shared';
import type { Movie } from '@/services/venueApi';
import { storage } from '@/utils/storage';

/** Same key as the search page, so the city follows the customer across Find services, Movies and Dining. */
const LOCATION_KEY = 'bookeasy.location';
/** Fired when one section changes the city, so the others on the page follow. */
const LOCATION_EVENT = 'bookeasy:location';
export type Loc = { lat: number; lng: number; label: string };

function saved(): Loc {
  try {
    const raw = storage.get(LOCATION_KEY);
    if (raw) return JSON.parse(raw) as Loc;
  } catch {
    // fall through to the default city
  }
  return { lat: CITIES[0].lat, lng: CITIES[0].lng, label: 'chennai' };
}

export function useVenueLocation(cityParam?: string | null) {
  const [loc, setLoc] = useState<Loc>(() => {
    const c = CITIES.find((x) => x.key === cityParam);
    return c ? { lat: c.lat, lng: c.lng, label: c.key } : saved();
  });
  useEffect(() => {
    const json = JSON.stringify(loc);
    if (storage.get(LOCATION_KEY) !== json) {
      storage.set(LOCATION_KEY, json);
      window.dispatchEvent(new CustomEvent<Loc>(LOCATION_EVENT, { detail: loc }));
    }
  }, [loc]);
  useEffect(() => {
    const follow = (e: Event) => {
      const next = (e as CustomEvent<Loc>).detail;
      setLoc((cur) => (cur.lat === next.lat && cur.lng === next.lng && cur.label === next.label ? cur : next));
    };
    window.addEventListener(LOCATION_EVENT, follow);
    return () => window.removeEventListener(LOCATION_EVENT, follow);
  }, []);
  return [loc, setLoc] as const;
}

export function CityPicker({ loc, onChange }: { loc: Loc; onChange: (l: Loc) => void }) {
  const { t } = useTranslation();
  const [locating, setLocating] = useState(false);
  const gps = () => {
    if (!navigator.geolocation) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => { onChange({ lat: p.coords.latitude, lng: p.coords.longitude, label: 'gps' }); setLocating(false); },
      () => setLocating(false),
      { timeout: 15000 },
    );
  };
  return (
    <div className="flex flex-wrap items-end gap-2">
      <SelectField label={t('search.location')} className="w-52" value={loc.label === 'gps' ? '' : loc.label}
        onChange={(e) => {
          const c = CITIES.find((x) => x.key === e.target.value);
          if (c) onChange({ lat: c.lat, lng: c.lng, label: c.key });
        }}>
        {loc.label === 'gps' && <option value="">{t('search.myLocation')}</option>}
        {CITIES.map((c) => <option key={c.key} value={c.key}>{t(`search.city.${c.key}`)}</option>)}
      </SelectField>
      <Button variant="secondary" onClick={gps} loading={locating}>
        <LocateFixed className="h-4 w-4" aria-hidden="true" />{t('business.useMyLocation')}
      </Button>
    </div>
  );
}

/** Drawn poster: the movie's two colours and its title. No artwork is stored or copied. */
/** Poster: the movie's photo (or its two colours) with the title set over a dark fade, like a printed poster. */
export function Poster({ movie, className, size = 'md' }: { movie: Pick<Movie, 'title' | 'certificate' | 'posterFrom' | 'posterTo' | 'genres' | 'posterUrl'>; className?: string; size?: 'sm' | 'md' }) {
  return (
    <div
      className={clsx('relative flex aspect-[2/3] flex-col justify-end overflow-hidden rounded-xl text-white shadow-card', className)}
      style={{ backgroundImage: `linear-gradient(160deg, ${movie.posterFrom} 0%, ${movie.posterTo} 85%)` }}
      aria-hidden="true"
    >
      {movie.posterUrl ? (
        <>
          <img src={fileUrl(movie.posterUrl)} alt="" loading="lazy" className="absolute inset-0 h-full w-full object-cover" />
          <span className="absolute inset-0" style={{ backgroundImage: `linear-gradient(to top, ${movie.posterTo}f2 0%, ${movie.posterTo}99 28%, transparent 60%)` }} />
        </>
      ) : (
        <span className="absolute inset-0 opacity-25" style={{ backgroundImage: 'radial-gradient(circle at 75% 20%, rgba(255,255,255,.55), transparent 45%)' }} />
      )}
      <span className="absolute right-2 top-2 rounded-md border border-white/70 bg-black/40 px-1.5 text-xs font-bold">{movie.certificate}</span>
      <div className={clsx('relative', size === 'sm' ? 'p-2' : 'p-3')}>
        <p className={clsx('font-extrabold uppercase leading-tight tracking-wide drop-shadow', size === 'sm' ? 'text-xs' : 'text-lg')}>{movie.title}</p>
        {size === 'md' && <p className="mt-1 text-xs text-white/80">{movie.genres}</p>}
      </div>
    </div>
  );
}

export function useVenueFormat() {
  const { i18n } = useTranslation();
  const locale = intlLocale(i18n.language);
  return {
    locale,
    time: (iso: string) =>
      new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' }).format(new Date(iso)),
    day: (d: string) =>
      new Intl.DateTimeFormat(locale, { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${d}T00:00:00Z`)),
    longDay: (iso: string) =>
      new Intl.DateTimeFormat(locale, { weekday: 'long', day: 'numeric', month: 'long', timeZone: 'Asia/Kolkata' }).format(new Date(iso)),
    /** Weekday, day number and month of a yyyy-mm-dd date, for the date strip. */
    dayParts: (d: string) => {
      const date = new Date(`${d}T00:00:00Z`);
      const part = (o: Intl.DateTimeFormatOptions) => new Intl.DateTimeFormat(locale, { ...o, timeZone: 'UTC' }).format(date);
      return { weekday: part({ weekday: 'short' }), day: part({ day: 'numeric' }), month: part({ month: 'short' }) };
    },
    runtime: (minutes: number) => `${Math.floor(minutes / 60)}h ${minutes % 60}m`,
  };
}

/** Wall-clock HH:mm on a yyyy-mm-dd date in India time, as an ISO instant. */
export function istInstant(date: string, hhmm: string): string {
  return new Date(`${date}T${hhmm}:00+05:30`).toISOString();
}
