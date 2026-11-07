import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useTranslation } from 'react-i18next';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import QRCode from 'qrcode';
import { ArrowLeft } from 'lucide-react';
import { Alert } from '@/components/ui/Alert';
import { Button, buttonClasses } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { TextAreaField } from '@/components/ui/FormFields';
import { ErrorState, LoadingState } from '@/components/ui/StateMessage';
import { BookingStatusBadge } from '@/features/booking/BookingStatusBadge';
import { SlotPicker } from '@/features/booking/SlotPicker';
import { ReviewForm } from '@/features/engagement/ReviewWidgets';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import { ACTIVE, bookingApi, isoDateIST, isVenueBooking, type Booking } from '@/services/bookingApi';
import { formatDateTime, formatINR } from '@/utils/format';

/** Link a phone camera opens to show the booking pass; staff can also paste it at check-in. */
export function passLink(payload: string): string {
  return `${window.location.origin}/pass/${encodeURIComponent(payload)}`;
}

function QrPass({ payload }: { payload: string }) {
  const { t } = useTranslation();
  const [src, setSrc] = useState<string>();
  useEffect(() => {
    QRCode.toDataURL(passLink(payload), { margin: 1, width: 220 }).then(setSrc).catch(() => setSrc(undefined));
  }, [payload]);
  return src ? <img src={src} alt={t('booking.qrAlt')} className="h-48 w-48 rounded-xl bg-white p-2" /> : null;
}

