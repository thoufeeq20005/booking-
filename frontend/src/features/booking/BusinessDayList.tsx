import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import { Alert } from '@/components/ui/Alert';
import { Button } from '@/components/ui/Button';
import { Card } from '@/components/ui/Card';
import { EmptyState } from '@/components/ui/StateMessage';
import { useApiForm } from '@/hooks/useApiForm';
import { intlLocale } from '@/i18n';
import type { Booking, BusinessAction } from '@/services/bookingApi';
import { formatINR } from '@/utils/format';
import { BookingStatusBadge } from './BookingStatusBadge';

/** A day's bookings with the actions that fit each one's status. Shared by owners and staff. */
export function BusinessDayList({ bookings, act }: {
  bookings: Booking[];
  act: (b: Booking, action: BusinessAction, reason?: string) => Promise<unknown>;
}) {
  const { t, i18n } = useTranslation();
  const form = useApiForm();
  const [asking, setAsking] = useState<{ id: number; action: BusinessAction } | null>(null);
  const [reason, setReason] = useState('');
  const time = (iso: string) => new Intl.DateTimeFormat(intlLocale(i18n.language), { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' }).format(new Date(iso));
  const started = (b: Booking) => new Date(b.startsAt).getTime() <= Date.now();

  if (bookings.length === 0) return <EmptyState title={t('booking.noneToday')} />;
  const run = async (b: Booking, action: BusinessAction, why?: string) => {
    const ok = await form.submit(() => act(b, action, why).then(() => true));
    if (ok) {
      setAsking(null);
      setReason('');
    }
  };

  return (
    <div className="space-y-3">
      {form.formError && <Alert tone="error">{form.formError}</Alert>}
      {bookings.map((b) => {
        const actions: BusinessAction[] = [];
        if (b.status === 'PENDING_APPROVAL') actions.push('confirm', 'decline');
        if (b.status === 'CONFIRMED') {
          if (!b.checkedInAt) actions.push('check-in');
          if (started(b)) actions.push('complete');
          if (started(b) && !b.checkedInAt) actions.push('no-show');
        }
        if (b.status === 'CONFIRMED' || b.status === 'PENDING_APPROVAL') actions.push('cancel');
        return (
          <Card key={b.id} className="space-y-3 p-4">
            <div className="flex flex-wrap items-center gap-3">
              <span className="w-20 font-semibold tabular-nums">{time(b.startsAt)}</span>
              <div className="min-w-0 flex-1">
                <p className="font-medium">{b.customerName} · {b.serviceName}</p>
                <p className="text-xs text-fg-muted">{[b.staffName, b.branchName, b.reference, formatINR(b.totalAmount)].filter(Boolean).join(' · ')}
                  {b.checkedInAt && ` · ${t('booking.checkedIn')}`}</p>
                {b.customerNote && <p className="text-xs text-fg-muted">“{b.customerNote}”</p>}
              </div>
              <BookingStatusBadge status={b.status} />
            </div>
            {actions.length > 0 && (
              <div className="flex flex-wrap gap-2">
                {actions.map((a) => (
                  <Button key={a} size="sm" variant={a === 'cancel' || a === 'decline' || a === 'no-show' ? 'ghost' : 'secondary'}
                    onClick={() => (a === 'cancel' || a === 'decline' ? setAsking({ id: b.id, action: a }) : void run(b, a))}>
                    {t(`booking.action.${a}`)}
                  </Button>
                ))}
              </div>
            )}
            {asking?.id === b.id && (
              <div className="flex flex-wrap items-end gap-2">
                <input aria-label={t('admin.reasonRequired')} placeholder={t('admin.reasonRequired')} value={reason}
                  onChange={(e) => setReason(e.target.value)} className="h-9 min-w-[16rem] flex-1 rounded-lg border border-border bg-surface px-2 text-sm" />
                <Button size="sm" variant="danger" disabled={!reason.trim()} onClick={() => void run(b, asking.action, reason.trim())}>
                  {t(`booking.action.${asking.action}`)}
                </Button>
              </div>
            )}
          </Card>
        );
      })}
    </div>
  );
}
