import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { CalendarClock, CreditCard, Landmark, Lock, MapPin, ShieldCheck, Smartphone, Timer, Wallet, type LucideIcon } from 'lucide-react';
import clsx from 'clsx';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import { bookingApi } from '@/services/bookingApi';
import { payWithRazorpay, paymentApi, type PaymentMethod } from '@/services/paymentApi';
import { formatDateTime, formatINR } from '@/utils/format';

const METHODS: { id: PaymentMethod; icon: LucideIcon }[] = [
  { id: 'upi', icon: Smartphone },
  { id: 'card', icon: CreditCard },
  { id: 'netbanking', icon: Landmark },
  { id: 'wallet', icon: Wallet },
];

function useCountdown(until?: string) {
  const [left, setLeft] = useState<number | null>(null);
  useEffect(() => {
    if (!until) return;
    const tick = () => setLeft(Math.max(0, Math.floor((new Date(until).getTime() - Date.now()) / 1000)));
    tick();
    const id = window.setInterval(tick, 1000);
    return () => window.clearInterval(id);
  }, [until]);
  return left;
}

/** Checkout for a booking that is holding its slot: pick a payment method, see the bill, pay. */
export function PaymentPage() {
  const { t, i18n } = useTranslation();
  const { id } = useParams();
  const bookingId = Number(id);
  const navigate = useNavigate();
  const form = useApiForm();
  const [method, setMethod] = useState<PaymentMethod>('upi');
  const [processing, setProcessing] = useState(false);

  const booking = useQuery({ queryKey: ['booking', bookingId], queryFn: () => bookingApi.get(bookingId) });
  const pending = booking.data?.status === 'PENDING_PAYMENT';
  const order = useQuery({
    queryKey: ['payment-order', bookingId],
    queryFn: () => paymentApi.order(bookingId),
    enabled: pending,
    retry: false,
    staleTime: Infinity,
  });
  const left = useCountdown(order.data?.expiresAt);

  if (booking.isLoading) return <LoadingState />;
  if (booking.error) return <ErrorState error={booking.error} onRetry={() => void booking.refetch()} />;
  const b = booking.data!;
  if (!pending) return <Navigate to={`/customer/bookings/${b.id}`} replace />;

  const expired = left === 0;
  const locale = intlLocale(i18n.language);
  const done = () => navigate(`/customer/bookings/${b.id}`, { replace: true, state: { justBooked: true } });

  const pay = async () => {
    const o = order.data;
    if (!o) return;
    if (o.demo) {
      setProcessing(true);
      await new Promise((r) => window.setTimeout(r, 900)); // feel of a gateway round-trip
      const ok = await form.submit(() => paymentApi.demoPay(b.id, method).then(() => true));
      setProcessing(false);
      if (ok) done();
      return;
    }
    try {
      const res = await payWithRazorpay(o, method);
      if (!res) return; // closed the checkout window
      const ok = await form.submit(() => paymentApi.verify({
        orderId: res.razorpay_order_id, paymentId: res.razorpay_payment_id, signature: res.razorpay_signature,
      }).then(() => true));
      if (ok) done();
    } catch {
      form.setFormError(t('payment.checkoutUnavailable'));
    }
  };

  return (
    <div className="mx-auto max-w-5xl px-4 py-10 sm:px-6">
      <Link to={`/customer/bookings/${b.id}`} className="text-sm text-fg-muted hover:text-fg">← {t('pay.back')}</Link>
      <h1 className="mt-2 text-3xl font-bold tracking-tight">{t('pay.title')}</h1>
      <p className="mt-1 text-fg-muted">{t('pay.subtitle')}</p>

      <div className="mt-6 grid gap-6 lg:grid-cols-[1fr_22rem]">
        <Card className="space-y-5 p-6">
          {order.data?.demo && <Alert>{t('pay.demoBanner')}</Alert>}
          {order.error && <ErrorState error={order.error} onRetry={() => void order.refetch()} />}
          {expired && <Alert tone="error">{t('pay.expired')}</Alert>}
          {form.formError && <Alert tone="error">{form.formError}</Alert>}

          <fieldset>
            <legend className="mb-3 font-semibold">{t('pay.chooseMethod')}</legend>
            <div role="radiogroup" className="grid gap-3 sm:grid-cols-2">
              {METHODS.map(({ id: m, icon: Icon }) => (
                <label key={m}
                  className={clsx('flex cursor-pointer items-start gap-3 rounded-xl border p-4 transition',
                    method === m ? 'border-brand-600 bg-brand-50 ring-1 ring-brand-600' : 'border-border hover:border-brand-300')}>
                  <input type="radio" name="method" value={m} checked={method === m} onChange={() => setMethod(m)} className="sr-only" />
                  <span className={clsx('flex h-10 w-10 shrink-0 items-center justify-center rounded-lg',
                    method === m ? 'bg-brand-600 text-white' : 'bg-surface-muted text-fg-muted')}>
                    <Icon className="h-5 w-5" aria-hidden="true" />
                  </span>
                  <span>
                    <span className="block font-medium">{t(`pay.method.${m}`)}</span>
                    <span className="block text-sm text-fg-muted">{t(`pay.methodHint.${m}`)}</span>
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          <p className="flex items-start gap-2 text-sm text-fg-muted">
            <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-success" aria-hidden="true" />
            {order.data?.demo ? t('pay.demoSecurity') : t('pay.security')}
          </p>

          <Button size="lg" className="w-full" onClick={() => void pay()}
            loading={processing || form.submitting} disabled={!order.data || expired}>
            <Lock className="h-4 w-4" aria-hidden="true" />
            {processing ? t('pay.processing') : t('payment.payNow', { amount: formatINR(b.totalAmount) })}
          </Button>
        </Card>

        <Card className="h-fit space-y-4 p-6">
          <h2 className="font-semibold">{t('pay.summary')}</h2>
          <div className="space-y-1 text-sm">
            <p className="font-medium">{b.organizationName}</p>
            <p className="text-fg-muted">{b.serviceName}{b.staffName ? ` · ${b.staffName}` : ''}</p>
            <p className="flex items-center gap-1.5 text-fg-muted"><CalendarClock className="h-4 w-4" aria-hidden="true" />
              {formatDateTime(b.startsAt, locale)}</p>
            <p className="flex items-center gap-1.5 text-fg-muted"><MapPin className="h-4 w-4" aria-hidden="true" />{b.branchName}</p>
          </div>
          <dl className="space-y-2 border-t border-border pt-4 text-sm">
            <div className="flex justify-between"><dt className="text-fg-muted">{t('home.exampleService')}</dt><dd>{formatINR(b.servicePrice)}</dd></div>
            <div className="flex justify-between"><dt className="text-fg-muted">{t('payment.platformFee')}</dt><dd>{formatINR(b.platformFee)}</dd></div>
            <div className="flex justify-between border-t border-border pt-2 text-base font-semibold">
              <dt>{t('pay.total')}</dt><dd className="text-brand-700">{formatINR(b.totalAmount)}</dd></div>
          </dl>
          {left !== null && !expired && (
            <p className="flex items-center gap-2 rounded-lg bg-warning/10 px-3 py-2 text-sm font-medium text-warning" role="timer">
              <Timer className="h-4 w-4" aria-hidden="true" />
              {t('pay.holdLeft', { time: `${Math.floor(left / 60)}:${String(left % 60).padStart(2, '0')}` })}
            </p>
          )}
          <p className="text-xs text-fg-muted">{t('booking.reference')}: <span className="font-mono">{b.reference}</span></p>
        </Card>
      </div>
    </div>
  );
}
