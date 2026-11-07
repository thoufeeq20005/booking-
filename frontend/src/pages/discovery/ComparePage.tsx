import { Link, useSearchParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQueries } from '@tanstack/react-query';
import { Card } from '@/components/ui/Card';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { useLocalized } from '@/features/organization/shared';
import { discoveryApi } from '@/services/discoveryApi';
import { formatINR } from '@/utils/format';

/** Up to three businesses side by side. */
export function ComparePage() {
  const { t } = useTranslation();
  const pick = useLocalized();
  const slugs = (useSearchParams()[0].get('slugs') ?? '').split(',').filter(Boolean).slice(0, 3);
  const results = useQueries({ queries: slugs.map((s) => ({ queryKey: ['public-org', s], queryFn: () => discoveryApi.profile(s) })) });
  if (slugs.length < 2) return <div className="mx-auto max-w-4xl px-4 py-10"><EmptyState title={t('extras.compareNeedTwo')} /></div>;
  if (results.some((r) => r.isLoading)) return <LoadingState />;
  const profiles = results.flatMap((r) => (r.data ? [r.data] : []));
  const rows: { label: string; value: (p: (typeof profiles)[number]) => string }[] = [
    { label: t('business.category'), value: (p) => pick(p.subcategoryName) },
    { label: t('review.rating'), value: (p) => (p.reviewCount > 0 ? `${Number(p.ratingAvg).toFixed(1)} (${p.reviewCount})` : '—') },
    { label: t('extras.priceRange'), value: (p) => {
      const prices = p.services.flatMap((s) => Object.values(s.branchPrices).map(Number));
      return prices.length ? `${formatINR(Math.min(...prices))} – ${formatINR(Math.max(...prices))}` : '—';
    } },
    { label: t('business.tab.services'), value: (p) => String(p.services.length) },
    { label: t('biz.team'), value: (p) => String(p.staff.length) },
    { label: t('team.branches'), value: (p) => p.branches.map((b) => b.city).join(', ') },
    { label: t('business.confirmationMode'), value: (p) => t(`business.mode.${p.confirmationMode}`) },
  ];
  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <h1 className="text-3xl font-bold tracking-tight">{t('extras.compare')}</h1>
      <Card className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-border">
              <th className="px-4 py-3" />
              {profiles.map((p) => <th key={p.slug} className="px-4 py-3"><Link to={`/b/${p.slug}`} className="font-semibold text-brand-700 hover:underline">{p.name}</Link></th>)}
            </tr>
          </thead>
          <tbody className="divide-y divide-border">
            {rows.map((r) => (
              <tr key={r.label}>
                <th scope="row" className="px-4 py-3 font-medium text-fg-muted">{r.label}</th>
                {profiles.map((p) => <td key={p.slug} className="px-4 py-3">{r.value(p)}</td>)}
              </tr>
            ))}
          </tbody>
        </table>
      </Card>
    </div>
  );
}
