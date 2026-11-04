import { useTranslation } from 'react-i18next';
import clsx from 'clsx';
import type { BookingStatus } from '@/services/bookingApi';

const styles: Record<BookingStatus, string> = {
  PENDING_PAYMENT: 'bg-amber-100 text-amber-900 dark:bg-amber-500/20 dark:text-amber-200',
  PENDING_APPROVAL: 'bg-amber-100 text-amber-900 dark:bg-amber-500/20 dark:text-amber-200',
  CONFIRMED: 'bg-success/15 text-success',
  COMPLETED: 'bg-surface-muted text-fg-muted',
  CANCELLED: 'bg-danger/15 text-danger',
  DECLINED: 'bg-danger/15 text-danger',
  NO_SHOW: 'bg-danger/15 text-danger',
  EXPIRED: 'bg-surface-muted text-fg-muted',
};

export function BookingStatusBadge({ status }: { status: BookingStatus }) {
  const { t } = useTranslation();
  return (
    <span className={clsx('inline-flex shrink-0 items-center rounded-full px-2.5 py-0.5 text-xs font-semibold', styles[status])}>
      {t(`booking.status.${status}`)}
    </span>
  );
}
