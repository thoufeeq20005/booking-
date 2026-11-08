import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { BadgePercent, CalendarDays, Gift, History, LocateFixed, MapPin, Package, Search, Sparkles } from 'lucide-react';
import clsx from 'clsx';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { LoadingState } from '@/components/ui/StateMessage';
import { fileUrl } from '@/features/organization/shared';
import { useVenueLocation } from '@/features/venue/shared';
import { CITIES } from '@/services/discoveryApi';
import { recommendApi, type RecommendedOffer } from '@/services/recommendApi';
import { interestsOf, useRecent } from '@/features/search/recent';

const KIND_ICON = { FREE: Gift, FREE_SERVICE: Gift, PACKAGE: Package, EVENT: CalendarDays, DISCOUNT: BadgePercent } as const;
const CHIPS = ['cafe', 'health', 'gym', 'learning', 'beauty'] as const;

function OfferBadge({ offer }: { offer: RecommendedOffer }) {
  const { t } = useTranslation();
  const Icon = KIND_ICON[offer.type];
  const label = offer.percentOff ? t('reco.percentOff', { percent: offer.percentOff }) : t(`reco.kind.${offer.type}`);
  return (
    <span className={clsx('inline-flex items-center gap-1 rounded-md px-2 py-0.5 text-xs font-semibold',
      offer.type === 'FREE' || offer.type === 'FREE_SERVICE' ? 'bg-emerald-100 text-emerald-800'
        : offer.type === 'PACKAGE' ? 'bg-violet-100 text-violet-800' : offer.type === 'EVENT' ? 'bg-amber-100 text-amber-800'
          : 'bg-brand-100 text-brand-800')}>
      <Icon className="h-3.5 w-3.5" aria-hidden="true" />{label}
    </span>
  );
}

/**
 * "Picked for you nearby": real offers around the visitor, chosen and explained by AI when the server has an API key.
 * The location is sent with the request only; it is not stored.
 */
