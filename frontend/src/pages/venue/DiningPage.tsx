import { useState } from 'react';
import { useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { BadgePercent, CalendarCheck, ChevronRight, Leaf, ReceiptIndianRupee, Search, Sparkles } from 'lucide-react';
import { SearchBar } from '@/features/search/SearchOverlay';
import clsx from 'clsx';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { RestaurantCard } from '@/features/venue/RestaurantCard';
import { CityPicker, useVenueLocation } from '@/features/venue/shared';
import { venueApi, type Restaurant } from '@/services/venueApi';

type Mood = 'cafes' | 'family' | 'veg' | 'premium' | 'offers';
const MOODS: { key: Mood; photo: string; test: (r: Restaurant) => boolean }[] = [
  { key: 'cafes', photo: '/dining/cafes.jpg', test: (r) => r.kind === 'cafe' },
  { key: 'premium', photo: '/dining/premium.jpg', test: (r) => r.tier === 'PREMIUM' },
  { key: 'family', photo: '/dining/family.jpg', test: (r) => r.kind !== 'cafe' && r.tier === 'CAFE' },
  { key: 'veg', photo: '/dining/veg.jpg', test: (r) => r.vegOnly },
  { key: 'offers', photo: '/dining/offers.jpg', test: (r) => (r.bestOfferPercent ?? 0) >= 20 },
];
type Sort = 'DISTANCE' | 'RATING' | 'COST';
const SORTS: Record<Sort, (a: Restaurant, b: Restaurant) => number> = {
  DISTANCE: (a, b) => a.distanceKm - b.distanceKm,
  RATING: (a, b) => Number(b.ratingAvg) - Number(a.ratingAvg) || b.reviewCount - a.reviewCount,
  COST: (a, b) => Number(a.costForTwo ?? 1e9) - Number(b.costForTwo ?? 1e9),
};

function Row({ title, subtitle, items, onSeeAll }: { title: string; subtitle?: string; items: Restaurant[]; onSeeAll?: () => void }) {
  const { t } = useTranslation();
  if (items.length === 0) return null;
  return (
    <section>
      <div className="flex items-end justify-between gap-3">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{title}</h2>
          {subtitle && <p className="mt-0.5 text-fg-muted">{subtitle}</p>}
        </div>
        {onSeeAll && (
          <button type="button" onClick={onSeeAll} className="inline-flex shrink-0 items-center gap-1 text-sm font-semibold text-brand-700 hover:underline">
            {t('home.seeAll')}<ChevronRight className="h-4 w-4" aria-hidden="true" />
          </button>
        )}
      </div>
      <ul className="-mx-1 mt-4 flex snap-x gap-5 overflow-x-auto px-1 pb-2">
        {items.map((r) => <li key={r.branchId} className="w-72 shrink-0 snap-start sm:w-80"><RestaurantCard r={r} /></li>)}
      </ul>
    </section>
  );
}

export function DiningPage() {
  const { t } = useTranslation();
  const [params, setParams] = useSearchParams();
  const [loc, setLoc] = useVenueLocation(params.get('city'));
  const q = params.get('q') ?? '';
  const mood = (params.get('mood') as Mood | null) ?? null;
  const [vegOnly, setVegOnly] = useState(false);
  const [offersOnly, setOffersOnly] = useState(false);
  const [sort, setSort] = useState<Sort>('DISTANCE');
  const query = useQuery({
    queryKey: ['dining', loc.lat, loc.lng, q],
    queryFn: () => venueApi.restaurants({ lat: loc.lat, lng: loc.lng, radiusKm: 15, q: q || undefined }),
    placeholderData: keepPreviousData,
  });
  const setParam = (k: string, v?: string) => {
    const n = new URLSearchParams(params);
    if (v) n.set(k, v); else n.delete(k);
    setParams(n, { replace: true });
  };
  const scrollToList = () => document.getElementById('dining-list')?.scrollIntoView?.({ behavior: 'smooth' });
  const pickMood = (m: Mood | undefined) => { setParam('mood', m); scrollToList(); };

  const all = query.data ?? [];
  const moodTest = MOODS.find((m) => m.key === mood)?.test;
  const filtered = mood || q || vegOnly || offersOnly;
  const list = all.filter((r) => (!vegOnly || r.vegOnly) && (!offersOnly || r.bestOfferPercent != null) && (!moodTest || moodTest(r)))
    .sort(SORTS[sort]);
  const byRating = (xs: Restaurant[]) => [...xs].sort(SORTS.RATING).slice(0, 10);
  const spotlight = [...all].filter((r) => r.bestOfferPercent != null)
    .sort((a, b) => (b.bestOfferPercent ?? 0) - (a.bestOfferPercent ?? 0) || Number(b.ratingAvg) - Number(a.ratingAvg)).slice(0, 10);
  const city = loc.label === 'gps' ? t('search.myLocation') : t(`search.city.${loc.label}`, { defaultValue: loc.label });

  return (
    <div className="pb-16">
      {/* Hero */}
      <section className="relative overflow-hidden bg-[#0b1020] text-white">
        <img src="/dining/hero.jpg" alt="" className="absolute inset-0 h-full w-full object-cover opacity-40" />
        <span className="absolute inset-0 bg-gradient-to-r from-[#0b1020] via-[#0b1020]/80 to-transparent" aria-hidden="true" />
        <div className="relative mx-auto max-w-6xl px-4 py-14 sm:px-6 sm:py-20">
          <p className="inline-flex items-center gap-2 rounded-full border border-amber-300/40 bg-white/5 px-3 py-1 text-xs font-semibold uppercase tracking-[0.2em] text-amber-200">
            <Sparkles className="h-3.5 w-3.5" aria-hidden="true" />{t('venue.heroEyebrow')}
          </p>
          <h1 className="mt-5 max-w-2xl text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">
            {t('venue.diningHeadline')} <span className="bg-gradient-to-r from-amber-200 to-amber-400 bg-clip-text italic text-transparent">{t('venue.diningHeadlineAccent')}</span>
          </h1>
          <SearchBar tab="dining" value={q} className="mt-8 max-w-xl rounded-full shadow-lift" />
          <ul className="mt-10 grid max-w-3xl gap-3 sm:grid-cols-3">
            {[{ icon: Search, k: 'discover' }, { icon: CalendarCheck, k: 'book' }, { icon: ReceiptIndianRupee, k: 'pay' }].map(({ icon: Icon, k }) => (
              <li key={k} className="rounded-2xl border border-white/10 bg-white/5 p-4 backdrop-blur">
                <Icon className="h-5 w-5 text-amber-300" aria-hidden="true" />
                <p className="mt-2 font-semibold">{t(`venue.step.${k}.title`)}</p>
                <p className="text-sm text-white/70">{t(`venue.step.${k}.body`)}</p>
              </li>
            ))}
          </ul>
        </div>
      </section>

      {/* Filters */}
      <div className="sticky top-16 z-20 border-b border-border bg-surface/95 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-end gap-2 px-4 py-3 sm:px-6">
          <CityPicker loc={loc} onChange={setLoc} />
          <button type="button" aria-pressed={vegOnly} onClick={() => setVegOnly((v) => !v)}
            className={clsx('h-11 rounded-full border px-4 text-sm', vegOnly ? 'border-emerald-600 bg-emerald-600 text-white' : 'border-border hover:border-emerald-500')}>
            <Leaf className="mr-1 inline h-4 w-4" aria-hidden="true" />{t('venue.pureVeg')}
          </button>
          <button type="button" aria-pressed={offersOnly} onClick={() => setOffersOnly((v) => !v)}
            className={clsx('h-11 rounded-full border px-4 text-sm', offersOnly ? 'border-brand-600 bg-brand-600 text-white' : 'border-border hover:border-brand-400')}>
            <BadgePercent className="mr-1 inline h-4 w-4" aria-hidden="true" />{t('venue.withOffers')}
          </button>
          {(mood || q) && (
            <button type="button" onClick={() => { setParam('mood'); setParam('q'); }} className="h-11 rounded-full px-3 text-sm text-fg-muted hover:text-fg">
              {t('venue.clear')}
            </button>
          )}
        </div>
      </div>

      <div className="mx-auto max-w-6xl space-y-14 px-4 pt-10 sm:px-6">
        <section>
          <h2 className="text-2xl font-bold tracking-tight">{t('venue.moodTitle')}</h2>
          <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
            {MOODS.map((m) => (
              <li key={m.key}>
                <button type="button" aria-pressed={mood === m.key} onClick={() => pickMood(mood === m.key ? undefined : m.key)}
                  className={clsx('group relative block aspect-[4/5] w-full overflow-hidden rounded-2xl text-left', mood === m.key && 'ring-4 ring-brand-500')}>
                  <img src={m.photo} alt="" loading="lazy" className="absolute inset-0 h-full w-full object-cover transition duration-500 group-hover:scale-105" />
                  <span className="absolute inset-0 bg-gradient-to-b from-black/65 via-black/10 to-transparent" aria-hidden="true" />
                  <span className="absolute left-3 right-3 top-3 text-lg font-bold leading-tight text-white drop-shadow">{t(`venue.mood.${m.key}`)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>

        {query.isLoading && <LoadingState />}
        {query.error && <ErrorState error={query.error} onRetry={() => void query.refetch()} />}

        {!filtered && (
          <>
            <Row title={t('venue.spotlight')} subtitle={t('venue.spotlightSub')} items={spotlight} onSeeAll={() => { setOffersOnly(true); scrollToList(); }} />
            <Row title={t('venue.mood.cafes')} subtitle={t('venue.cafesSub')} items={byRating(all.filter(MOODS[0]!.test))} onSeeAll={() => pickMood('cafes')} />
            <Row title={t('venue.mood.premium')} subtitle={t('venue.premiumSub')} items={byRating(all.filter(MOODS[1]!.test))} onSeeAll={() => pickMood('premium')} />
            <Row title={t('venue.familyTitle')} subtitle={t('venue.familySub')} items={byRating(all.filter(MOODS[2]!.test))} onSeeAll={() => pickMood('family')} />
          </>
        )}

        <section id="dining-list" className="scroll-mt-36">
          <div className="flex flex-wrap items-end justify-between gap-3">
            <h2 className="text-2xl font-bold tracking-tight">
              {mood ? t(`venue.mood.${mood}`) : q ? t('venue.resultsFor', { q }) : t('venue.nearYou', { city })}
              {query.data && <span className="ml-2 text-base font-normal text-fg-muted">({list.length})</span>}
            </h2>
            <div role="radiogroup" aria-label={t('venue.sortBy')} className="flex gap-1 rounded-full bg-surface-muted p-1 text-sm">
              {(Object.keys(SORTS) as Sort[]).map((s) => (
                <button key={s} type="button" role="radio" aria-checked={sort === s} onClick={() => setSort(s)}
                  className={clsx('rounded-full px-3 py-1.5', sort === s ? 'bg-surface font-semibold shadow-card' : 'text-fg-muted')}>
                  {t(`venue.sort.${s}`)}
                </button>
              ))}
            </div>
          </div>
          {query.data && list.length === 0 && <EmptyState title={t('venue.noRestaurants')}>{t('venue.noRestaurantsHint')}</EmptyState>}
          <ul className="mt-5 grid gap-x-6 gap-y-8 sm:grid-cols-2 lg:grid-cols-3">
            {list.map((r) => <li key={r.branchId}><RestaurantCard r={r} /></li>)}
          </ul>
        </section>
      </div>
    </div>
  );
}
