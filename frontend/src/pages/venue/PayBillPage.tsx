import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, BadgePercent, ReceiptIndianRupee } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useApiForm } from '@/hooks/useApiForm';
import { fileUrl } from '@/features/organization/shared';
import { discoveryApi } from '@/services/discoveryApi';
import { toApiError } from '@/services/apiClient';
import { venueApi } from '@/services/venueApi';
import { formatINR } from '@/utils/format';

/** Pay the restaurant bill: the offer and the cover charge already paid are taken off; BookEasy adds no fee. */
export function PayBillPage() {
  const { t } = useTranslation();
  const slug = useParams().slug ?? '';
  const navigate = useNavigate();
  const form = useApiForm();
  const profile = useQuery({ queryKey: ['public-org', slug], queryFn: () => discoveryApi.profile(slug) });
  const branchId = profile.data?.branches[0]?.id;
  const [text, setText] = useState('');
  const [amount, setAmount] = useState<number>();
  useEffect(() => {
    const n = Number(text.replace(/,/g, ''));
    const id = setTimeout(() => setAmount(text.trim() && Number.isFinite(n) && n > 0 ? n : undefined), 350);
    return () => clearTimeout(id);
  }, [text]);
  const quote = useQuery({
    queryKey: ['bill-quote', branchId, amount],
    queryFn: () => venueApi.billQuote(branchId!, amount!),
    enabled: branchId != null && amount != null,
    retry: false,
  });
  if (profile.isLoading) return <LoadingState />;
  if (profile.error || !profile.data || branchId == null) return <ErrorState error={profile.error} onRetry={() => void profile.refetch()} />;
  const p = profile.data;
  const q = quote.data;

  const pay = async () => {
    if (!amount) return;
    const b = await form.submit(() => venueApi.payBill({ branchId, amount, bookingId: q?.bookingId }));
    if (!b) return;
    if (b.status === 'PENDING_PAYMENT') navigate(`/customer/bookings/${b.id}/pay`);
    else navigate(`/customer/bookings/${b.id}`, { state: { justBooked: true } });
  };

  return (
    <div className="mx-auto max-w-lg space-y-5 px-4 py-8 sm:px-6">
      <Link to={`/b/${p.slug}`} className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg"><ArrowLeft className="h-4 w-4" aria-hidden="true" />{p.name}</Link>
      <div className="flex items-center gap-3">
        {p.coverUrl && <img src={fileUrl(p.coverUrl)} alt="" className="h-14 w-14 rounded-xl object-cover" />}
        <div>
          <h1 className="text-2xl font-bold tracking-tight">{t('venue.payBill')}</h1>
          <p className="text-sm text-fg-muted">{p.name} · {p.branches[0]?.locality ?? p.branches[0]?.city}</p>
        </div>
      </div>

      <Card className="space-y-4 p-6">
        <label className="block">
          <span className="text-sm font-medium">{t('venue.enterBill')}</span>
          <span className="mt-2 flex items-center gap-2 border-b-2 border-brand-500 pb-1">
            <span className="text-3xl font-bold text-fg-muted">₹</span>
            <input inputMode="decimal" autoFocus value={text} onChange={(e) => setText(e.target.value.replace(/[^\d.,]/g, ''))}
              aria-label={t('venue.enterBill')} placeholder="0" className="w-full bg-transparent text-4xl font-bold outline-none" />
          </span>
        </label>
        {quote.isFetching && !q && <LoadingState />}
        {quote.error && <Alert tone="error">{t(`errors.${toApiError(quote.error).code}`, { defaultValue: t('errors.INTERNAL_ERROR') })}</Alert>}
        {q && amount != null && (
          <dl className="space-y-2 text-sm">
            <div className="flex justify-between"><dt>{t('venue.billAmount')}</dt><dd>{formatINR(q.billAmount)}</dd></div>
            <div className="flex justify-between text-emerald-700">
              <dt className="flex items-center gap-1"><BadgePercent className="h-4 w-4" aria-hidden="true" />{q.offerTitle ?? t('venue.noOffer')}</dt>
              <dd>− {formatINR(q.discount)}</dd>
            </div>
            {Number(q.coverCredit) > 0 && (
              <div className="flex justify-between text-emerald-700"><dt>{t('venue.coverCredit')}</dt><dd>− {formatINR(q.coverCredit)}</dd></div>
            )}
            <div className="flex justify-between text-fg-muted"><dt>{t('venue.bookEasyFee')}</dt><dd>{formatINR(0)}</dd></div>
            <div className="flex justify-between border-t border-border pt-2 text-lg font-bold"><dt>{t('venue.toPay')}</dt><dd>{formatINR(q.payable)}</dd></div>
            <p className="text-xs text-fg-muted">{q.bookingId ? t('venue.usingBooking') : t('venue.walkInNote')}</p>
          </dl>
        )}
        {form.formError && <Alert tone="error">{form.formError}</Alert>}
        <Button size="lg" className="w-full" disabled={!q || amount == null} loading={form.submitting} onClick={() => void pay()}>
          <ReceiptIndianRupee className="h-5 w-5" aria-hidden="true" />
          {q ? t('venue.payAmount', { amount: formatINR(q.payable) }) : t('venue.payBill')}
        </Button>
        <p className="text-xs text-fg-muted">{t('venue.payBillHint')}</p>
      </Card>
    </div>
  );
}
