import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Leaf, Search, Tag, UtensilsCrossed } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { fileUrl } from '@/features/organization/shared';
import { CityPicker, useVenueLocation } from '@/features/venue/shared';
import { venueApi, type Restaurant } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

function RestaurantCard({ r }: { r: Restaurant }) {
  const { t } = useTranslation();
  return (
    <Link to={`/b/${r.slug}`} className="group block h-full">
      <Card className="flex h-full flex-col overflow-hidden transition group-hover:shadow-lift">
        <div className="relative aspect-[16/10] overflow-hidden bg-gradient-to-br from-amber-100 via-orange-50 to-rose-100">
          {r.coverUrl
            ? <img src={fileUrl(r.coverUrl)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]" />
            : <UtensilsCrossed className="absolute left-1/2 top-1/2 h-10 w-10 -translate-x-1/2 -translate-y-1/2 text-orange-300" aria-hidden="true" />}
          {r.offer && (
            <span className="absolute bottom-2 left-2 right-2 flex items-center gap-1 truncate rounded-lg bg-brand-700/90 px-2 py-1 text-xs font-semibold text-white">
              <Tag className="h-3 w-3 shrink-0" aria-hidden="true" />{r.offer}
            </span>
          )}
        </div>
        <div className="flex flex-1 flex-col gap-1 p-4">
          <div className="flex items-start justify-between gap-2">
            <p className="font-semibold leading-snug group-hover:text-brand-700">{r.name}</p>
            {r.reviewCount > 0 && (
              <span className="shrink-0 rounded-md bg-emerald-600 px-1.5 text-sm font-bold text-white">{Number(r.ratingAvg).toFixed(1)}</span>
            )}
          </div>
          {r.cuisines && <p className="text-sm text-fg-muted">{r.cuisines}</p>}
          <p className="text-sm text-fg-muted">{[r.locality, r.city].filter(Boolean).join(', ')} · {t('search.distance', { km: r.distanceKm })}</p>
          <p className="mt-auto flex items-center justify-between pt-2 text-sm">
            {r.costForTwo != null ? <span>{t('venue.costForTwo', { price: formatINR(r.costForTwo) })}</span> : <span />}
            {r.vegOnly && <span className="flex items-center gap-1 text-emerald-700"><Leaf className="h-3.5 w-3.5" aria-hidden="true" />{t('venue.pureVeg')}</span>}
          </p>
        </div>
      </Card>
    </Link>
  );
}

export function DiningPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const [loc, setLoc] = useVenueLocation(params.get('city'));
  const q = params.get('q') ?? '';
  const [text, setText] = useState(q);
  const [vegOnly, setVegOnly] = useState(false);
  const query = useQuery({
    queryKey: ['dining', loc.lat, loc.lng, q],
    queryFn: () => venueApi.restaurants({ lat: loc.lat, lng: loc.lng, radiusKm: 15, q: q || undefined }),
    placeholderData: keepPreviousData,
  });
  const list = (query.data ?? []).filter((r) => !vegOnly || r.vegOnly);

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div>
        <h1 className="flex items-center gap-2 text-3xl font-bold tracking-tight">
          <UtensilsCrossed className="h-7 w-7 text-brand-600" aria-hidden="true" />{t('venue.diningTitle')}
        </h1>
        <p className="mt-1 text-fg-muted">{t('venue.diningSubtitle')}</p>
      </div>
      <form role="search" className="flex flex-wrap gap-2"
        onSubmit={(e) => { e.preventDefault(); const n = new URLSearchParams(params); if (text.trim()) n.set('q', text.trim()); else n.delete('q'); setParams(n, { replace: true }); }}>
        <input aria-label={t('venue.diningSearch')} placeholder={t('venue.diningSearch')} value={text} onChange={(e) => setText(e.target.value)}
          className="h-11 min-w-0 flex-1 rounded-xl border border-border bg-surface px-3.5 text-sm" />
        <Button type="submit"><Search className="h-4 w-4" aria-hidden="true" />{t('admin.search')}</Button>
      </form>
      <div className="flex flex-wrap items-end justify-between gap-3">
        <CityPicker loc={loc} onChange={setLoc} />
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={vegOnly} onChange={(e) => setVegOnly(e.target.checked)} className="h-4 w-4 accent-emerald-600" />
          {t('venue.pureVeg')}
        </label>
      </div>
      {query.isLoading && <LoadingState />}
      {query.error && <ErrorState error={query.error} onRetry={() => void query.refetch()} />}
      {query.data && list.length === 0 && <EmptyState title={t('venue.noRestaurants')}>{t('venue.noRestaurantsHint')}</EmptyState>}
      <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
        {list.map((r) => <li key={r.branchId}><RestaurantCard r={r} /></li>)}
      </ul>
    </div>
  );
}