function CancelPanel({ b, onDone }: { b: Booking; onDone: (n: Booking) => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [reason, setReason] = useState('');
  const preview = useQuery({ queryKey: ['cancel-preview', b.id], queryFn: () => bookingApi.cancelPreview(b.id) });
  if (!preview.data) return <LoadingState />;
  if (!preview.data.allowed) return <p className="text-sm text-fg-muted">{t('booking.cannotCancel')}</p>;
  return (
    <div className="space-y-3">
      <Alert>{t('booking.refundPreview', { amount: formatINR(preview.data.refundAmount), percent: preview.data.refundPercent })}</Alert>
      <TextAreaField label={t('business.holidayReason')} rows={2} maxLength={500} value={reason} onChange={(e) => setReason(e.target.value)} />
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <Button variant="danger" loading={form.submitting}
        onClick={() => void form.submit(() => bookingApi.cancel(b.id, reason.trim() || undefined)).then((n) => n && onDone(n))}>
        {t('booking.confirmCancel')}
      </Button>
    </div>
  );
}

function ReschedulePanel({ b, onDone }: { b: Booking; onDone: (n: Booking) => void }) {
  const { t } = useTranslation();
  const form = useApiForm();
  const [date, setDate] = useState(isoDateIST(new Date(b.startsAt)));
  const [startsAt, setStartsAt] = useState<string>();
  return (
    <div className="space-y-3">
      <SlotPicker branchId={b.branchId} serviceId={b.serviceId} staffId={b.staffId} date={date}
        onDate={(d) => { setDate(d); setStartsAt(undefined); }} value={startsAt} onPick={setStartsAt} />
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      <Button disabled={!startsAt} loading={form.submitting}
        onClick={() => void form.submit(() => bookingApi.reschedule(b.id, startsAt!, b.staffId)).then((n) => n && onDone(n))}>
        {t('booking.confirmReschedule')}
      </Button>
    </div>
  );
}

export function BookingDetailPage() {
  const { t, i18n } = useTranslation();
  const id = Number(useParams().id);
  const justBooked = (useLocation().state as { justBooked?: boolean } | null)?.justBooked;
  const qc = useQueryClient();
  const [panel, setPanel] = useState<'cancel' | 'reschedule' | null>(null);
  const { data: b, error, isLoading, refetch } = useQuery({ queryKey: ['booking', id], queryFn: () => bookingApi.get(id) });
  if (isLoading) return <LoadingState />;
  if (error || !b) return <ErrorState error={error} onRetry={() => void refetch()} />;
  const locale = intlLocale(i18n.language);
  const active = ACTIVE.includes(b.status);
  const update = (n: Booking) => {
    qc.setQueryData(['booking', id], n);
    void qc.invalidateQueries({ queryKey: ['my-bookings'] });
    setPanel(null);
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6 px-4 py-10 sm:px-6">
      <Link to="/customer/bookings" className="inline-flex items-center gap-1 text-sm text-fg-muted hover:text-fg">
        <ArrowLeft className="h-4 w-4" aria-hidden="true" />{t('booking.myBookings')}
      </Link>
      {justBooked && <Alert tone="success">{t(b.status === 'PENDING_APPROVAL' ? 'booking.requested' : 'booking.booked')}</Alert>}
      <div className="flex flex-wrap items-center gap-3">
        <h1 className="text-2xl font-bold tracking-tight">{b.serviceName}</h1>
        <BookingStatusBadge status={b.status} />
      </div>
      <Card className="grid gap-6 p-6 sm:grid-cols-[1fr_auto]">
        <dl className="space-y-2 text-sm">
          <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('booking.when')}</dt><dd className="font-medium">{formatDateTime(b.startsAt, locale)}</dd></div>
          <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('booking.where')}</dt>
            <dd><Link to={`/b/${b.organizationSlug}`} className="font-medium text-brand-700 hover:underline">{b.organizationName}</Link> · {b.branchName}<br />{b.branchAddress}</dd></div>
          {b.screenName && <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('venue.screen')}</dt><dd>{b.screenName}</dd></div>}
          {Boolean(b.seats?.length) && <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('venue.seats')}</dt>
            <dd className="flex flex-wrap gap-1">{b.seats!.map((s) => <span key={s} className="rounded-md bg-brand-50 px-2 py-0.5 font-medium text-brand-700">{s}</span>)}</dd></div>}
          {b.partySize != null && <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('venue.guests')}</dt><dd>{b.partySize}</dd></div>}
          {b.patientName && <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('extras.forWhom')}</dt><dd>{b.patientName}</dd></div>}
          {b.staffName && <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('booking.with')}</dt><dd>{b.staffName}</dd></div>}
          <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('booking.reference')}</dt><dd className="font-mono">{b.reference}</dd></div>
          <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('home.exampleTotal')}</dt>
            <dd>{formatINR(b.totalAmount)} <span className="text-fg-muted">({formatINR(b.servicePrice)} + {formatINR(b.platformFee)})</span></dd></div>
          {b.refundAmount != null && <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('booking.refund')}</dt><dd>{formatINR(b.refundAmount)}</dd></div>}
          {b.cancelReason && <div><dt className="text-xs uppercase tracking-wide text-fg-muted">{t('booking.reason')}</dt><dd>{b.cancelReason}</dd></div>}
        </dl>
        {b.qrPayload && (
          <div className="text-center">
            <QrPass payload={b.qrPayload} />
            <p className="mt-2 text-xs text-fg-muted">{t('booking.showAtVenue')}</p>
          </div>
        )}
      </Card>
      {b.status === 'COMPLETED' && !b.reviewed && (
        <Card className="p-6"><ReviewForm appointmentId={b.id} onDone={() => void refetch()} /></Card>
      )}
      {b.status === 'PENDING_PAYMENT' && (
        <Card className="flex flex-wrap items-center justify-between gap-3 p-6">
          <p className="text-sm">{t('pay.unpaid')}</p>
          <Link to={`/customer/bookings/${b.id}/pay`} className={buttonClasses('primary')}>{t('pay.continue')}</Link>
        </Card>
      )}
      {b.paid && (
        <Link to={`/customer/bookings/${b.id}/receipt`} className="inline-block text-sm text-brand-700 hover:underline">{t('payment.viewReceipt')}</Link>
      )}
      {active && (
        <Card className="space-y-4 p-6">
          <div className="flex flex-wrap gap-2">
            {!isVenueBooking(b) && <Button variant="secondary" onClick={() => setPanel(panel === 'reschedule' ? null : 'reschedule')}>{t('booking.reschedule')}</Button>}
            <Button variant="ghost" onClick={() => setPanel(panel === 'cancel' ? null : 'cancel')}>{t('booking.cancel')}</Button>
          </div>
          {panel === 'cancel' && <CancelPanel b={b} onDone={update} />}
          {panel === 'reschedule' && <ReschedulePanel b={b} onDone={update} />}
        </Card>
      )}
    </div>
  );
}
