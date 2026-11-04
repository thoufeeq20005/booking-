import { Link, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import { ArrowLeft, Printer } from 'lucide-react';
import { Logo } from '@/components/brand/Logo';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { intlLocale } from '@/i18n';
import { paymentApi } from '@/services/paymentApi';
import { formatDateTime, formatINR } from '@/utils/format';

export function ReceiptPage() {
  const { t, i18n } = useTranslation();
  const id = Number(useParams().id);
  const { data: r, error, isLoading } = useQuery({ queryKey: ['receipt', id], queryFn: () => paymentApi.receipt(id) });
  if (isLoading) return <LoadingState />;
  if (error || !r) return <ErrorState error={error} />;
  const locale = intlLocale(i18n.language);
  const row = (label: string, value: string) => (
    <div className="flex justify-between gap-4 py-1.5"><dt className="text-fg-muted">{label}</dt><dd className="text-right">{value}</dd></div>
  );

  return (
    <div className="mx-auto max-w-2xl space-y-4 px-4 py-10 sm:px-6">
      <div className="flex items-center justify-between print:hidden">
        <Link to={`/customer/bookings/${id}`} className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
          <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('booking.myBookings')}
        </Link>
        <Button variant="secondary" size="sm" onClick={() => window.print()}><Printer className="h-4 w-4" aria-hidden="true" />{t('payment.print')}</Button>
      </div>
      <Card className="p-8 print:border-0 print:shadow-none">
        <div className="flex items-start justify-between">
          <Logo />
          <div className="text-right text-sm">
            <p className="font-semibold">{t('payment.receipt')}</p>
            <p className="font-mono">{r.receiptNumber}</p>
            <p className="text-fg-muted">{formatDateTime(r.paidAt, locale)}</p>
          </div>
        </div>
        <dl className="mt-8 divide-y divide-border text-sm">
          {row(t('payment.billedTo'), r.customerName)}
          {row(t('admin.business'), r.businessName)}
          {row(t('team.service'), r.serviceName)}
          {row(t('booking.when'), formatDateTime(r.appointmentAt, locale))}
          {row(t('booking.reference'), r.reference)}
          {row(t('home.exampleService'), formatINR(r.servicePrice))}
          {row(t('payment.platformFee'), formatINR(r.platformFee))}
          <div className="flex justify-between py-2 font-semibold"><dt>{t('payment.totalPaid')}</dt><dd>{formatINR(r.total)}</dd></div>
          {row(t('payment.paymentId'), `${r.paymentId}${r.method ? ` · ${r.method.toUpperCase()}` : ''}`)}
          {r.refunded != null && row(t('booking.refund'), `${formatINR(r.refunded)} · ${t(`payment.refundStatus.${r.refundStatus}`)}`)}
        </dl>
        <p className="mt-6 text-xs text-fg-muted">{t('footer.currencyNote')}</p>
      </Card>
    </div>
  );
}
