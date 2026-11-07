import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { BadgePercent, Star, UtensilsCrossed } from 'lucide-react';
import clsx from 'clsx';
import { fileUrl } from '@/features/organization/shared';
import type { Restaurant } from '@/services/venueApi';
import { formatINR, formatWallTime } from '@/utils/format';
import { useVenueFormat } from './shared';

/** Minutes since midnight in India time. */
function istMinutes(now = new Date()) {
  const [h, m] = new Intl.DateTimeFormat('en-GB', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: 'Asia/Kolkata' })
    .format(now).split(':').map(Number);
  return h! * 60 + m!;
}
const toMinutes = (hhmm: string) => Number(hhmm.slice(0, 2)) * 60 + Number(hhmm.slice(3, 5));

/** OPEN (with closing time), OPENS_LATER (with opening time) or CLOSED for today. */
export function openState(r: Pick<Restaurant, 'opensAt' | 'closesAt'>, now = new Date()) {
  if (!r.opensAt || !r.closesAt) return { kind: 'CLOSED' as const };
  const t = istMinutes(now);
  if (t < toMinutes(r.opensAt)) return { kind: 'OPENS_LATER' as const, at: r.opensAt };
  if (t >= toMinutes(r.closesAt)) return { kind: 'CLOSED' as const };
  return { kind: 'OPEN' as const, until: r.closesAt };
}

export function ScoreBadge({ value, className }: { value: number; className?: string }) {
  const tone = value >= 4.5 ? 'bg-emerald-800' : value >= 4 ? 'bg-emerald-600' : value >= 3.5 ? 'bg-lime-600' : 'bg-amber-500';
  return (
    <span className={clsx('inline-flex shrink-0 items-center gap-0.5 rounded-md px-1.5 py-0.5 text-sm font-bold text-white', tone, className)}>
      {value.toFixed(1)}<Star className="h-3 w-3 fill-white" aria-hidden="true" />
    </span>
  );
}

/** Photo-first restaurant card: offer ribbon on the photo, rating, cuisines, cost for two, area, distance, open status. */
export function RestaurantCard({ r, className }: { r: Restaurant; className?: string }) {
  const { t } = useTranslation();
  const f = useVenueFormat();
  const state = openState(r);
  return (
    <Link to={`/b/${r.slug}`} className={clsx('group block', className)}>
      <div className="relative aspect-[4/3] overflow-hidden rounded-2xl bg-gradient-to-br from-amber-100 to-rose-100 shadow-card">
        {r.coverUrl
          ? <img src={fileUrl(r.coverUrl)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-500 group-hover:scale-105" />
          : <UtensilsCrossed className="absolute left-1/2 top-1/2 h-10 w-10 -translate-x-1/2 -translate-y-1/2 text-orange-300" aria-hidden="true" />}
        {r.tier === 'PREMIUM' && (
          <span className="absolute left-3 top-3 rounded-md bg-black/60 px-2 py-0.5 text-[11px] font-semibold uppercase tracking-wider text-amber-200 backdrop-blur">
            {t('venue.premiumTag')}
          </span>
        )}
        {r.bestOfferPercent != null && (
          <span className="absolute bottom-3 left-0 flex items-center gap-1.5 rounded-r-lg bg-gradient-to-r from-brand-700 to-brand-500 py-1 pl-3 pr-4 text-sm font-semibold text-white shadow">
            <BadgePercent className="h-4 w-4" aria-hidden="true" />{t('venue.flatOff', { percent: r.bestOfferPercent })}
          </span>
        )}
      </div>
      <div className="mt-2.5 space-y-0.5 px-0.5">
        <div className="flex items-start justify-between gap-2">
          <p className="truncate text-lg font-semibold leading-snug group-hover:text-brand-700">{r.name}</p>
          {r.reviewCount > 0 && <ScoreBadge value={Number(r.ratingAvg)} />}
        </div>
        <div className="flex justify-between gap-3 text-sm text-fg-muted">
          <span className="truncate">{r.cuisines}</span>
          {r.costForTwo != null && <span className="shrink-0">{t('venue.costForTwo', { price: formatINR(r.costForTwo) })}</span>}
        </div>
        <div className="flex justify-between gap-3 text-sm">
          <span className="truncate text-fg-muted/80">{[r.locality, r.city].filter(Boolean).join(', ')}</span>
          <span className="shrink-0 text-xs text-fg-muted">{t('search.distance', { km: r.distanceKm })}</span>
        </div>
        {state.kind === 'OPENS_LATER' && <p className="text-sm text-red-600">{t('venue.opensAt', { time: formatWallTime(state.at, f.locale) })}</p>}
        {state.kind === 'CLOSED' && <p className="text-sm text-red-600">{t('venue.closedNow')}</p>}
        {state.kind === 'OPEN' && <p className="text-sm text-emerald-700">{t('venue.openUntil', { time: formatWallTime(state.until, f.locale) })}</p>}
      </div>
    </Link>
  );
}
