import { useTranslation } from 'react-i18next';
import { useQuery } from '@tanstack/react-query';
import clsx from 'clsx';
import { LoadingState } from '@/components/ui/StateMessage';
import { intlLocale } from '@/i18n';
import { bookingApi, nextDays } from '@/services/bookingApi';

interface Props {
  branchId: number;
  serviceId: number;
  staffId?: number;
  date: string;
  onDate: (d: string) => void;
  value?: string;
  onPick: (startsAt: string) => void;
}

/** Fourteen day chips and the free start times of the chosen day. */
export function SlotPicker({ branchId, serviceId, staffId, date, onDate, value, onPick }: Props) {
  const { t, i18n } = useTranslation();
  const locale = intlLocale(i18n.language);
  const q = useQuery({
    queryKey: ['slots', branchId, serviceId, staffId ?? 0, date],
    queryFn: () => bookingApi.slots(branchId, serviceId, date, staffId),
  });
  const dayLabel = (d: string) =>
    new Intl.DateTimeFormat(locale, { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' }).format(new Date(`${d}T00:00:00Z`));
  const timeLabel = (iso: string) =>
    new Intl.DateTimeFormat(locale, { hour: 'numeric', minute: '2-digit', timeZone: 'Asia/Kolkata' }).format(new Date(iso));

  return (
    <div className="space-y-3">
      <div role="radiogroup" aria-label={t('booking.date')} className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
        {nextDays(14).map((d) => (
          <button key={d} type="button" role="radio" aria-checked={d === date} onClick={() => onDate(d)}
            className={clsx('shrink-0 rounded-xl border px-3 py-2 text-sm', d === date ? 'border-brand-500 bg-brand-50 text-brand-700' : 'border-border hover:border-brand-300')}>
            {dayLabel(d)}
          </button>
        ))}
      </div>
      {q.isLoading && <LoadingState />}
      {q.data && (q.data.slots.length === 0 ? (
        <p className="text-sm text-fg-muted">{t(`team.outcome.${q.data.outcome === 'OPEN' ? 'FULL' : q.data.outcome}`)}</p>
      ) : (
        <div role="radiogroup" aria-label={t('booking.time')} className="flex flex-wrap gap-2">
          {q.data.slots.map((s) => (
            <button key={s.startsAt} type="button" role="radio" aria-checked={value === s.startsAt} onClick={() => onPick(s.startsAt)}
              className={clsx('rounded-lg border px-3 py-1.5 text-sm tabular-nums', value === s.startsAt ? 'border-brand-600 bg-brand-600 text-white' : 'border-border hover:border-brand-400')}>
              {timeLabel(s.startsAt)}
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
