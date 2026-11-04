import { useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Clock, Globe, Mail, MapPin, Phone, Star } from 'lucide-react';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { DAY_KEYS, fileUrl, useLocalized } from '@/features/organization/shared';
import { intlLocale } from '@/i18n';
import { BookingWizard } from '@/features/booking/BookingWizard';
import { BusinessReviews } from '@/features/engagement/ReviewWidgets';
import { FavouriteButton } from '@/features/engagement/FavouriteButton';
import { discoveryApi, mapsLink } from '@/services/discoveryApi';
import { formatINR, formatWallTime } from '@/utils/format';

export function BusinessPage() {
  const { t, i18n } = useTranslation();
  const pick = useLocalized();
  const slug = useParams().slug ?? '';
  const { data: p, error, isLoading, refetch } = useQuery({ queryKey: ['public-org', slug], queryFn: () => discoveryApi.profile(slug) });
  const locale = intlLocale(i18n.language);

  if (isLoading) return <LoadingState />;
  if (error || !p) return <ErrorState error={error} onRetry={() => void refetch()} />;

  const priceRange = (prices: (number | string)[]) => {
    const nums = prices.map(Number);
    const lo = Math.min(...nums);
    const hi = Math.max(...nums);
    return lo === hi ? formatINR(lo) : `${formatINR(lo)} – ${formatINR(hi)}`;
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-center gap-4">
        {p.logoUrl ? <img src={fileUrl(p.logoUrl)} alt="" className="h-16 w-16 rounded-2xl object-cover" />
          : <span className="flex h-16 w-16 items-center justify-center rounded-2xl bg-brand-50 text-2xl font-bold text-brand-700">{p.name.charAt(0)}</span>}
        <div>
          <h1 className="flex items-center gap-2 text-3xl font-bold tracking-tight">{p.name} <FavouriteButton orgId={p.id} /></h1>
          <p className="text-fg-muted">
            {pick(p.categoryName)} › {pick(p.subcategoryName)}
            {p.reviewCount > 0 && (
              <span className="ml-2 inline-flex items-center gap-1"><Star className="h-4 w-4 fill-amber-400 text-amber-400" aria-hidden="true" />
                {Number(p.ratingAvg).toFixed(1)} ({t('biz.reviews', { count: p.reviewCount })})</span>
            )}
          </p>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-[1fr_24rem]">
        <div className="space-y-6">
          {p.description && <Card className="whitespace-pre-line p-6 text-sm">{p.description}</Card>}
          <Card className="p-6">
            <h2 className="text-lg font-semibold">{t('business.tab.services')}</h2>
            <ul className="mt-3 divide-y divide-border">
              {p.services.map((s) => (
                <li key={s.id} className="flex flex-wrap justify-between gap-2 py-3 text-sm">
                  <div>
                    <p className="font-medium">{s.name}</p>
                    <p className="text-fg-muted">{t('team.summary', { minutes: s.durationMinutes, price: priceRange(Object.values(s.branchPrices)) })}
                      {s.consultationType === 'ONLINE' && ` · ${t('team.typeName.ONLINE')}`}</p>
                    {s.description && <p className="mt-1 text-fg-muted">{s.description}</p>}
                  </div>
                </li>
              ))}
              {p.services.length === 0 && <li className="py-3 text-sm text-fg-muted">{t('team.noServices')}</li>}
            </ul>
          </Card>
          {p.staff.length > 0 && (
            <Card className="p-6">
              <h2 className="text-lg font-semibold">{t('biz.team')}</h2>
              <ul className="mt-3 grid gap-3 sm:grid-cols-2">
                {p.staff.map((s) => (
                  <li key={s.id} className="text-sm">
                    <p className="font-medium">{[s.title, s.displayName].filter(Boolean).join(' ')}</p>
                    {s.specialisation && <p className="text-fg-muted">{s.specialisation}</p>}
                  </li>
                ))}
              </ul>
            </Card>
          )}
          <BusinessReviews slug={p.slug} rating={Number(p.ratingAvg)} count={p.reviewCount} />
          {p.branches.map((b) => (
            <Card key={b.id} className="p-6 text-sm">
              <h2 className="text-lg font-semibold">{b.name}</h2>
              <p className="mt-2 flex items-start gap-2"><MapPin className="mt-0.5 h-4 w-4 text-brand-600" aria-hidden="true" />
                <span>{[b.addressLine1, b.addressLine2, b.locality, b.city, b.state, b.pincode].filter(Boolean).join(', ')}{' '}
                  <a className="text-brand-700 hover:underline" href={mapsLink(b.latitude, b.longitude)} target="_blank" rel="noopener noreferrer">{t('search.openMap')}</a></span></p>
              {b.phone && <p className="mt-1 flex items-center gap-2"><Phone className="h-4 w-4 text-brand-600" aria-hidden="true" /><a href={`tel:${b.phone}`}>{b.phone}</a></p>}
              <div className="mt-3 flex items-start gap-2"><Clock className="mt-0.5 h-4 w-4 text-brand-600" aria-hidden="true" />
                <ul>
                  {DAY_KEYS.map((d, i) => {
                    const h = b.hours.find((x) => x.dayOfWeek === i + 1);
                    return (
                      <li key={d} className="flex gap-3">
                        <span className="w-10 text-fg-muted">{t(`days.${d}`)}</span>
                        <span>{h ? `${formatWallTime(h.opensAt, locale)}–${formatWallTime(h.closesAt, locale)}` : t('business.closed')}</span>
                      </li>
                    );
                  })}
                </ul>
              </div>
            </Card>
          ))}
        </div>
        <aside className="space-y-4">
          <BookingWizard profile={p} />
          <Card className="space-y-2 p-5 text-sm">
            {p.contactPhone && <p className="flex items-center gap-2"><Phone className="h-4 w-4 text-brand-600" aria-hidden="true" /><a href={`tel:${p.contactPhone}`}>{p.contactPhone}</a></p>}
            {p.contactEmail && <p className="flex items-center gap-2"><Mail className="h-4 w-4 text-brand-600" aria-hidden="true" /><a href={`mailto:${p.contactEmail}`}>{p.contactEmail}</a></p>}
            {p.website && <p className="flex items-center gap-2"><Globe className="h-4 w-4 text-brand-600" aria-hidden="true" /><a href={p.website} target="_blank" rel="noopener noreferrer nofollow" className="text-brand-700 hover:underline">{p.website}</a></p>}
            <p className="text-fg-muted">{t(`biz.mode.${p.confirmationMode}`)}</p>
          </Card>
        </aside>
      </div>
    </div>
  );
}
