import { useEffect, useMemo, useRef, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, BadgePercent, Check, Clock, MapPin, Phone, Star, Users, X } from 'lucide-react';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { LoadingState } from '@/components/ui/StateMessage';
import { useAuth } from '@/contexts/AuthContext';
import { BusinessReviews } from '@/features/engagement/ReviewWidgets';
import { FavouriteButton } from '@/features/engagement/FavouriteButton';
import { LocationButton } from '@/features/maps/LocationButton';
import { fileUrl } from '@/features/organization/shared';
import { useApiForm } from '@/hooks/useApiForm';
import { nextDays } from '@/services/bookingApi';
import type { PublicProfile } from '@/services/discoveryApi';
import { venueApi, type DiningDetails, type DiningOffer, type Restaurant, type TableSlot } from '@/services/venueApi';
import { formatINR, formatWallTime } from '@/utils/format';
import { DiningMenu } from './DiningPanel';
import { RestaurantCard } from './RestaurantCard';
import { useVenueFormat } from './shared';

const offerFor = (offers: DiningOffer[], meal: 'LUNCH' | 'DINNER') => offers.filter((o) => o.meal === 'ALL_DAY' || o.meal === meal);

export function RatingBadge({ value, count, className }: { value: number; count?: number; className?: string }) {
  const { t } = useTranslation();
  return (
    <span className={clsx('inline-flex flex-col items-center rounded-lg bg-emerald-600 px-2 py-0.5 text-white', className)}
      aria-label={t('venue.ratingLabel', { rating: value.toFixed(1), count: count ?? 0 })}>
      <span className="flex items-center gap-0.5 text-sm font-bold">{value.toFixed(1)}<Star className="h-3 w-3 fill-white" aria-hidden="true" /></span>
      {count != null && <span className="text-[10px] opacity-90">{count}</span>}
    </span>
  );
}

