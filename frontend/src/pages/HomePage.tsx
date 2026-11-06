import { useState, type FormEvent, type ReactNode } from 'react';
import { Link, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueries, useQuery } from '@tanstack/react-query';
import { BadgeCheck, CalendarClock, ChevronRight, IndianRupee, MapPin, QrCode, Search } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { Button, buttonClasses } from '@/components/ui/Button';
import { fileUrl, useLocalized } from '@/features/organization/shared';
import { usePublicConfig } from '@/services/platformApi';
import { usePublicCategories } from '@/services/organizationApi';
import { CITIES, discoveryApi, type SearchHit } from '@/services/discoveryApi';
import { formatINR } from '@/utils/format';
import { illustratePlatformFee, paiseToRupees } from '@/utils/money';

const EXAMPLE_FEE_PAISE = 100_000; // ₹1,000

/** Cities with listings in the demo data, shown as quick links. */
const FEATURED_CITIES = ['chennai', 'coimbatore', 'madurai', 'tiruchirappalli', 'salem', 'thoothukudi'] as const;
const CATEGORY_PHOTOS: Record<string, string> = {
  healthcare: '/home/healthcare.jpg',
  'salon-spa': '/home/salon-spa.jpg',
  education: '/home/education.jpg',
  fitness: '/home/fitness.jpg',
};
const cityOf = (key: string) => CITIES.find((c) => c.key === key)!;

function HeroSearch() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [what, setWhat] = useState('');
  const [city, setCity] = useState<string>('chennai');
  const submit = (e: FormEvent) => {
    e.preventDefault();
    const p = new URLSearchParams({ city });
    if (what.trim()) p.set('q', what.trim());
    navigate(`/search?${p.toString()}`);
  };
  return (
    <form onSubmit={submit} role="search"
      className="grid gap-2 rounded-2xl border-2 border-brand-500 bg-surface p-2 shadow-card sm:grid-cols-[1fr_14rem_auto]">
      <label className="flex items-center gap-2 rounded-xl px-3">
        <Search className="h-5 w-5 shrink-0 text-fg-muted" aria-hidden="true" />
        <span className="sr-only">{t('home.searchWhat')}</span>
        <input value={what} onChange={(e) => setWhat(e.target.value)} placeholder={t('home.searchWhatPlaceholder')}
          className="h-12 w-full bg-transparent text-base outline-none placeholder:text-fg-muted" />
      </label>
      <label className="flex items-center gap-2 rounded-xl px-3 sm:border-l sm:border-border">
        <MapPin className="h-5 w-5 shrink-0 text-fg-muted" aria-hidden="true" />
        <span className="sr-only">{t('home.searchCity')}</span>
        <select value={city} onChange={(e) => setCity(e.target.value)} className="h-12 w-full bg-transparent text-base outline-none">
          {CITIES.map((c) => <option key={c.key} value={c.key}>{t(`search.city.${c.key}`)}</option>)}
        </select>
      </label>
      <Button type="submit" size="lg" className="h-12 px-8">{t('home.searchButton')}</Button>
    </form>
  );
}

function ScoreBadge({ value }: { value: number }) {
  return (
    <span className="inline-flex h-7 min-w-[2.25rem] items-center justify-center rounded-lg rounded-bl-none bg-brand-700 px-1.5 text-sm font-bold text-white">
      {value.toFixed(1)}
    </span>
  );
}

function PlaceCard({ h }: { h: SearchHit }) {
  const { t } = useTranslation();
  const pick = useLocalized();
  return (
    <Link to={`/b/${h.slug}`} className="group block h-full">
      <Card className="flex h-full flex-col overflow-hidden transition group-hover:shadow-lift">
        <div className="aspect-[4/3] overflow-hidden bg-surface-muted">
          {h.coverUrl
            ? <img src={fileUrl(h.coverUrl)} alt="" loading="lazy" className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]" />
            : <span className="flex h-full items-center justify-center text-4xl font-bold text-brand-300">{h.name.charAt(0)}</span>}
        </div>
        <div className="flex flex-1 flex-col gap-1 p-4">
          <p className="font-semibold leading-snug group-hover:text-brand-700">{h.name}</p>
          <p className="text-sm text-fg-muted">{pick(h.subcategoryName)} · {[h.locality, h.city].filter(Boolean).join(', ')}</p>
          {h.reviewCount > 0 && (
            <p className="mt-1 flex items-center gap-2 text-sm">
              <ScoreBadge value={Number(h.ratingAvg)} />
              <span className="text-fg-muted">{t('biz.reviews', { count: h.reviewCount })}</span>
            </p>
          )}
          {h.fromPrice != null && (
            <p className="mt-auto pt-2 text-right text-sm">{t('search.from', { price: formatINR(h.fromPrice) })}</p>
          )}
        </div>
      </Card>
    </Link>
  );
}

