import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { LocateFixed, MapPin, Search, Star } from 'lucide-react';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField } from '@/components/ui/FormFields';
import { EmptyState, ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { CategoryIcon, fileUrl, useLocalized } from '@/features/organization/shared';
import { usePublicCategories } from '@/services/organizationApi';
import { CITIES, discoveryApi, mapsLink, type SortKey } from '@/services/discoveryApi';
import { formatINR } from '@/utils/format';
import { storage } from '@/utils/storage';
import { useAuth } from '@/contexts/AuthContext';
import { compareList, extrasApi } from '@/services/extrasApi';

const LOCATION_KEY = 'bookeasy.location';
type Loc = { lat: number; lng: number; label: string };

function savedLocation(): Loc {
  try {
    const raw = storage.get(LOCATION_KEY);
    if (raw) return JSON.parse(raw) as Loc;
  } catch {
    // fall through to the default city
  }
  return { lat: CITIES[0].lat, lng: CITIES[0].lng, label: 'chennai' };
}

export function SearchPage() {
  const { t } = useTranslation();
  const pick = useLocalized();
  const categories = usePublicCategories();
  const [params, setParams] = useSearchParams();
  const [loc, setLoc] = useState<Loc>(savedLocation);
  const [locating, setLocating] = useState(false);
  const [text, setText] = useState(params.get('q') ?? '');
  const { status } = useAuth();
  const saved = useQuery({ queryKey: ['saved-locations'], queryFn: extrasApi.locations, enabled: status === 'authenticated' });
  const [compare, setCompare] = useState<string[]>(compareList.get);
  const q = params.get('q') ?? '';
  const categoryId = params.get('category') ? Number(params.get('category')) : undefined;
  const radiusKm = Number(params.get('radius') ?? 10);
  const sort = (params.get('sort') ?? 'DISTANCE') as SortKey;

  useEffect(() => {
    storage.set(LOCATION_KEY, JSON.stringify(loc));
  }, [loc]);

  const query = useQuery({
    queryKey: ['search', loc.lat, loc.lng, q, categoryId, radiusKm, sort],
    queryFn: () => discoveryApi.search({ lat: loc.lat, lng: loc.lng, radiusKm, categoryId, q: q || undefined, sort }),
    placeholderData: keepPreviousData,
  });
  const update = (patch: Record<string, string>) => {
    const next = new URLSearchParams(params);
    Object.entries(patch).forEach(([k, v]) => (v ? next.set(k, v) : next.delete(k)));
    setParams(next, { replace: true });
  };
  const useGps = () => {
    if (!navigator.geolocation) return;
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => { setLoc({ lat: p.coords.latitude, lng: p.coords.longitude, label: 'gps' }); setLocating(false); },
      () => setLocating(false),
      { timeout: 15000 },
    );
  };

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('search.title')}</h1>
      <form className="flex flex-wrap gap-2" onSubmit={(e) => { e.preventDefault(); update({ q: text.trim() }); }} role="search">
        <input aria-label={t('search.placeholder')} placeholder={t('search.placeholder')} value={text} onChange={(e) => setText(e.target.value)}
          className="h-11 min-w-0 flex-1 rounded-xl border border-border bg-surface px-3.5 text-sm" />
        <Button type="submit"><Search className="h-4 w-4" aria-hidden="true" />{t('admin.search')}</Button>
      </form>
      <div className="flex flex-wrap items-end gap-3">
        <SelectField label={t('search.location')} className="w-48" value={loc.label === 'gps' ? '' : loc.label}
          onChange={(e) => {
            const v = e.target.value;
            const s = saved.data?.find((x) => `saved:${x.id}` === v);
            if (s) { setLoc({ lat: Number(s.latitude), lng: Number(s.longitude), label: v }); return; }
            const c = CITIES.find((x) => x.key === v);
            if (c) setLoc({ lat: c.lat, lng: c.lng, label: c.key });
          }}>
          {loc.label === 'gps' && <option value="">{t('search.myLocation')}</option>}
          {saved.data?.map((s) => <option key={`saved-${s.id}`} value={`saved:${s.id}`}>{s.label}</option>)}
          {CITIES.map((c) => <option key={c.key} value={c.key}>{t(`search.city.${c.key}`)}</option>)}
        </SelectField>
        <Button variant="secondary" onClick={useGps} loading={locating}><LocateFixed className="h-4 w-4" aria-hidden="true" />{t('business.useMyLocation')}</Button>
        <SelectField label={t('business.category')} className="w-48" value={categoryId ?? ''} onChange={(e) => update({ category: e.target.value })}>
          <option value="">{t('admin.all')}</option>
          {categories.data?.map((c) => <option key={c.id} value={c.id}>{pick(c.name)}</option>)}
        </SelectField>
        <SelectField label={t('search.radius')} className="w-32" value={radiusKm} onChange={(e) => update({ radius: e.target.value })}>
          {[2, 5, 10, 25, 50].map((r) => <option key={r} value={r}>{t('search.km', { count: r })}</option>)}
        </SelectField>
        <SelectField label={t('search.sort')} className="w-40" value={sort} onChange={(e) => update({ sort: e.target.value })}>
          <option value="DISTANCE">{t('search.sortBy.DISTANCE')}</option>
          <option value="RATING">{t('search.sortBy.RATING')}</option>
          <option value="PRICE">{t('search.sortBy.PRICE')}</option>
        </SelectField>
      </div>

      {!categoryId && !q && categories.data && (
        <div className="flex flex-wrap gap-2">
          {categories.data.map((c) => (
            <button key={c.id} type="button" onClick={() => update({ category: String(c.id) })}
              className="inline-flex items-center gap-2 rounded-full border border-border px-3 py-1.5 text-sm hover:border-brand-400">
              <CategoryIcon name={c.icon} className="h-4 w-4 text-brand-600" />{pick(c.name)}
            </button>
          ))}
        </div>
      )}

      {compare.length >= 2 && (
        <div className="sticky top-20 z-10 flex flex-wrap items-center justify-between gap-2 rounded-xl border border-brand-300 bg-brand-50 px-4 py-2 text-sm">
          <span>{t('extras.comparing', { count: compare.length })}</span>
          <Link to={`/compare?slugs=${compare.join(',')}`} className="font-semibold text-brand-700 hover:underline">{t('extras.compareNow')}</Link>
        </div>
      )}
      {query.isLoading && <LoadingState />}
      {query.error && <ErrorState error={query.error} onRetry={() => void query.refetch()} />}
      {query.data?.items.length === 0 && <EmptyState title={t('search.noResults')}>{t('search.noResultsHint')}</EmptyState>}
      <ul className="grid gap-4 md:grid-cols-2">
        {query.data?.items.map((h) => (
          <li key={h.branchId}>
            <Card className="flex h-full gap-4 p-5">
              {h.logoUrl ? <img src={fileUrl(h.logoUrl)} alt="" className="h-14 w-14 rounded-xl object-cover" />
                : <span className="flex h-14 w-14 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-xl font-bold text-brand-700">{h.name.charAt(0)}</span>}
              <div className="min-w-0 flex-1">
                <Link to={`/b/${h.slug}`} className="font-semibold hover:text-brand-700">{h.name}</Link>
                <p className="text-sm text-fg-muted">{pick(h.subcategoryName)} · {h.branchName}</p>
                <p className="mt-1 flex flex-wrap items-center gap-x-3 gap-y-1 text-sm">
                  <span className="inline-flex items-center gap-1"><MapPin className="h-3.5 w-3.5 text-brand-600" aria-hidden="true" />
                    {t('search.distance', { km: h.distanceKm })} · {[h.locality, h.city].filter(Boolean).join(', ')}</span>
                  {h.reviewCount > 0 && <span className="inline-flex items-center gap-1"><Star className="h-3.5 w-3.5 fill-amber-400 text-amber-400" aria-hidden="true" />{Number(h.ratingAvg).toFixed(1)} ({h.reviewCount})</span>}
                  {h.fromPrice != null && <span>{t('search.from', { price: formatINR(h.fromPrice) })}</span>}
                </p>
                <span className="mt-1 flex flex-wrap items-center gap-3 text-xs">
                  <a href={mapsLink(h.latitude, h.longitude)} target="_blank" rel="noopener noreferrer" className="text-brand-700 hover:underline">
                    {t('search.openMap')}
                  </a>
                  <label className="inline-flex items-center gap-1">
                    <input type="checkbox" checked={compare.includes(h.slug)} onChange={() => setCompare(compareList.toggle(h.slug))} />
                    {t('extras.compare')}
                  </label>
                </span>
              </div>
            </Card>
          </li>
        ))}
      </ul>
    </div>
  );
}