/** Guests → day → lunch/dinner → time (each showing its offer) → offer → pay the cover charge. */
export function TableBookingSheet({ profile, details, initialOffer, onClose }: {
  profile: PublicProfile; details: DiningDetails; initialOffer?: number; onClose: () => void;
}) {
  const { t } = useTranslation();
  const { status } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const f = useVenueFormat();
  const form = useApiForm();
  const ref = useRef<HTMLDivElement>(null);
  const branchId = profile.branches[0]?.id ?? 0;
  const [guests, setGuests] = useState<number | null>(null);
  const days = nextDays(7);
  const [date, setDate] = useState(days[0]!);
  const [meal, setMeal] = useState<'LUNCH' | 'DINNER'>(new Date().getHours() >= 16 ? 'DINNER' : 'LUNCH');
  const [slot, setSlot] = useState<TableSlot>();
  const [offerId, setOfferId] = useState<number | undefined>(initialOffer);
  useEffect(() => { ref.current?.focus(); }, []);
  const slots = useQuery({
    queryKey: ['table-slots', branchId, date, guests],
    queryFn: () => venueApi.tableSlots(branchId, date, guests!),
    enabled: guests != null,
  });
  const shown = (slots.data ?? []).filter((s) => s.meal === meal);
  const options = slot ? offerFor(details.offers, slot.meal) : [];
  const chosen = options.find((o) => o.id === offerId) ?? options[0];
  const cover = Number(details.coverPerGuest) * (guests ?? 0);
  const bestFor = (m: 'LUNCH' | 'DINNER') => offerFor(details.offers, m)[0];

  const book = async () => {
    if (!slot || guests == null) return;
    const b = await form.submit(() => venueApi.bookTable({ branchId, startsAt: slot.startsAt, partySize: guests, offerId: chosen?.id }));
    if (!b) { void slots.refetch(); return; }
    if (b.status === 'PENDING_PAYMENT') navigate(`/customer/bookings/${b.id}/pay`);
    else navigate(`/customer/bookings/${b.id}`, { state: { justBooked: true } });
  };

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/50 sm:items-center sm:p-4" onKeyDown={(e) => { if (e.key === 'Escape') onClose(); }}>
      <div ref={ref} tabIndex={-1} role="dialog" aria-modal="true" aria-labelledby="table-sheet"
        className="flex max-h-[92vh] w-full max-w-xl flex-col overflow-hidden rounded-t-3xl bg-surface shadow-lift outline-none sm:rounded-3xl">
        <div className="flex items-center justify-between border-b border-border px-5 py-4">
          <h2 id="table-sheet" className="text-lg font-semibold">{t('venue.bookingAt', { name: profile.name })}</h2>
          <button type="button" onClick={onClose} aria-label={t('common.cancel')} className="rounded-lg p-1 hover:bg-surface-muted"><X className="h-5 w-5" aria-hidden="true" /></button>
        </div>

        <div className="flex-1 space-y-5 overflow-y-auto px-5 py-4">
          {guests == null ? (
            <section>
              <h3 className="font-medium">{t('venue.numberOfGuests')}</h3>
              <div role="radiogroup" aria-label={t('venue.numberOfGuests')} className="mt-3 grid grid-cols-5 gap-2">
                {Array.from({ length: details.maxPartySize }, (_, i) => i + 1).map((n) => (
                  <button key={n} type="button" role="radio" aria-checked={false} onClick={() => setGuests(n)}
                    className="h-14 rounded-xl border border-border text-lg font-medium hover:border-brand-500 hover:bg-brand-50">{n}</button>
                ))}
              </div>
            </section>
          ) : (
            <>
              <button type="button" onClick={() => { setGuests(null); setSlot(undefined); }}
                className="inline-flex items-center gap-1 rounded-full border border-border px-3 py-1 text-sm hover:border-brand-400">
                <Users className="h-4 w-4" aria-hidden="true" />{t('venue.guestsCount', { count: guests })} ▾
              </button>
              <section>
                <h3 className="text-sm font-medium text-fg-muted">{t('venue.chooseDateTime')}</h3>
                <div role="tablist" aria-label={t('booking.date')} className="-mx-1 mt-2 flex gap-1 overflow-x-auto border-b border-border px-1">
                  {days.map((d, i) => {
                    const p = f.dayParts(d);
                    return (
                      <button key={d} type="button" role="tab" aria-selected={d === date} onClick={() => { setDate(d); setSlot(undefined); }}
                        className={clsx('shrink-0 border-b-2 px-3 pb-2 text-center text-sm', d === date ? 'border-brand-600 font-semibold text-brand-700' : 'border-transparent text-fg-muted hover:text-fg')}>
                        <span className="block">{i === 0 ? t('venue.today') : i === 1 ? t('venue.tomorrow') : `${p.day} ${p.month}`}</span>
                        <span className="block text-xs">{p.weekday}</span>
                      </button>
                    );
                  })}
                </div>
                <div role="radiogroup" aria-label={t('venue.meal')} className="mt-3 grid grid-cols-2 rounded-xl bg-surface-muted p-1">
                  {(['LUNCH', 'DINNER'] as const).map((m) => (
                    <button key={m} type="button" role="radio" aria-checked={meal === m} onClick={() => { setMeal(m); setSlot(undefined); }}
                      className={clsx('rounded-lg py-2 text-sm font-medium', meal === m ? 'bg-surface shadow-card' : 'text-fg-muted')}>
                      {t(`venue.mealName.${m}`)}
                    </button>
                  ))}
                </div>
                {slots.isLoading && <LoadingState />}
                {slots.data && shown.length === 0 && <p className="mt-3 text-sm text-fg-muted">{t('venue.noTablesMeal')}</p>}
                <div role="radiogroup" aria-label={t('booking.time')} className="mt-3 grid grid-cols-3 gap-2">
                  {shown.map((s) => {
                    const best = bestFor(s.meal);
                    return (
                      <button key={s.startsAt} type="button" role="radio" aria-checked={slot?.startsAt === s.startsAt} disabled={!s.available}
                        onClick={() => { setSlot(s); setOfferId(offerFor(details.offers, s.meal).find((o) => o.id === offerId)?.id); }}
                        className={clsx('rounded-xl border px-2 py-2 text-center',
                          !s.available ? 'cursor-not-allowed border-border opacity-40'
                            : slot?.startsAt === s.startsAt ? 'border-brand-600 bg-brand-50 ring-1 ring-brand-600' : 'border-border hover:border-brand-400')}>
                        <span className="block text-sm font-semibold tabular-nums">{f.time(s.startsAt)}</span>
                        <span className="block truncate text-[11px] text-brand-700">{best ? `${best.percent}% ${t('venue.off')}` : t('venue.noOffer')}</span>
                      </button>
                    );
                  })}
                </div>
              </section>

              {slot && options.length > 0 && (
                <section>
                  <h3 className="font-medium">{t('venue.selectOption')}</h3>
                  <div role="radiogroup" aria-label={t('venue.selectOption')} className="mt-2 space-y-2">
                    {options.map((o) => (
                      <button key={o.id} type="button" role="radio" aria-checked={chosen?.id === o.id} onClick={() => setOfferId(o.id)}
                        className={clsx('flex w-full items-center gap-3 rounded-2xl border p-4 text-left',
                          chosen?.id === o.id ? 'border-brand-600 bg-brand-50' : 'border-border hover:border-brand-300')}>
                        <BadgePercent className="h-8 w-8 shrink-0 text-brand-600" aria-hidden="true" />
                        <span className="min-w-0 flex-1">
                          <span className="block font-semibold">{o.title}</span>
                          <span className="block text-sm text-fg-muted">{t('venue.coverLine', { amount: formatINR(details.coverPerGuest) })}</span>
                        </span>
                        <span className={clsx('flex h-5 w-5 items-center justify-center rounded-full border', chosen?.id === o.id ? 'border-brand-600 bg-brand-600 text-white' : 'border-border')}>
                          {chosen?.id === o.id && <Check className="h-3 w-3" aria-hidden="true" />}
                        </span>
                      </button>
                    ))}
                  </div>
                </section>
              )}
            </>
          )}
        </div>

        {guests != null && (
          <div className="space-y-2 border-t border-border bg-surface-muted px-5 py-4">
            <p className="text-sm">{t('venue.totalCover', { amount: formatINR(cover) })} <span className="text-brand-700">{t('venue.redeemable')}</span></p>
            {form.formError && <Alert tone="error">{form.formError}</Alert>}
            {status === 'authenticated' ? (
              <Button size="lg" className="w-full" disabled={!slot} loading={form.submitting} onClick={() => void book()}>{t('venue.proceedToBook')}</Button>
            ) : (
              <Link to={`/signin?next=${encodeURIComponent(location.pathname)}`} className={buttonClasses('primary', 'lg', 'w-full')}>{t('booking.signInToBook')}</Link>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

function SampleBill({ details }: { details: DiningDetails }) {
  const { t } = useTranslation();
  const best = details.offers[0];
  const bill = Number(details.costForTwo ?? 0);
  if (!best || !bill) return null;
  // The cover charge paid when booking comes off the bill, so overall you pay the bill minus the offer.
  const youPay = bill - Math.round(bill * best.percent) / 100;
  return (
    <div className="rounded-2xl border border-dashed border-border bg-surface-muted p-4 text-sm">
      <p className="text-xs uppercase tracking-wide text-fg-muted">{t('venue.sampleBill')}</p>
      <dl className="mt-2 space-y-1">
        <div className="flex justify-between"><dt>{t('venue.estimatedBill')}</dt><dd>{formatINR(bill)}</dd></div>
        <div className="flex justify-between font-semibold"><dt>{t('venue.youPay')}</dt><dd>{formatINR(youPay)}</dd></div>
        <div className="flex justify-between text-brand-700"><dt>{t('venue.youSave')}</dt><dd>{formatINR(bill - youPay)}</dd></div>
      </dl>
    </div>
  );
}

const TABS = ['offers', 'menu', 'reviews', 'facilities', 'more'] as const;

/** The restaurant page: about, offers, menu, reviews, facilities and similar places, with Book a table and Pay bill. */
export function RestaurantPage({ profile: p, details }: { profile: PublicProfile; details: DiningDetails }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const f = useVenueFormat();
  const [sheet, setSheet] = useState<{ offer?: number } | null>(null);
  const [tab, setTab] = useState<(typeof TABS)[number]>('offers');
  const b = p.branches[0];
  const more = useQuery({
    queryKey: ['dining-more', b?.id],
    queryFn: () => venueApi.restaurants({ lat: Number(b!.latitude), lng: Number(b!.longitude), radiusKm: 10 }),
    enabled: Boolean(b),
  });
  const others = (more.data ?? []).filter((r: Restaurant) => r.organizationId !== p.id).slice(0, 6);
  const today = b?.hours.find((h) => h.dayOfWeek === ((new Date().getDay() + 6) % 7) + 1);
  const go = (id: (typeof TABS)[number]) => { setTab(id); document.getElementById(`r-${id}`)?.scrollIntoView?.({ behavior: 'smooth', block: 'start' }); };
  const ratings = useMemo(() => Number(p.ratingAvg), [p.ratingAvg]);

  return (
    <div className="pb-32">
      <div className="mx-auto max-w-5xl px-4 pt-6 sm:px-6">
        <Link to="/dining" className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg"><ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('venue.diningTitle')}</Link>
        <div className="mt-3 grid gap-2 overflow-hidden rounded-3xl sm:grid-cols-[2fr_1fr]">
          {p.coverUrl
            ? <img src={fileUrl(p.coverUrl)} alt={p.name} className="aspect-[16/9] h-full w-full object-cover sm:row-span-2" />
            : <div className="aspect-[16/9] bg-gradient-to-br from-amber-100 to-rose-100 sm:row-span-2" />}
          {p.logoUrl && <img src={fileUrl(p.logoUrl)} alt="" className="hidden h-full w-full object-cover sm:block" />}
        </div>

        <div className="mt-5 flex flex-wrap items-start justify-between gap-4">
          <div className="min-w-0 space-y-1">
            <h1 className="flex items-center gap-2 text-3xl font-bold tracking-tight">{p.name}<FavouriteButton orgId={p.id} /></h1>
            <p className="text-fg-muted">{[details.cuisines, details.costForTwo != null ? t('venue.costForTwo', { price: formatINR(details.costForTwo) }) : null].filter(Boolean).join(' · ')}</p>
            {b && <p className="flex items-center gap-1 text-sm text-fg-muted"><MapPin className="h-4 w-4" aria-hidden="true" />{[b.locality, b.city].filter(Boolean).join(', ')}</p>}
            <p className="flex items-center gap-1 text-sm text-fg-muted"><Clock className="h-4 w-4" aria-hidden="true" />
              {today ? `${t('venue.openToday')} · ${formatWallTime(today.opensAt, f.locale)} – ${formatWallTime(today.closesAt, f.locale)}` : t('venue.closedToday')}</p>
            <p className="text-sm">{details.tier === 'PREMIUM' ? t('venue.tierName.PREMIUM') : t('venue.tierName.CAFE')}{details.vegOnly && ` · ${t('venue.pureVeg')}`}</p>
          </div>
          {p.reviewCount > 0 && <RatingBadge value={ratings} count={p.reviewCount} className="px-3 py-1" />}
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          {(p.contactPhone || b?.phone) && (
            <a href={`tel:${b?.phone ?? p.contactPhone}`} className={buttonClasses('secondary', 'sm')}><Phone className="h-4 w-4" aria-hidden="true" />{t('venue.call')}</a>
          )}
          {b && <LocationButton place={{ name: p.name, address: [b.addressLine1, b.locality, b.city].filter(Boolean).join(', '), latitude: Number(b.latitude), longitude: Number(b.longitude) }} />}
        </div>
      </div>

      <nav aria-label={t('venue.sections')} className="sticky top-16 z-20 mt-6 border-b border-border bg-surface/95 backdrop-blur">
        <div className="mx-auto flex max-w-5xl gap-1 overflow-x-auto px-4 sm:px-6">
          {TABS.map((id) => (
            <button key={id} type="button" onClick={() => go(id)} aria-current={tab === id ? 'true' : undefined}
              className={clsx('shrink-0 border-b-2 px-3 py-3 text-sm font-medium', tab === id ? 'border-brand-600 text-brand-700' : 'border-transparent text-fg-muted hover:text-fg')}>
              {t(`venue.tab.${id}`)}
            </button>
          ))}
        </div>
      </nav>

      <div className="mx-auto max-w-5xl space-y-10 px-4 py-8 sm:px-6">
        <section id="r-offers" className="scroll-mt-32 space-y-4">
          <h2 className="text-xl font-bold">{t('venue.offersTitle')}</h2>
          {details.offers.length === 0 ? <p className="text-sm text-fg-muted">{t('venue.noOffers')}</p> : (
            <ul className="-mx-1 flex gap-4 overflow-x-auto px-1 pb-2">
              {details.offers.map((o) => (
                <li key={o.id} className="w-80 shrink-0">
                  <div className="relative flex overflow-hidden rounded-2xl bg-gradient-to-r from-brand-700 to-brand-500 text-white shadow-card">
                    <div className="flex w-28 shrink-0 flex-col justify-center border-r border-dashed border-white/40 p-4">
                      <span className="text-xs uppercase tracking-wide opacity-80">{t('venue.flat')}</span>
                      <span className="text-3xl font-extrabold leading-none">{o.percent}%</span>
                      <span className="text-xs font-semibold uppercase">{t('venue.off')}</span>
                    </div>
                    <div className="min-w-0 flex-1 space-y-1 p-4">
                      <p className="truncate font-semibold">{o.title}</p>
                      <p className="text-xs opacity-90">{t(`venue.mealWhen.${o.meal}`)} · {t('venue.coverShort', { amount: formatINR(details.coverPerGuest) })}</p>
                      <button type="button" onClick={() => setSheet({ offer: o.id })} className="mt-1 text-sm font-semibold underline-offset-2 hover:underline">{t('venue.bookNow')} ›</button>
                    </div>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <SampleBill details={details} />
          {details.offer && <p className="rounded-xl bg-amber-50 p-3 text-sm text-amber-900">{details.offer}</p>}
        </section>

        <section id="r-menu" className="scroll-mt-32"><DiningMenu details={details} /></section>

        <section id="r-reviews" className="scroll-mt-32"><BusinessReviews slug={p.slug} rating={ratings} count={p.reviewCount} /></section>

        <section id="r-facilities" className="scroll-mt-32">
          <h2 className="text-xl font-bold">{t('venue.facilities')}</h2>
          {details.features.length === 0 ? <p className="mt-2 text-sm text-fg-muted">{t('venue.noFacilities')}</p> : (
            <ul className="mt-3 grid grid-cols-2 gap-2 sm:grid-cols-3">
              {details.features.map((x) => <li key={x} className="flex items-center gap-2 text-sm"><span className="h-1.5 w-1.5 rotate-45 bg-brand-500" aria-hidden="true" />{x}</li>)}
            </ul>
          )}
          {p.description && <p className="mt-4 whitespace-pre-line text-sm text-fg-muted">{p.description}</p>}
        </section>

        <section id="r-more" className="scroll-mt-32">
          <h2 className="text-xl font-bold">{t('venue.exploreMore')}</h2>
          {others.length === 0 ? <p className="mt-2 text-sm text-fg-muted">{t('venue.noRestaurants')}</p> : (
            <ul className="-mx-1 mt-3 flex gap-5 overflow-x-auto px-1 pb-2">
              {others.map((r) => <li key={r.branchId} className="w-72 shrink-0"><RestaurantCard r={r} /></li>)}
            </ul>
          )}
        </section>
      </div>

      <div className="fixed inset-x-0 bottom-0 z-30 border-t border-border bg-surface/95 backdrop-blur">
        <div className="mx-auto max-w-5xl space-y-2 px-4 py-3 sm:px-6">
          {details.offers[0] && <p className="text-center text-sm text-brand-700">{t('venue.saveUpTo', { percent: details.offers[0].percent })}</p>}
          <div className="grid grid-cols-2 gap-3">
            <Button variant="secondary" size="lg" onClick={() => setSheet({})}>{t('venue.bookTable')}</Button>
            <Button size="lg" onClick={() => navigate(`/dining/${p.slug}/pay-bill`)}>{t('venue.payBill')}</Button>
          </div>
        </div>
      </div>

      {sheet && <TableBookingSheet profile={p} details={details} initialOffer={sheet.offer} onClose={() => setSheet(null)} />}
    </div>
  );
}
