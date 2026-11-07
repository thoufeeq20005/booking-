import { useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { BadgePercent, CalendarCheck, Leaf, ReceiptIndianRupee, Search, Sparkles, UtensilsCrossed } from 'lucide-react';
import clsx from 'clsx';
import { Card } from '@/components/ui/Card';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { fileUrl } from '@/features/organization/shared';
import { RatingBadge } from '@/features/venue/RestaurantPage';
import { CityPicker, useVenueLocation } from '@/features/venue/shared';
import { venueApi, type Restaurant } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

type Mood = 'cafes' | 'family' | 'veg' | 'premium' | 'offers';
const MOODS: { key: Mood; photo: string; test: (r: Restaurant) => boolean }[] = [
  { key: 'cafes', photo: '/dining/cafes.jpg', test: (r) => r.kind === 'cafe' },
  { key: 'family', photo: '/dining/family.jpg', test: (r) => r.kind !== 'cafe' && r.tier === 'CAFE' },
  { key: 'veg', photo: '/dining/veg.jpg', test: (r) => r.vegOnly },
  { key: 'premium', photo: '/dining/premium.jpg', test: (r) => r.tier === 'PREMIUM' },
  { key: 'offers', photo: '/dining/offers.jpg', test: (r) => (r.bestOfferPercent ?? 0) >= 20 },
];

function OfferBadge({ r, className }: { r: Restaurant; className?: string }) {
  if (r.bestOfferPercent == null) return null;
  return (
    <span className={clsx('inline-flex items-center gap-1 rounded-lg bg-brand-700 px-2 py-0.5 text-xs font-semibold text-white', className)}>
      <BadgePercent className="h-3.5 w-3.5" aria-hidden="true" />{r.bestOfferTitle}
    </span>
  );
}

function SpotlightCard({ r }: { r: Restaurant }) {
  const { t } = useTranslation();
  return (
    <Link to={`/b/${r.slug}`} className="group block w-72 shrink-0 sm:w-80">
      <div className="relative aspect-[4/3] overflow-hidden rounded-2xl bg-surface-muted">
        {r.coverUrl && <img src={fileUrl(r.coverUrl)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]" />}
        <OfferBadge r={r} className="absolute left-3 top-3" />
        {r.reviewCount > 0 && <RatingBadge value={Number(r.ratingAvg)} className="absolute bottom-3 right-3" />}
        <span className="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-black/70 to-transparent" aria-hidden="true" />
        <span className="absolute bottom-3 left-3 right-16 text-white">
          <span className="block truncate text-lg font-semibold">{r.name}</span>
          <span className="block truncate text-sm opacity-90">{t('search.distance', { km: r.distanceKm })}{r.costForTwo != null && ` · ${t('venue.costForTwo', { price: formatINR(r.costForTwo) })}`}</span>
        </span>
      </div>
    </Link>
  );
}

function RestaurantRow({ r }: { r: Restaurant }) {
  const { t } = useTranslation();
  return (
    <Link to={`/b/${r.slug}`} className="group block h-full">
      <Card className="flex h-full gap-4 p-3 transition group-hover:shadow-lift">
        <div className="relative h-24 w-28 shrink-0 overflow-hidden rounded-xl bg-gradient-to-br from-amber-100 to-rose-100">
          {r.coverUrl ? <img src={fileUrl(r.coverUrl)} alt="" loading="lazy" className="h-full w-full object-cover" />
            : <UtensilsCrossed className="absolute left-1/2 top-1/2 h-8 w-8 -translate-x-1/2 -translate-y-1/2 text-orange-300" aria-hidden="true" />}
        </div>
        <div className="min-w-0 flex-1 space-y-0.5 text-sm">
          <div className="flex items-start justify-between gap-2">
            <p className="truncate text-base font-semibold group-hover:text-brand-700">{r.name}</p>
            {r.reviewCount > 0 && <RatingBadge value={Number(r.ratingAvg)} />}
          </div>
          {r.cuisines && <p className="truncate text-fg-muted">{r.cuisines}</p>}
          <p className="truncate text-fg-muted">{t('search.distance', { km: r.distanceKm })} · {[r.locality, r.city].filter(Boolean).join(', ')}</p>
          <p className="flex flex-wrap items-center gap-x-3 gap-y-1">
            {r.costForTwo != null && <span>{t('venue.costForTwo', { price: formatINR(r.costForTwo) })}</span>}
            {r.vegOnly && <span className="flex items-center gap-1 text-emerald-700"><Leaf className="h-3.5 w-3.5" aria-hidden="true" />{t('venue.pureVeg')}</span>}
            {r.bestOfferPercent != null && <span className="font-medium text-brand-700">{r.bestOfferTitle}</span>}
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
  const mood = (params.get('mood') as Mood | null) ?? null;
  const [vegOnly, setVegOnly] = useState(false);
  const [offersOnly, setOffersOnly] = useState(false);
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
  const all = query.data ?? [];
  const moodTest = MOODS.find((m) => m.key === mood)?.test;
  const list = all.filter((r) => (!vegOnly || r.vegOnly) && (!offersOnly || r.bestOfferPercent != null) && (!moodTest || moodTest(r)));
  const spotlight = [...all].filter((r) => r.bestOfferPercent != null)
    .sort((a, b) => (b.bestOfferPercent ?? 0) - (a.bestOfferPercent ?? 0) || Number(b.ratingAvg) - Number(a.ratingAvg)).slice(0, 8);
  const city = loc.label === 'gps' ? t('search.myLocation') : t(`search.city.${loc.label}`, { defaultValue: loc.label });

  return (
    <div className="pb-16">
      <section className="mx-auto max-w-6xl px-4 pt-8 sm:px-6">
        <h1 className="text-center text-3xl font-extrabold tracking-tight sm:text-4xl">
          {t('venue.diningHeadline')} <span className="italic text-brand-600">{t('venue.diningHeadlineAccent')}</span>
        </h1>
        <div className="relative mt-6 overflow-hidden rounded-3xl shadow-lift">
          <img src="/dining/hero.jpg" alt="" className="absolute inset-0 h-full w-full object-cover" />
          <span className="absolute inset-0 bg-gradient-to-b from-brand-900/70 via-brand-800/60 to-brand-900/80" aria-hidden="true" />
          <div className="relative flex flex-col items-center gap-5 px-6 py-14 text-center text-white sm:py-20">
            <p className="text-3xl font-bold drop-shadow sm:text-5xl">{t('venue.heroTitle')}</p>
            <form role="search" className="flex w-full max-w-xl items-center gap-2 rounded-full bg-white p-1.5 pl-5 text-fg shadow-card"
              onSubmit={(e) => { e.preventDefault(); setParam('q', text.trim() || undefined); document.getElementById('dining-list')?.scrollIntoView?.({ behavior: 'smooth' }); }}>
              <Search className="h-5 w-5 shrink-0 text-fg-muted" aria-hidden="true" />
              <input aria-label={t('venue.diningSearch')} placeholder={t('venue.diningSearch')} value={text} onChange={(e) => setText(e.target.value)}
                className="h-10 min-w-0 flex-1 bg-transparent text-sm outline-none" />
              <button type="submit" className="rounded-full bg-brand-600 px-5 py-2 text-sm font-semibold text-white hover:bg-brand-700">{t('admin.search')}</button>
            </form>
          </div>
        </div>
        <ul className="mt-6 grid gap-3 sm:grid-cols-3">
          {[{ icon: Search, k: 'discover' }, { icon: CalendarCheck, k: 'book' }, { icon: ReceiptIndianRupee, k: 'pay' }].map(({ icon: Icon, k }) => (
            <li key={k} className="flex items-start gap-3 rounded-2xl border border-border p-4">
              <Icon className="mt-0.5 h-6 w-6 shrink-0 text-brand-600" aria-hidden="true" />
              <div><p className="font-semibold">{t(`venue.step.${k}.title`)}</p><p className="text-sm text-fg-muted">{t(`venue.step.${k}.body`)}</p></div>
            </li>
          ))}
        </ul>
      </section>

      <div className="sticky top-16 z-20 mt-8 border-y border-border bg-surface/95 backdrop-blur">
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
            <button type="button" onClick={() => { setParam('mood'); setParam('q'); setText(''); }} className="h-11 rounded-full px-3 text-sm text-fg-muted hover:text-fg">
              {t('venue.clear')}
            </button>
          )}
        </div>
      </div>

      <div className="mx-auto max-w-6xl space-y-12 px-4 pt-8 sm:px-6">
        <section>
          <h2 className="text-2xl font-bold tracking-tight">{t('venue.moodTitle')}</h2>
          <ul className="mt-4 grid grid-cols-2 gap-3 sm:grid-cols-5">
            {MOODS.map((m) => (
              <li key={m.key}>
                <button type="button" aria-pressed={mood === m.key}
                  onClick={() => { setParam('mood', mood === m.key ? undefined : m.key); document.getElementById('dining-list')?.scrollIntoView?.({ behavior: 'smooth' }); }}
                  className={clsx('group relative block aspect-[4/5] w-full overflow-hidden rounded-2xl text-left', mood === m.key && 'ring-4 ring-brand-500')}>
                  <img src={m.photo} alt="" loading="lazy" className="absolute inset-0 h-full w-full object-cover transition duration-300 group-hover:scale-105" />
                  <span className="absolute inset-0 bg-gradient-to-b from-black/60 via-black/10 to-transparent" aria-hidden="true" />
                  <span className="absolute left-3 top-3 right-3 text-lg font-bold leading-tight text-white drop-shadow">{t(`venue.mood.${m.key}`)}</span>
                </button>
              </li>
            ))}
          </ul>
        </section>

        {spotlight.length > 0 && !mood && !q && (
          <section>
            <h2 className="flex items-center gap-2 text-2xl font-bold tracking-tight"><Sparkles className="h-6 w-6 text-brand-600" aria-hidden="true" />{t('venue.spotlight')}</h2>
            <ul className="-mx-1 mt-4 flex gap-4 overflow-x-auto px-1 pb-2">
              {spotlight.map((r) => <li key={r.branchId}><SpotlightCard r={r} /></li>)}
            </ul>
          </section>
        )}

        <section id="dining-list" className="scroll-mt-36">
          <h2 className="text-2xl font-bold tracking-tight">
            {mood ? t(`venue.mood.${mood}`) : q ? t('venue.resultsFor', { q }) : t('venue.nearYou', { city })}
          </h2>
          {query.isLoading && <LoadingState />}
          {query.error && <ErrorState error={query.error} onRetry={() => void query.refetch()} />}
          {query.data && list.length === 0 && <EmptyState title={t('venue.noRestaurants')}>{t('venue.noRestaurantsHint')}</EmptyState>}
          <ul className="mt-4 grid gap-3 md:grid-cols-2">
            {list.map((r) => <li key={r.branchId}><RestaurantRow r={r} /></li>)}
          </ul>
        </section>
      </div>
    </div>
  );
}
