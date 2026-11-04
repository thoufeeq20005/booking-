import { useTranslation } from 'react-i18next';
import { CreditCard } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import type { Booking } from '@/services/bookingApi';
import { payWithRazorpay, paymentApi } from '@/services/paymentApi';
import { formatDateTime, formatINR } from '@/utils/format';

/** Pay for a booking that is holding its slot. The server confirms only after verifying Razorpay's signature. */
export function PayNow({ booking, onPaid }: { booking: Booking; onPaid: () => void }) {
  const { t, i18n } = useTranslation();
  const form = useApiForm();

  const pay = async () => {
    const order = await form.submit(() => paymentApi.order(booking.id));
    if (!order) return;
    try {
      const res = await payWithRazorpay(order);
      if (!res) return;
      const ok = await form.submit(() => paymentApi.verify({
        orderId: res.razorpay_order_id, paymentId: res.razorpay_payment_id, signature: res.razorpay_signature,
      }).then(() => true));
      if (ok) onPaid();
    } catch {
      form.setFormError(t('payment.checkoutUnavailable'));
    }
  };

  const holdUntil = new Date(new Date(booking.createdAt).getTime() + 15 * 60000).toISOString();
  return (
    <div className="space-y-3">
      <Alert>{t('payment.holdNotice', { time: formatDateTime(holdUntil, intlLocale(i18n.language)) })}</Alert>
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <Button onClick={() => void pay()} loading={form.submitting}>
        <CreditCard className="h-4 w-4" aria-hidden="true" />{t('payment.payNow', { amount: formatINR(booking.totalAmount) })}
      </Button>
    </div>
  );
}
