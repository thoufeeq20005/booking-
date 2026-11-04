import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { Card } from '@/components/ui/Card';
import { intlLocale } from '@/i18n';
import { paymentApi } from '@/services/paymentApi';
import { formatDateTime, formatINR } from '@/utils/format';

/** What the business is owed and what has been paid out. */
export function EarningsCard({ orgId }: { orgId: number }) {
  const { t, i18n } = useTranslation();
  const { data } = useQuery({ queryKey: ['earnings', orgId], queryFn: () => paymentApi.earnings(orgId) });
  if (!data) return null;
  return (
    <Card className="space-y-3 p-5">
      <h2 className="text-lg font-semibold">{t('payment.earnings')}</h2>
      <dl className="grid grid-cols-2 gap-3 text-sm">
        <div><dt className="text-fg-muted">{t('payment.pendingPayout')}</dt><dd className="text-xl font-bold tabular-nums">{formatINR(data.pendingPayoutPaise / 100)}</dd></div>
        <div><dt className="text-fg-muted">{t('payment.paidOut')}</dt><dd className="text-xl font-bold tabular-nums">{formatINR(data.settledPaise / 100)}</dd></div>
      </dl>
      {data.settlements.length > 0 && (
        <ul className="divide-y divide-border text-sm">
          {data.settlements.slice(0, 5).map((s) => (
            <li key={s.id} className="flex justify-between py-2">
              <span>{formatDateTime(s.createdAt, intlLocale(i18n.language))}</span>
              <span>{formatINR(s.amountPaise / 100)} · {t(`payment.settlementStatus.${s.status}`)}</span>
            </li>
          ))}
        </ul>
      )}
    </Card>
  );
}