function SectionHeading({ title, subtitle, action }: { title: string; subtitle?: string; action?: ReactNode }) {
  return (
    <div className="mb-5 flex flex-wrap items-end justify-between gap-2">
      <div>
        <h2 className="text-2xl font-bold tracking-tight">{title}</h2>
        {subtitle && <p className="mt-1 text-fg-muted">{subtitle}</p>}
      </div>
      {action}
    </div>
  );
}

export function HomePage() {
  const { t } = useTranslation();
  const pick = useLocalized();
  const { data: config } = usePublicConfig();
  const categories = usePublicCategories();
  const percent = config?.commissionPercent;
  const example = percent !== undefined ? illustratePlatformFee(EXAMPLE_FEE_PAISE, percent) : null;

  const chennai = cityOf('chennai');
  const top = useQuery({
    queryKey: ['home-top', 'chennai'],
    queryFn: () => discoveryApi.search({ lat: chennai.lat, lng: chennai.lng, radiusKm: 25, sort: 'RATING' }),
    staleTime: 5 * 60 * 1000,
  });
  const cityCounts = useQueries({
    queries: FEATURED_CITIES.map((key) => {
      const c = cityOf(key);
      return {
        queryKey: ['home-city', key],
        queryFn: () => discoveryApi.search({ lat: c.lat, lng: c.lng, radiusKm: 15, sort: 'DISTANCE' as const }),
        staleTime: 5 * 60 * 1000,
      };
    }),
  });

  const values = [
    { icon: BadgeCheck, title: t('home.valueVerifiedTitle'), body: t('home.valueVerifiedBody') },
    { icon: CalendarClock, title: t('home.valueRealtimeTitle'), body: t('home.valueRealtimeBody') },
    {
      icon: IndianRupee,
      title: t('home.valuePricingTitle'),
      body: percent !== undefined ? t('home.valuePricingBody', { percent }) : null,
    },
  ];
  const steps = [
    { icon: MapPin, text: t('home.howStep1') },
    { icon: Search, text: t('home.howStep2') },
    { icon: QrCode, text: t('home.howStep3') },
  ];
  const topItems = (top.data?.items ?? []).filter((h, i, all) => all.findIndex((x) => x.organizationId === h.organizationId) === i).slice(0, 8);

  return (
    <>
      <section className="border-b border-border bg-gradient-to-b from-brand-50 to-surface">
        <div className="mx-auto max-w-7xl px-4 pb-12 pt-12 sm:px-6 md:pt-16">
          <p className="inline-flex rounded-full border border-brand-200 bg-surface px-3 py-1 text-xs font-semibold text-brand-700">
            {t('home.heroEyebrow')}
          </p>
          <h1 className="mt-4 max-w-3xl text-4xl font-extrabold leading-tight tracking-tight sm:text-5xl">{t('home.heroTitle')}</h1>
          <p className="mt-4 max-w-2xl text-lg text-fg-muted">{t('home.heroSubtitle')}</p>
          <div className="mt-8"><HeroSearch /></div>
        </div>
      </section>

      <section className="mx-auto mt-12 max-w-7xl px-4 sm:px-6">
        <SectionHeading title={t('home.typesTitle')} />
        <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
          {categories.data?.map((c) => (
            <Link key={c.id} to={`/search?category=${c.id}`} className="group block">
              <div className="aspect-[4/3] overflow-hidden rounded-2xl bg-surface-muted">
                {CATEGORY_PHOTOS[c.slug] && (
                  <img src={CATEGORY_PHOTOS[c.slug]} alt="" loading="lazy" className="h-full w-full object-cover transition duration-300 group-hover:scale-[1.03]" />
                )}
              </div>
              <p className="mt-2 font-semibold group-hover:text-brand-700">{pick(c.name)}</p>
              {c.description && <p className="text-sm text-fg-muted">{pick(c.description)}</p>}
            </Link>
          ))}
        </div>
      </section>

      <section className="mx-auto mt-14 max-w-7xl px-4 sm:px-6">
        <SectionHeading title={t('home.citiesTitle')} subtitle={t('home.citiesSubtitle')} />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-6">
          {FEATURED_CITIES.map((key, i) => {
            const res = cityCounts[i]?.data;
            const n = res?.items.length ?? 0;
            return (
              <Link key={key} to={`/search?city=${key}`}
                className="group rounded-2xl border border-border bg-surface p-4 transition hover:border-brand-400 hover:shadow-card">
                <MapPin className="h-5 w-5 text-brand-600" aria-hidden="true" />
                <p className="mt-3 font-semibold group-hover:text-brand-700">{t(`search.city.${key}`)}</p>
                <p className="text-sm text-fg-muted">
                  {res ? (res.hasMore ? t('home.placesMany', { count: n }) : t('home.placesCount', { count: n })) : ' '}
                </p>
              </Link>
            );
          })}
        </div>
      </section>

      {topItems.length > 0 && (
        <section className="mx-auto mt-14 max-w-7xl px-4 sm:px-6">
          <SectionHeading
            title={t('home.topTitle', { city: t('search.city.chennai') })}
            subtitle={t('home.topSubtitle')}
            action={
              <Link to="/search?city=chennai&sort=RATING" className="inline-flex items-center gap-1 text-sm font-semibold text-brand-700 hover:underline">
                {t('home.seeAll')}<ChevronRight className="h-4 w-4" aria-hidden="true" />
              </Link>
            }
          />
          <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {topItems.map((h) => <li key={h.organizationId}><PlaceCard h={h} /></li>)}
          </ul>
        </section>
      )}

      <section className="mx-auto mt-16 max-w-7xl px-4 sm:px-6" aria-label={t('home.valuePricingTitle')}>
        <div className="grid gap-5 md:grid-cols-3">
          {values.map(({ icon: Icon, title, body }) => (
            <Card key={title} className="p-6">
              <span className="inline-flex h-11 w-11 items-center justify-center rounded-xl bg-brand-50 text-brand-700">
                <Icon className="h-5 w-5" aria-hidden="true" />
              </span>
              <h3 className="mt-4 font-semibold">{title}</h3>
              {body && <p className="mt-1.5 text-sm text-fg-muted">{body}</p>}
            </Card>
          ))}
        </div>
      </section>

      <section id="how" className="mx-auto mt-16 grid max-w-7xl scroll-mt-24 gap-8 px-4 sm:px-6 lg:grid-cols-[1.4fr_1fr]">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">{t('home.howTitle')}</h2>
          <ol className="mt-6 space-y-3">
            {steps.map(({ icon: Icon, text }, i) => (
              <li key={text} className="flex items-center gap-4 rounded-2xl border border-border p-4">
                <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-brand-600 text-sm font-bold text-white">
                  {i + 1}
                </span>
                <Icon className="h-5 w-5 shrink-0 text-brand-600" aria-hidden="true" />
                <p className="text-sm">{text}</p>
              </li>
            ))}
          </ol>
          <div className="mt-6 flex flex-wrap gap-3">
            <Link to="/search" className={buttonClasses('primary', 'lg')}>{t('home.ctaExplore')}</Link>
            <Link to="/business/register" className={buttonClasses('secondary', 'lg')}>{t('home.ctaBusiness')}</Link>
          </div>
        </div>
        {example && (
          <Card className="self-start p-6 sm:p-8">
            <h2 className="text-sm font-semibold uppercase tracking-wider text-fg-muted">{t('home.exampleTitle')}</h2>
            <dl className="mt-5 space-y-3 text-sm">
              <div className="flex justify-between">
                <dt className="text-fg-muted">{t('home.exampleService')}</dt>
                <dd className="font-medium">{formatINR(paiseToRupees(example.serviceFeePaise))}</dd>
              </div>
              <div className="flex justify-between">
                <dt className="text-fg-muted">{t('home.examplePlatform', { percent })}</dt>
                <dd className="font-medium">{formatINR(paiseToRupees(example.platformFeePaise))}</dd>
              </div>
              <div className="flex justify-between border-t border-border pt-3 text-base">
                <dt className="font-semibold">{t('home.exampleTotal')}</dt>
                <dd className="font-bold text-brand-700">{formatINR(paiseToRupees(example.totalPaise))}</dd>
              </div>
            </dl>
          </Card>
        )}
      </section>
    </>
  );
}
