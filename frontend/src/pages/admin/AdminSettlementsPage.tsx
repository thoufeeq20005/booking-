import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { SelectField } from '@/components/ui/FormFields';
import { EmptyState, LoadingState } from '@/components/ui/StateMessage';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import { paymentApi } from '@/services/paymentApi';
import { formatDateTime, formatINR } from '@/utils/format';

export function AdminSettlementsPage() {
  const { t, i18n } = useTranslation();
  const qc = useQueryClient();
  const form = useApiForm();
  const [status, setStatus] = useState('PENDING_MANUAL');
  const [refs, setRefs] = useState<Record<number, string>>({});
  const q = useQuery({ queryKey: ['settlements', status], queryFn: () => paymentApi.settlements(status || undefined) });
  const refresh = () => qc.invalidateQueries({ queryKey: ['settlements'] });
  const locale = intlLocale(i18n.language);

  return (
    <div className="mx-auto max-w-6xl space-y-6 px-4 py-10 sm:px-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <h1 className="text-3xl font-bold tracking-tight">{t('payment.settlements')}</h1>
        <Button variant="secondary" loading={form.submitting} onClick={() => void form.submit(paymentApi.runSettlement).then(refresh)}>
          {t('payment.runSettlement')}
        </Button>
      </div>
      <p className="text-sm text-fg-muted">{t('payment.settlementsIntro')}</p>
      <SelectField label={t('admin.status')} value={status} onChange={(e) => setStatus(e.target.value)} className="w-56">
        <option value="">{t('admin.all')}</option>
        {['PENDING_MANUAL', 'TRANSFERRED', 'PAID'].map((s) => <option key={s} value={s}>{t(`payment.settlementStatus.${s}`)}</option>)}
      </SelectField>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {q.isLoading && <LoadingState />}
      {q.data?.length === 0 && <EmptyState title={t('payment.noSettlements')} />}
      <div className="space-y-3">
        {q.data?.map((s) => (
          <Card key={s.id} className="flex flex-wrap items-center gap-4 p-4">
            <div className="min-w-0 flex-1">
              <p className="font-semibold">{s.organizationName} · {formatINR(s.amountPaise / 100)}</p>
              <p className="text-xs text-fg-muted">{formatDateTime(s.createdAt, locale)} · {t(`payment.settlementStatus.${s.status}`)}
                {s.payoutReference ? ` · ${s.payoutReference}` : ''}{s.razorpayTransferId ? ` · ${s.razorpayTransferId}` : ''}</p>
            </div>
            {s.status === 'PENDING_MANUAL' && (
              <div className="flex gap-2">
                <input aria-label={t('payment.payoutReference')} placeholder={t('payment.payoutReference')} value={refs[s.id] ?? ''}
                  onChange={(e) => setRefs((r) => ({ ...r, [s.id]: e.target.value }))} className="h-9 w-48 rounded-lg border border-border bg-surface px-2 text-sm" />
                <Button size="sm" disabled={!refs[s.id]?.trim()}
                  onClick={() => void form.submit(() => paymentApi.markPaid(s.id, refs[s.id]!.trim())).then(refresh)}>
                  {t('payment.markPaid')}
                </Button>
              </div>
            )}
          </Card>
        ))}
      </div>
    </div>
  );
}
