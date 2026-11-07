import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { BadgePercent, CalendarDays, Gift, Package, Plus, Trash2 } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField } from '@/components/ui/FormFields';
import { LoadingState } from '@/components/ui/StateMessage';
import { TextField } from '@/components/ui/TextField';
import { useApiForm } from '@/hooks/useApiForm';
import { formatDate } from '@/utils/format';
import { intlLocale } from '@/i18n';
import { recommendApi, type Promotion, type PromotionKind } from '@/services/recommendApi';

const ICON = { FREE: Gift, PACKAGE: Package, EVENT: CalendarDays, DISCOUNT: BadgePercent } as const;

function PromotionLine({ p }: { p: Promotion }) {
  const { t, i18n } = useTranslation();
  const Icon = ICON[p.kind];
  return (
    <div className="flex items-start gap-3">
      <span className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-700"><Icon className="h-5 w-5" aria-hidden="true" /></span>
      <div className="min-w-0 text-sm">
        <p className="font-semibold">{p.title}{p.percentOff ? ` (${t('reco.percentOff', { percent: p.percentOff })})` : ''}</p>
        {p.details && <p className="text-fg-muted">{p.details}</p>}
        {p.validUntil && <p className="text-xs text-fg-muted">{t('promo.until', { date: formatDate(p.validUntil, intlLocale(i18n.language)) })}</p>}
      </div>
    </div>
  );
}

/** Current offers on a business page (nothing when there are none). */
export function BusinessOffers({ slug }: { slug: string }) {
  const { t } = useTranslation();
  const q = useQuery({ queryKey: ['promotions', slug], queryFn: () => recommendApi.forBusiness(slug) });
  if (!q.data?.length) return null;
  return (
    <Card className="border-brand-200 bg-brand-50/40 p-6">
      <h2 className="text-lg font-semibold">{t('promo.title')}</h2>
      <ul className="mt-3 space-y-3">{q.data.map((p) => <li key={p.id}><PromotionLine p={p} /></li>)}</ul>
    </Card>
  );
}

/** Owner's "Offers" tab: publish discounts, free check-ups or trials, packages and events. */
export function OffersSection({ orgId }: { orgId: number }) {
  const { t } = useTranslation();
  const qc = useQueryClient();
  const form = useApiForm();
  const key = ['owner-promotions', orgId];
  const list = useQuery({ queryKey: key, queryFn: () => recommendApi.ownerList(orgId) });
  const [v, setV] = useState({ title: '', details: '', percentOff: '', validFrom: '', validUntil: '' });
  const [kind, setKind] = useState<PromotionKind>('DISCOUNT');
  const set = (k: keyof typeof v) => (e: { target: { value: string } }) => setV((s) => ({ ...s, [k]: e.target.value }));
  const apply = (d?: Promotion[]) => d && qc.setQueryData(key, d);
  const add = async (e: FormEvent) => {
    e.preventDefault();
    const d = await form.submit(() => recommendApi.ownerAdd(orgId, {
      title: v.title.trim(), details: v.details.trim() || undefined, kind,
      percentOff: kind === 'DISCOUNT' && v.percentOff.trim() ? Number(v.percentOff) : undefined,
      validFrom: v.validFrom || undefined, validUntil: v.validUntil || undefined,
    }));
    if (d) { apply(d); setV({ title: '', details: '', percentOff: '', validFrom: '', validUntil: '' }); }
  };
  return (
    <Card className="p-6">
      <h2 className="text-lg font-semibold">{t('promo.title')}</h2>
      <p className="mt-1 text-sm text-fg-muted">{t('promo.hint')}</p>
      {list.isLoading && <LoadingState />}
      {list.data?.length === 0 && <p className="mt-3 text-sm text-fg-muted">{t('promo.none')}</p>}
      <ul className="mt-3 divide-y divide-border">
        {list.data?.map((p) => (
          <li key={p.id} className="flex items-start justify-between gap-3 py-3">
            <div className={p.current ? '' : 'opacity-60'}>
              <PromotionLine p={p} />
              {!p.current && <p className="ml-12 text-xs font-medium text-amber-700">{t('promo.notCurrent')}</p>}
            </div>
            <Button variant="ghost" size="sm" aria-label={t('promo.remove', { title: p.title })}
              onClick={() => void form.submit(() => recommendApi.ownerDelete(orgId, p.id)).then(apply)}><Trash2 className="h-4 w-4" aria-hidden="true" /></Button>
          </li>
        ))}
      </ul>
      <form onSubmit={add} className="mt-4 grid gap-3 border-t border-border pt-4 sm:grid-cols-2">
        <SelectField label={t('promo.kindLabel')} value={kind} onChange={(e) => setKind(e.target.value as PromotionKind)}>
          {(['DISCOUNT', 'FREE', 'PACKAGE', 'EVENT'] as const).map((k) => <option key={k} value={k}>{t(`promo.kind.${k}`)}</option>)}
        </SelectField>
        <TextField label={t('promo.titleLabel')} placeholder={t(`promo.example.${kind}`)} value={v.title} onChange={set('title')} maxLength={100} error={form.fieldErrors.title} />
        <TextField className="sm:col-span-2" label={t('promo.detailsLabel')} value={v.details} onChange={set('details')} maxLength={300} error={form.fieldErrors.details} />
        {kind === 'DISCOUNT' && <TextField label={t('promo.percentLabel')} inputMode="numeric" value={v.percentOff} onChange={set('percentOff')} error={form.fieldErrors.percentOff} />}
        <TextField label={t('promo.fromLabel')} type="date" value={v.validFrom} onChange={set('validFrom')} error={form.fieldErrors.validFrom} />
        <TextField label={t('promo.untilLabel')} type="date" value={v.validUntil} onChange={set('validUntil')} error={form.fieldErrors.validUntil} />
        {form.formError && <Alert tone="error" className="sm:col-span-2">{form.formError}</Alert>}
        <div className="sm:col-span-2"><Button type="submit" loading={form.submitting}><Plus className="h-4 w-4" aria-hidden="true" />{t('promo.add')}</Button></div>
      </form>
    </Card>
  );
}