export function ForYouSection() {
  const { t } = useTranslation();
  const [loc, setLoc] = useVenueLocation();
  const [text, setText] = useState('');
  const [q, setQ] = useState('');
  const [locating, setLocating] = useState(false);
  const [denied, setDenied] = useState(false);
  // Their recent searches personalise the picks when they haven't asked for something specific.
  const interests = interestsOf(useRecent());
  const query = useQuery({
    queryKey: ['recommendations', loc.lat.toFixed(3), loc.lng.toFixed(3), q, q ? '' : interests.join('|')],
    queryFn: () => recommendApi.nearby({ lat: loc.lat, lng: loc.lng, q: q || undefined, interests: q ? undefined : interests }),
    placeholderData: keepPreviousData,
    staleTime: 5 * 60 * 1000,
  });
  const where = loc.label === 'gps' ? t('reco.yourLocation') : t(`search.city.${loc.label}`, { defaultValue: CITIES[0].key });
  const useGps = () => {
    if (!navigator.geolocation) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => { setLoc({ lat: p.coords.latitude, lng: p.coords.longitude, label: 'gps' }); setLocating(false); setDenied(false); },
      () => { setLocating(false); setDenied(true); },
      { timeout: 15000, maximumAge: 10 * 60 * 1000 },
    );
  };
  const submit = (e: FormEvent) => { e.preventDefault(); setQ(text.trim()); };
  const data = query.data;

  return (
    <section className="mx-auto mt-12 max-w-7xl px-4 sm:px-6" aria-labelledby="for-you">
      <Card className="overflow-hidden">
        <div className="bg-gradient-to-r from-brand-700 via-brand-600 to-violet-600 px-6 py-6 text-white">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 id="for-you" className="flex items-center gap-2 text-2xl font-bold tracking-tight">
                <Sparkles className="h-6 w-6 text-amber-300" aria-hidden="true" />{t('reco.title')}
              </h2>
              <p className="mt-1 flex items-center gap-1 text-sm text-white/85"><MapPin className="h-4 w-4" aria-hidden="true" />{t('reco.near', { place: where })}</p>
            </div>
            <Button variant="secondary" size="sm" loading={locating} onClick={useGps}>
              <LocateFixed className="h-4 w-4" aria-hidden="true" />{t('business.useMyLocation')}
            </Button>
          </div>
          <form role="search" onSubmit={submit} className="mt-4 flex max-w-2xl items-center gap-2 rounded-full bg-white p-1.5 pl-4 text-fg">
            <Search className="h-4 w-4 shrink-0 text-fg-muted" aria-hidden="true" />
            <input value={text} onChange={(e) => setText(e.target.value)} maxLength={100} aria-label={t('reco.askLabel')}
              placeholder={t('reco.askPlaceholder')} className="h-9 min-w-0 flex-1 bg-transparent text-sm outline-none" />
            <button type="submit" className="rounded-full bg-brand-600 px-4 py-1.5 text-sm font-semibold text-white hover:bg-brand-700">{t('reco.ask')}</button>
          </form>
          <div className="mt-3 flex flex-wrap gap-2">
            {CHIPS.map((c) => {
              const value = t(`reco.chip.${c}`);
              return (
                <button key={c} type="button" aria-pressed={q === value} onClick={() => { setText(value); setQ(q === value ? '' : value); }}
                  className={clsx('rounded-full border px-3 py-1 text-xs font-medium', q === value ? 'border-white bg-white text-brand-700' : 'border-white/50 text-white hover:bg-white/10')}>
                  {value}
                </button>
              );
            })}
          </div>
          {denied && <p className="mt-2 text-xs text-white/80">{t('reco.locationDenied')}</p>}
        </div>

        <div className="p-6">
          {query.isLoading && <LoadingState label={t('reco.loading')} />}
          {data && data.items.length === 0 && <p className="text-sm text-fg-muted">{t('reco.none')}</p>}
          {data && data.items.length > 0 && (
            <>
              {data.basedOn && data.basedOn.length > 0 && (
                <p className="mb-3 inline-flex items-center gap-1.5 rounded-full bg-violet-50 px-3 py-1 text-xs font-medium text-violet-800">
                  <History className="h-3.5 w-3.5" aria-hidden="true" />{t('reco.basedOn', { terms: data.basedOn.join(', ') })}
                </p>
              )}
              {data.source === 'AI' && data.intro && <p className="mb-4 text-sm">{data.intro}</p>}
              <ul className={clsx('grid gap-4 sm:grid-cols-2 lg:grid-cols-3', query.isFetching && 'opacity-60')}>
                {data.items.map(({ offer, headline, reason }) => (
                  <li key={offer.id}>
                    <Link to={`/b/${offer.slug}`} className="group flex h-full gap-3 rounded-2xl border border-border p-3 transition hover:border-brand-300 hover:shadow-card">
                      <div className="h-20 w-20 shrink-0 overflow-hidden rounded-xl bg-surface-muted">
                        {offer.coverUrl && <img src={fileUrl(offer.coverUrl)} alt="" loading="lazy" className="h-full w-full object-cover" />}
                      </div>
                      <div className="min-w-0 space-y-1 text-sm">
                        <OfferBadge offer={offer} />
                        <p className="font-semibold leading-snug group-hover:text-brand-700">{headline}</p>
                        {reason && <p className="text-fg-muted">{reason}</p>}
                        <p className="truncate text-xs text-fg-muted">{offer.businessName} · {offer.category} · {t('search.distance', { km: offer.distanceKm })}</p>
                      </div>
                    </Link>
                  </li>
                ))}
              </ul>
              <p className="mt-4 flex items-center gap-1 text-xs text-fg-muted">
                {data.source === 'AI' && <Sparkles className="h-3.5 w-3.5 text-violet-500" aria-hidden="true" />}
                {t(data.source === 'AI' ? 'reco.byAi' : 'reco.byRules')} {t('reco.privacy')}
              </p>
            </>
          )}
        </div>
      </Card>
    </section>
  );
}
